# フェーズX：デプロイパイプライン高速化

## ステータス
🔲 未着手

## 背景
ECR pull・Docker起動ステップ（SSM send-command①）に約5分かかっている。
現状のボトルネックを分析し、優先度付きで改善策をまとめる。

## 現状の処理フローと推定時間

```
SSM send-command ①
├── ECR login                     ~  3秒
├── docker pull amazia-core        ~ 60秒  ← Javaイメージは重い（数百MB）
├── docker pull amazia-console     ~ 60秒  ← PHPイメージも重い（直列実行）
├── aws s3 cp amazia.zip           ~ 10秒
├── unzip -o amazia.zip            ~ 20秒
├── docker-compose down            ~ 10秒
├── docker-compose up -d           ~ 60秒  ← MySQL healthcheck待機が支配的
└── ポーリング最小待機              ~ 15秒  ← 完了直後でも15秒後まで確認しない
                              合計 ~ 4〜5分
```

## 改善策

### 改善① docker pull の並列化（優先度：高）

**効果：約60秒短縮**

coreとconsoleのpullを逐次→並列に変更する。

```bash
# 現状（逐次）
docker pull .../amazia-core:latest
docker pull .../amazia-console:latest

# 改善後（並列）
docker pull .../amazia-core:latest &
docker pull .../amazia-console:latest &
wait
```

`&` でバックグラウンド実行し、`wait` で両方の完了を待つ。
エラーハンドリングが必要な場合は各pullの終了コードを確認する。

**変更箇所：** `.github/workflows/deploy.yml` SSM send-command① の docker pull 2行

---

### 改善② ポーリング間隔の最適化（優先度：中）

**効果：最大14秒短縮**

現在は1回目のポーリングまで必ず `sleep 15` が入る。
実際の処理時間に合わせて初回だけ短くする。

```bash
# 現状（全回一律 sleep 15）
for i in $(seq 1 24); do
  sleep 15
  STATUS=$(...)
done

# 改善後（初回のみ短縮）
for i in $(seq 1 24); do
  if [ "$i" -eq 1 ]; then sleep 5; else sleep 15; fi
  STATUS=$(...)
done
```

**変更箇所：** `.github/workflows/deploy.yml` SSM send-command① のポーリングループ

---

### 改善③ Dockerイメージのレイヤーキャッシュ活用（優先度：中）

**効果：pull転送量の削減（変更レイヤーのみ転送）**

現状は毎回 `latest` タグを上書きしているため、変更のないレイヤーも再転送されている可能性がある。
GitHub Actions 側でのビルド時に `--cache-from` を活用し、ECR に push するレイヤーを最小化する。

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

**変更箇所：** `.github/workflows/deploy.yml` ECRビルドステップ

---

### 改善④ S3 zip の廃止・dist直接配置（優先度：低・中長期）

**効果：unzip処理の排除＋転送量の削減**

現状は全ソースをzipしてS3経由で転送しているが、EC2側で実際に必要なのは
`amazia-market/dist` と `amazia-console/public/vue` のみ。

```bash
# 改善後イメージ
aws s3 sync amazia-market/dist/       s3://.../amazia-market/
aws s3 sync amazia-console/public/vue/ s3://.../amazia-console/

# EC2側
aws s3 sync s3://.../amazia-market/  /var/www/amazia-market/
aws s3 sync s3://.../amazia-console/ /var/www/amazia-console/
```

`s3 sync` は差分のみ転送するため、変更のないファイルをスキップできる。
ただし nginx.conf などの設定ファイルの配置方法を別途考慮する必要がある。

**変更箇所：** `deploy.yml` S3アップロードステップ・SSM send-command①②

---

## 改善効果まとめ

| 改善策 | 効果 | 工数 | リスク |
|--------|------|------|--------|
| ① docker pull 並列化 | ~60秒短縮 | 小 | 低 |
| ② ポーリング間隔最適化 | ~14秒短縮 | 小 | 低 |
| ③ レイヤーキャッシュ活用 | 変動（数十秒〜数分） | 小 | 低 |
| ④ S3 zip廃止・直接配置 | ~30秒短縮＋転送量削減 | 中 | 中 |

①②③を組み合わせると合計1〜2分程度の短縮が見込める。

## 推奨着手順序

1. ①②（変更箇所が少なく即効性が高い）
2. ③（ビルドキャッシュの恩恵はイメージサイズと変更頻度次第）
3. ④（設計変更を伴うため他フェーズの区切りで実施）
