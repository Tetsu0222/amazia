# フェーズX-1：デプロイパイプライン高速化

## ステータス
🔲 未着手（随時）

## 改訂履歴
- 2026-05-10: パイプライン現状（phaseX-2 / phaseX-4 / phaseX-6 適用後）に合わせて方針を再設計。
  - 改善①「docker pull 並列化」は **却下**（phaseX-4 で t3.micro 復帰し、Mem available 60MB の状況で並列 pull は OOM を再誘発するリスクが高い）。
  - 改善②「ポーリング初回 sleep 短縮」を SSM **全 6 ループ** に拡張。
  - 改善④「S3 zip 廃止・dist 直接配置」を phaseX-2 から正式移管し優先度を上げる。

## 背景
phaseX-2 で OOM／SSM キュー詰まりは根絶し、デプロイ全体は 5 分未満に到達。
その後 phaseX-6 でデプロイ後ヘルスチェック（テーブル存在確認・WARN 抽出・スキーマスナップショット）が追加され、SSM send-command が **6 段** まで増えている。
本フェーズでは **t3.micro 復帰の制約下で安全に取れる純高速化** に絞る。

## 現状の処理フローと推定時間

`.github/workflows/deploy.yml` 上の SSM send-command は以下の 6 段構成（2026-05-10 時点）：

| # | ステップ | ループ最大 | 初回 sleep | 用途 |
|---|---------|----------|----------|------|
| 1 | EC2 - Docker停止・ECR pull・Docker起動 | sleep 15 × 24 = 360s | 15s | ECR pull / unzip / docker-compose up |
| 2 | EC2 - Nginx設定とフロントエンド配置 | sleep 10 × 12 = 120s | 10s | dist 配置・nginx reload |
| 3 | デプロイ後ヘルスチェック | sleep 10 × 36 = 360s | 10s | curl による 200 確認（HTTP直叩き） |
| 4 | ヘルスチェック - 主要テーブル存在確認 | sleep 5 × 12 = 60s | 5s | information_schema 件数確認 |
| 5 | ヘルスチェック - 起動ログ schema WARN 抽出 | sleep 5 × 12 = 60s | 5s | docker logs grep |
| 6 | 本番 DB スキーマスナップショット保存 | sleep 5 × 12 = 60s | 5s | mysqldump → S3 |

ステップ 1 内訳：

```
SSM send-command ① (EC2 - Docker停止・ECR pull・Docker起動)
├── docker-compose down              ~ 10秒
├── ECR login                        ~  3秒
├── docker pull amazia-core          ~ 60秒  ← Javaイメージは重い（数百MB）
├── docker pull amazia-console       ~ 60秒  ← PHPイメージも重い（直列・並列化はリスク高で見送り）
├── aws s3 cp amazia.zip             ~ 10秒
├── unzip -o amazia.zip              ~ 20秒  ← S3 zip 廃止で消去対象
├── docker-compose up -d             ~ 60秒  ← MySQL healthcheck待機が支配的
└── ポーリング最小待機               ~ 15秒  ← 初回 sleep 15 が必ず入る
                              合計 ~ 4〜5分
```

## 改善策

### 改善① docker pull の並列化（**却下** — t3.micro 制約下で危険）

**当初想定：** core/console pull を並列化して 60 秒短縮。
**却下理由（2026-05-10 改訂）：**
- phaseX-4 で t3.micro（1GB RAM）に復帰。Spring Heap 制限後でも軽負荷ピーク時 Mem available 60MB / Swap 257MB という余裕の薄い構成。
- 並列 pull は core+console（数百MB ずつ）の **同時転送・同時展開** を発生させ、稼働中コンテナと併せて RAM/Swap を圧迫する。
- phaseX-2 で OOM → SSM キュー詰まりを根絶した経緯がある（[トラブル028](../../troubles/028_cd_ssm_undeliverable_then_container_crashloop.md)）。pull 順序・down→pull→up を整える対症療法では救えなかった構造的問題で、再発させてはならない。
- 60 秒短縮の効果に対し、SSM 詰まり再発時の手動復旧コスト（EC2 stop/start）は割に合わない。

**将来 t3.small / t3.medium へ戻した場合のみ再検討する** 予備案として記載のみ残す。

---

### 改善② ポーリング間隔の最適化（優先度：高 — 6 ループ全てに拡張）

**効果：累計 30〜60 秒短縮**

phaseX-6 で SSM ループは 6 段に増えた。各段で「初回だけ短く・以降は元の間隔」のパターンを統一適用する。

```bash
# 共通パターン
for i in $(seq 1 N); do
  if [ "$i" -eq 1 ]; then
    sleep 2     # 初回は短く
  else
    sleep $ORIGINAL  # 2 回目以降は従来値
  fi
  STATUS=$(...)
done
```

| 対象ループ | 元 sleep | 短縮量 |
|-----------|---------|-------|
| ① Docker停止・pull・起動 | 15s → 2s | 13s |
| ② Nginx 設定・dist 配置 | 10s → 2s | 8s |
| ③ デプロイ後ヘルスチェック | 10s → 2s | 8s |
| ④ 主要テーブル存在確認 | 5s → 2s | 3s |
| ⑤ schema WARN 抽出 | 5s → 2s | 3s |
| ⑥ DB スキーマスナップショット | 5s → 2s | 3s |
| **合計** | | **38s** |

実コマンド完了が初回ポーリング前に終わっていた場合、最大で **38 秒短縮**。

**注意：** 短縮しすぎて SSM `get-command-invocation` を `Pending` で叩き続けると API レート消費が増える。最初の短縮値は 2〜5 秒に留める。

**変更箇所：** `.github/workflows/deploy.yml` の SSM ポーリングループ 6 箇所すべて。

---

### 改善③ Dockerイメージのレイヤーキャッシュ活用（優先度：中）

**効果：pull 転送量の削減（変更レイヤーのみ転送）**

現状は GitHub Actions 側のビルドステップで `--cache-from` を使っていない（[deploy.yml:231-241](../../../.github/workflows/deploy.yml#L231-L241)）。
そのため latest タグを毎回上書きし、Dockerfile 上層のレイヤーが変わっただけでも全レイヤーが再 push される可能性がある。

```yaml
# 改善後（キャッシュ活用ビルド）
- name: amazia-core イメージビルド・ECR push
  run: |
    docker pull 741011674945.dkr.ecr.ap-southeast-2.amazonaws.com/amazia-core:latest || true
    docker build \
      --cache-from 741011674945.dkr.ecr.ap-southeast-2.amazonaws.com/amazia-core:latest \
      -t amazia-core ./amazia-core
    docker tag amazia-core:latest 741011674945.dkr.ecr.ap-southeast-2.amazonaws.com/amazia-core:latest
    docker push 741011674945.dkr.ecr.ap-southeast-2.amazonaws.com/amazia-core:latest
```

EC2 側 pull は **変更レイヤーのみ転送** に縮み、転送時間と RAM 圧も下がる（並列化を見送った代替効果）。

**変更箇所：** `.github/workflows/deploy.yml` ECR ビルドステップ（core / console 双方）。

---

### 改善④ S3 zip の廃止・dist 直接配置（優先度：高 — phaseX-2 から正式移管）

**効果：unzip 処理の排除（〜20 秒）+ 転送量の削減**

phaseX-2 完了報告で「③ S3 転送軽量化のみ X-1 へ移管」と明記されている宿題。

#### 現状の zip 転送内容

`deploy.yml:264-270` で全リポジトリを zip して S3 に上げ、EC2 側で unzip している。
EC2 側で実際に必要なファイルは：

- `amazia-market/dist/`（Vue 静的ファイル）
- `amazia-console/public/vue/`（Console Vue 静的ファイル）
- `nginx/amazia.conf` / `nginx/amazia.cloudfront.conf`（nginx 設定）
- `docker-compose.yml`（コンテナ起動用）
- `ops/healthcheck/required_tables.txt`（phaseX-6 ヘルスチェック）
- `amazia-console` の Laravel 本体（コンテナ内で動くため amazia-console ディレクトリ全体）

#### 改善後の構成（案）

`s3 sync` は差分のみ転送するため、変更のないファイルをスキップできる。

```bash
# GitHub Actions 側（個別 sync）
aws s3 sync amazia-market/dist/         s3://fullstack-renaissance-demo/amazia-market/    --delete
aws s3 sync amazia-console/public/vue/  s3://fullstack-renaissance-demo/amazia-console-vue/ --delete
aws s3 cp   nginx/amazia.conf           s3://fullstack-renaissance-demo/nginx/
aws s3 cp   nginx/amazia.cloudfront.conf s3://fullstack-renaissance-demo/nginx/
aws s3 cp   docker-compose.yml          s3://fullstack-renaissance-demo/
aws s3 cp   ops/healthcheck/required_tables.txt s3://fullstack-renaissance-demo/ops/healthcheck/

# Laravel 本体（コンテナ内で動く amazia-console）は zip を残すか別途 sync するか要検討
# → コンテナイメージ側に取り込む方向で別フェーズ化（後述「未解決の論点」）
```

EC2 側：

```bash
aws s3 sync s3://.../amazia-market/         /home/ssm-user/amazia/amazia-market/dist/
aws s3 sync s3://.../amazia-console-vue/    /home/ssm-user/amazia/amazia-console/public/vue/
aws s3 cp   s3://.../nginx/amazia.conf      /home/ssm-user/amazia/nginx/
# 以下同様
```

#### 未解決の論点（実装前に決める）

1. **Laravel 本体（amazia-console）の扱い**
   - 現状は zip 経由で `/home/ssm-user/amazia/amazia-console/` 全体が EC2 に展開される。
   - 案 A: Console コンテナイメージに Laravel 本体を取り込む（ECR push に時間が増える代わりに EC2 への転送が消える）。
   - 案 B: 引き続き S3 sync（公開不要のソースを S3 に置くため、バケットポリシーで public-block を確認）。
   - **本フェーズではいったん案 B（sync）に倒し、案 A は別フェーズで検討。**

2. **ops/healthcheck/required_tables.txt の同期タイミング**
   - phaseX-6 のテーブル存在確認は GitHub Actions ランナー上のチェックアウトファイルを直接読んでいるため、EC2 側に配る必要は **ない**（[deploy.yml:466](../../../.github/workflows/deploy.yml#L466)）。S3 sync 対象から外してよい。

3. **古い `amazia.zip` の片付け**
   - 移行後は S3 上の `amazia.zip` を削除し、ライフサイクルで再生成されないことを確認する。

**変更箇所：** `deploy.yml` の以下 4 ステップ。
- 「S3にzipをアップロード」 → 「S3 に dist / 設定ファイルを sync」
- 「EC2 - Docker停止・ECR pull・Docker起動」 → unzip / s3 cp 行を sync 系に置換
- 「EC2 - Nginx設定とフロントエンド配置」 → コピー元パスの調整
- （該当する場合）docker-compose.yml の volume マウントパス

---

## 改善効果まとめ

| 改善策 | 効果 | 工数 | リスク |
|--------|------|------|--------|
| ① docker pull 並列化 | — | — | **却下**（t3.micro で OOM 再誘発リスク高） |
| ② ポーリング初回 sleep 短縮（6 ループ） | ~38秒短縮 | 小 | 低 |
| ③ レイヤーキャッシュ活用 | 変動（転送量削減） | 小 | 低 |
| ④ S3 zip 廃止・dist 直接配置 | ~20秒短縮＋転送量削減 | 中 | 中（設計変更を含む） |

②③④を組み合わせると **合計 1 分前後の短縮** + 転送量・unzip 負荷の削減が見込める。

## 推奨着手順序

1. **②（ポーリング 6 ループ最適化）** — 即効性が高く変更箇所も明確。最初にやる。
2. **③（レイヤーキャッシュ活用）** — GitHub Actions 側のみで完結。EC2 側に影響しない。
3. **④（S3 zip 廃止）** — 設計変更を含むため最後。Laravel 本体の扱いは別フェーズで再検討する前提で、まずは dist + 設定ファイルだけ sync 化する。

## フェーズ完了の定義

- [ ] 改善② を deploy.yml 6 ループに適用しデプロイ実機で正常終了を確認
- [ ] 改善③ を core / console 両方のビルドに適用し、2 回連続デプロイで pull 転送量の減少を確認
- [ ] 改善④ で zip 廃止の sync 化を実装し、デプロイ実機で 200 応答 + テーブル存在確認 + スナップショット保存まで成功
- [ ] S3 上の旧 `amazia.zip` を削除（ライフサイクルで再生成されないことを確認）
- [ ] 本設計書のステータスを「✅ 完了」に更新
