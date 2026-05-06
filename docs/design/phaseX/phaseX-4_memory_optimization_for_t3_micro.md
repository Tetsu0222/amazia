# フェーズX-4：t3.micro 復帰のためのメモリ最適化（Spring Heap 制限 + Swap）

## ステータス
✅ 完了（2026-05-06）

### 実構成
- インスタンスタイプ: **t3.micro（1024MB / 無料枠 750h）に復帰**
- Spring Heap: `JAVA_TOOL_OPTIONS=-Xmx384m -Xss256k -XX:MaxMetaspaceSize=128m`（[docker-compose.yml:30](../../../docker-compose.yml#L30)）
- Swap: `/swapfile` 2GB、`vm.swappiness=10`、`/etc/fstab` で永続化
- 月額課金: **$0 復帰**

### 実測値（2026-05-06）
- amazia-core メモリ: **219MB**（Heap 制限後・前回 293MB から 74MB 減）
- 軽負荷試験（3経路 × 60秒 = 180リクエスト）: **全て 200**
- 負荷ピーク時 Swap 使用: **257MB**（基準 500MB 以下 ✅）
- 負荷ピーク時 Mem available: **60MB**（枯渇寸前だが Swap が機能）
- OOM Killer 発動: **なし**（`dmesg | grep -i oom` 何も出ず）
- スキーマ健全性: t3.small → t3.micro 切替前後で完全一致（roles=5 / permissions=13 / role_permissions=57）

## 位置付け
時系列フェーズ（1〜20）に依存しない横断的インフラ改善フェーズ。
[トラブル028](../../troubles/028_cd_ssm_undeliverable_then_container_crashloop.md) §再発防止に
「Spring Heap (`-Xmx`) 制限・コンテナ常駐数削減・無料枠 t2.micro での同居数の見直しを別タスク化」と
記録された項目に対応する独立タスク。

---

## 背景・なぜ今やるか

- [029](../../troubles/029_compose_plugin_lost_and_users_schema_drift.md) の調査時に、t3.micro
  （メモリ 1024MB）では restart loop 中に SSM Agent のシェル応答が返らないほど **メモリ余裕が
  恒常的にゼロに近い** ことが判明した。
- 切り分け目的で t3.small（メモリ 1913MB）に一時昇格して 927MB 使用・利用可能 822MB を観測。
  逆算すると t3.micro での利用可能枠は **約 97MB** しかなく、バーストや GC で容易に枯渇する。
- ボトルネックは `amazia-core`（Spring Boot）。JVM はデフォルトで物理メモリの 25% をヒープ上限に
  取るため、明示的な `-Xmx` 制限と、緊急時のクッションとなる Swap を組み合わせれば t3.micro でも
  運用できる見込み。
- 「無料枠の制約下でどう設計判断したか」が本プロジェクトの主軸（メモリ
  [feedback_free_tier_first.md](../../../../../.claude/projects/c--Users-root2-OneDrive--------ProjectFullStackRenaissance/memory/feedback_free_tier_first.md)）。
  t3.small 恒久運用は月 $15 課金で方針と矛盾するため、t3.micro 復帰を試みる。

---

## 着手前提条件

時系列フェーズ非依存だが、**インフラ変更の同時並行は切り分けを困難にする** ため、以下の順序で進める。

- [phaseX-3（HTTPS 化）](phaseX-3_https_via_cloudfront.md) と並行着手しない
- phaseX-3 の動作確認が完了し、HTTPS 経由で Market/Console/Spring が安定している状態を確認してから着手する
- 万一 phaseX-3 を保留とする判断になった場合は、phaseX-4 を先行してよい（その場合は本フェーズ完了後に phaseX-3 へ進む）

理由：t3.micro 戻し → OOM 発生時、原因が JVM ヒープ不足なのか CloudFront 経由のリクエスト増なのか、
同時に変更すると判別できなくなる。

---

## 設計判断のサマリ

| 項目 | 現状（t3.small 一時運用） | 本フェーズの判断 | 判断理由 |
|------|--------------------------|------------------|---------|
| インスタンスタイプ | t3.small（メモリ 1913MB） | **t3.micro（1024MB）に戻す** | 無料枠完走 |
| Spring Heap | 未指定（デフォルト = 物理 25%） | **`-Xmx384m -Xss256k`** で固定 | t3.micro でも安定するヒープ上限 |
| Swap | なし | **2GB のスワップファイルを設定** | OOM Killer 発動回避のクッション |
| MySQL Buffer Pool | デフォルト（128MB） | 同左で維持 | 削減すると性能劣化が顕著 |
| Laravel | 特に制限なし | 同左で維持 | PHP-FPM のメモリは小さい |

**コスト試算：恒久 $0**（EC2 t3.micro 750h/月の無料枠内、Swap は EBS 容量内）

---

## ステップ一覧

| # | ステップ | 対象 | 内容 |
|---|---------|------|------|
| 1 | 現行メモリ使用量のベースライン計測 | EC2 (t3.small) | `docker stats` でコンテナ毎の使用量を 5 分間計測 |
| 2 | Spring Heap 制限の追加 | docker-compose.yml | `amazia-core` に `JAVA_TOOL_OPTIONS=-Xmx384m -Xss256k` を環境変数で渡す |
| 3 | Swap 2GB のセットアップスクリプト整備 | EC2 | `/swapfile` を作成し `/etc/fstab` に永続化 |
| 4 | t3.small で動作確認 | 全体 | Heap 制限と Swap が効いていることを確認、4 ポート 200 |
| 5 | t3.micro に戻して再検証 | AWS / EC2 | スキーマ健全性チェック → インスタンスタイプ変更 → 起動 → 再チェック → CD 経路確認 |
| 6 | 負荷試験（軽負荷で OOM が出ないか） | EC2 | 3 経路（Spring/Console/Market）に `curl` ループで 1 分間アクセスして安定性を見る |
| 7 | 設計書・トラブルドキュメント更新 | docs | 029 §再発防止に「phaseX-4 完了」と記録、本書のステータスを ✅ に |

---

## ステップ詳細

### ステップ 1：ベースライン計測

```bash
# t3.small で稼働中の状態で 5 分間サンプリング
docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}"
# 5 回繰り返して平均を取る
```

**確認ポイント：** amazia-core が 600MB 以上消費していたら -Xmx384m では不足の可能性。
その場合 -Xmx 値を再調整する。

---

### ステップ 2：Spring Heap 制限の追加

[`docker-compose.yml`](../../../docker-compose.yml) の `amazia-core` サービスに環境変数を 1 行追加する。
追加位置は `SPRING_PROFILES_ACTIVE` の直下（`environment:` ブロック先頭）。

**変更前（[docker-compose.yml:27-34](../../../docker-compose.yml#L27-L34)）：**

```yaml
    environment:
      SPRING_PROFILES_ACTIVE: dev
      CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS:-http://localhost:5173,http://localhost:3000}
      JWT_SECRET: ${JWT_SECRET}
      JWT_ACCESS_TTL: ${JWT_ACCESS_TTL:-900}
      JWT_REFRESH_TTL: ${JWT_REFRESH_TTL:-1209600}
      AWS_SES_FROM_ADDRESS: ${AWS_SES_FROM_ADDRESS:-no-reply@amazia.example.com}
      PASSWORD_RESET_URL: ${PASSWORD_RESET_URL:-http://localhost:5173/password/reset/confirm}
```

**変更後：**

```yaml
    environment:
      SPRING_PROFILES_ACTIVE: dev
      JAVA_TOOL_OPTIONS: "-Xmx384m -Xss256k -XX:MaxMetaspaceSize=128m"
      CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS:-http://localhost:5173,http://localhost:3000}
      JWT_SECRET: ${JWT_SECRET}
      JWT_ACCESS_TTL: ${JWT_ACCESS_TTL:-900}
      JWT_REFRESH_TTL: ${JWT_REFRESH_TTL:-1209600}
      AWS_SES_FROM_ADDRESS: ${AWS_SES_FROM_ADDRESS:-no-reply@amazia.example.com}
      PASSWORD_RESET_URL: ${PASSWORD_RESET_URL:-http://localhost:5173/password/reset/confirm}
```

**留意：**
- `JAVA_TOOL_OPTIONS` は JVM が自動で読み込む環境変数。[Dockerfile:14](../../../amazia-core/Dockerfile#L14) の `ENTRYPOINT` を変更せずに済む
- `-Xss256k` はスレッドスタックサイズの抑制（デフォルト 1MB）、`-XX:MaxMetaspaceSize=128m` はクラスメタデータ領域の上限
- `JWT_SECRET` 等の既存変数は **絶対に削除しない**（[トラブル029 原因2](../../troubles/029_compose_plugin_lost_and_users_schema_drift.md) で `.env` 不在によるバグが発生済み）

---

### ステップ 3：Swap 2GB のセットアップ

```bash
# /swapfile を作成（2GB）
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# 永続化
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Swappiness の調整（メモリを使い切るまで Swap を使わない）
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-swappiness.conf
sudo sysctl -p /etc/sysctl.d/99-swappiness.conf

# 確認
free -m
swapon --show
```

**留意：** EBS の課金枠内（無料枠 30GB）に収める。現在 8GB 使用中なので 2GB 追加でも 10GB で枠内。
EC2 起動時に自動マウントされるよう `/etc/fstab` への登録が必須。

---

### ステップ 4：t3.small で動作確認

```bash
docker compose up -d --force-recreate amazia-core
sleep 60
docker stats --no-stream amazia-core
# amazia-core のメモリが 384MB + メタデータ で 500MB 程度に収まることを確認
```

---

### ステップ 5：t3.micro に戻して再検証

#### 5-1. 停止前のスキーマ健全性チェック（必須）

[トラブル029 原因3](../../troubles/029_compose_plugin_lost_and_users_schema_drift.md) で
ALTER 直打ちによる応急処置（業務カラムの手動追加）を行ったため、現状の mysql volume には
migration ファイルにない状態が残存している。インスタンス停止 → 起動の前後で同じ状態が維持されて
いることを保証するため、停止前に以下を記録する。

```bash
# Session Manager 経由で実行
docker exec amazia-mysql mysql -uroot -proot_pass amazia -e "
  SHOW TABLES;
  SHOW COLUMNS FROM users;
  SELECT COUNT(*) AS roles_cnt FROM roles;
  SELECT COUNT(*) AS perms_cnt FROM permissions;
  SELECT COUNT(*) AS rp_cnt FROM role_permissions;
"
# 期待値：
#   - users に employee_id / password_hash / role_id / active_flag / failed_attempts / locked_until が存在
#   - roles / permissions / role_permissions テーブルが存在しレコードあり
```

出力をテキストとして保存しておき、5-4 の起動後チェックと突き合わせる。

#### 5-2. インスタンスタイプ変更

```bash
# 1. EC2 停止
aws ec2 stop-instances --instance-ids i-024a0748df78fc93e --region ap-southeast-2

# 2. インスタンスタイプ変更
aws ec2 modify-instance-attribute --instance-id i-024a0748df78fc93e \
  --instance-type '{"Value":"t3.micro"}' --region ap-southeast-2

# 3. 起動
aws ec2 start-instances --instance-ids i-024a0748df78fc93e --region ap-southeast-2
```

#### 5-3. 起動後の基本確認

```bash
# SSM Online 待機後、Session Manager で接続
docker compose ps                 # 3 コンテナとも Up、mysql は (healthy)
docker compose logs amazia-core | tail -50  # JAVA_TOOL_OPTIONS が認識されているか
free -m && swapon --show          # Swap 2GB が有効
```

#### 5-4. スキーマ健全性の再チェック（5-1 と突き合わせ）

```bash
docker exec amazia-mysql mysql -uroot -proot_pass amazia -e "
  SHOW TABLES;
  SHOW COLUMNS FROM users;
  SELECT COUNT(*) AS roles_cnt FROM roles;
"
# 5-1 の出力と完全一致することを確認。
# 不一致なら mysql volume の状態崩れが起きている → 復旧を最優先
# （[トラブル029 §修正内容 3-4](../../troubles/029_compose_plugin_lost_and_users_schema_drift.md) を再適用）
```

#### 5-5. CD 経路でも JAVA_TOOL_OPTIONS が維持されることの確認

[deploy.yml:266](../../../.github/workflows/deploy.yml#L266) の SSM 経由 `docker-compose up -d`
が走った後も `JAVA_TOOL_OPTIONS` が効いていることを確認する。

```bash
# 任意のコミットを main に push して CD を走らせ、デプロイ完了後に
docker exec amazia-core ps -ef | grep java
# → java -jar app.jar の引数または環境にオプションが反映されていること
docker exec amazia-core sh -c 'echo $JAVA_TOOL_OPTIONS'
# → "-Xmx384m -Xss256k -XX:MaxMetaspaceSize=128m"
```

---

### ステップ 6：軽負荷試験

Spring 単体だけでなく、Console（Laravel）/ Market 経由のメモリ圧も含めて測定する。
[トラブル029 §検証](../../troubles/029_compose_plugin_lost_and_users_schema_drift.md) で
動作確認した 4 ポート構成に合わせる。

```bash
# 端末 A：3 経路を同時に 60 秒間叩く（外部 IP は EIP 13.54.203.95）
HOST=13.54.203.95
for i in $(seq 1 60); do
  curl -s -o /dev/null -w "core=%{http_code} "    http://$HOST:8080/api/products
  curl -s -o /dev/null -w "console=%{http_code} " http://$HOST:8000/
  curl -s -o /dev/null -w "market=%{http_code}\n" http://$HOST/
  sleep 1
done | tee /tmp/load_test.log
```

```bash
# 端末 B：並行してメモリ・swap・コンテナ状態を監視
watch -n 2 "free -m && echo --- && docker stats --no-stream && echo --- && docker compose ps"
```

**判定：**

| 条件 | 結果 |
|------|------|
| 全 180 リクエスト（3 経路 × 60 回）が 200 を返し続ける | ✅ 必須 |
| Swap 使用量が 500MB 以下で安定 | ✅ 必須 |
| amazia-core のメモリが 500MB 前後で頭打ち | ✅ 期待値 |
| `docker compose ps` で Restarting が発生しない | ✅ 必須 |
| OOM Killer 発動（`dmesg | grep -i oom`）・コンテナが Restarting に入る | ❌ → t3.small 恒久化（A 案） |

なお Spring 直叩きしか行えない場合は `/api/products` のみで代替可だが、Console/Market 経由の
負荷を含まないため判定の信頼性は下がる点に留意する。

---

### ステップ 7：設計書・ドキュメント更新

- 本書のステータスを ✅ 完了 に更新
- [029](../../troubles/029_compose_plugin_lost_and_users_schema_drift.md) §再発防止 の
  「phaseX-4 で t3.micro 復帰を試みる」項目に結果を追記
- メモリ
  [reference_aws_infra_facts.md](../../../../../.claude/projects/c--Users-root2-OneDrive--------ProjectFullStackRenaissance/memory/reference_aws_infra_facts.md)
  のインスタンスタイプ表記を最新に更新

---

## リスクと対策

| リスク | 対策 |
|--------|------|
| `-Xmx384m` でも不足し OOM が出る | Swap 2GB がクッションになる。それでもダメなら Heap を 320m に下げ、Spring の機能（一部 Auto-config 無効化）も検討 |
| Swap 多用で Spring の応答が遅くなる | `vm.swappiness=10` で「メモリ枯渇寸前まで Swap を使わない」設定とする |
| EBS 容量超過で課金 | 8GB → 10GB（Swap 2GB 追加分）で無料枠 30GB 内 |
| t3.micro での負荷耐性不足 | Step 6 で実測。耐えられない場合は A 案（t3.small 恒久）に倒す |
| Laravel/MySQL も含めた合計でメモリ不足 | コンテナ常駐数削減（mysql の buffer_pool_size 縮小）も予備案として用意 |

---

## 期待効果

- t3.micro 無料枠（750h/月）に戻ることで、月額課金がゼロに復帰
- 「無料枠完走」というポートフォリオの主軸に整合
- Spring Heap 制限・Swap 設定・Swappiness 調整というインフラチューニングの実装記録が残る
- フェーズ間の依存はないため、phaseX-3（HTTPS化）と並行して進められる

---

## 参考：採用しなかった選択肢

| 案 | 不採用理由 |
|---|----------|
| t3.small 恒久運用 | 月 $15 課金で無料枠完走方針と矛盾。リスク許容できない場合の最終フォールバック |
| amazia-core を停止して Console + Market のみ運用 | 機能の半分が失われ、ポートフォリオとして成立しない |
| Spring Boot Native Image 化（GraalVM） | ビルド時間が極端に伸び、CD パイプラインへの影響大 |
| Spring を別の軽量フレームワーク（Quarkus / Micronaut）に置換 | コード書き換えが大規模で、本タスクの範疇を逸脱 |
| t4g.micro（ARM）への切り替え | Java バイナリの ARM 対応確認が要、検証工数が大きい |
