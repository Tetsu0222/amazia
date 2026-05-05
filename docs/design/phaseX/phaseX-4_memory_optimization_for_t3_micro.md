# フェーズX-4：t3.micro 復帰のためのメモリ最適化（Spring Heap 制限 + Swap）

## ステータス
🔲 未着手

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
| 5 | t3.micro に戻して再検証 | AWS / EC2 | インスタンスタイプ変更 → 起動 → 動作確認 |
| 6 | 負荷試験（軽負荷で OOM が出ないか） | EC2 | `ab` か `curl` ループで 1 分間アクセスして安定性を見る |
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

`docker-compose.yml` の `amazia-core` セクションに以下を追加：

```yaml
amazia-core:
  image: ...
  environment:
    SPRING_PROFILES_ACTIVE: dev
    JAVA_TOOL_OPTIONS: "-Xmx384m -Xss256k -XX:MaxMetaspaceSize=128m"
    # ...既存の環境変数
```

**留意：** `JAVA_TOOL_OPTIONS` は JVM が自動で読み込む環境変数。Dockerfile の ENTRYPOINT を
変更せずに済む。`-Xss256k` はスレッドスタックサイズの抑制（デフォルト 1MB）、
`-XX:MaxMetaspaceSize=128m` はクラスメタデータ領域の上限。

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

```bash
# 1. EC2 停止
aws ec2 stop-instances --instance-ids i-024a0748df78fc93e --region ap-southeast-2

# 2. インスタンスタイプ変更
aws ec2 modify-instance-attribute --instance-id i-024a0748df78fc93e \
  --instance-type '{"Value":"t3.micro"}' --region ap-southeast-2

# 3. 起動
aws ec2 start-instances --instance-ids i-024a0748df78fc93e --region ap-southeast-2

# 4. SSM Online 待機後、Session Manager で接続して全コンテナ Up を確認
```

---

### ステップ 6：軽負荷試験

```bash
# /api/products に 60 秒間アクセス
for i in $(seq 1 60); do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/products
  sleep 1
done

# 並行してメモリと swap を監視
watch -n 2 "free -m && echo --- && docker stats --no-stream"
```

**判定：**
- 全リクエスト 200 が返り続け、Swap が 500MB 以下で安定 → ✅ t3.micro 運用可能
- OOM Killer 発動・コンテナが Restarting に入る → ❌ t3.small 維持を選択（A 案恒久化）

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
