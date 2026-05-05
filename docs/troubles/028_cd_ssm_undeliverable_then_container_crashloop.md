# 028: CD 中の SSM 配信不能 → stop/start 後にコンテナがクラッシュループ

## ステータス
🟡 対応中（2026-05-05）

## 発症箇所
- DuckDNS ドメイン取得後の疎通確認（`amazia.duckdns.org` / `13.54.203.95`）
- GitHub Actions `Deploy to EC2` ワークフロー（実行中にキャンセル）
- EC2 上の systemd unit `/etc/systemd/system/amazia.service`（`docker compose up -d --remove-orphans`）

## 症状

phaseX-3（HTTPS化）に向けて DuckDNS の疎通を確認していたところ、以下のシーケンスを観測した。

### 1. デプロイ中のバックエンド無応答
- `:80`（Market nginx）/ `:8001`（Console nginx）→ HTTP 200 OK
- `:8000`（Laravel）→ TCP 接続は通るが**レスポンスなし**で curl タイムアウト
- `:8080`（Spring）→ TCP 接続自体が確立せず（Empty reply）

### 2. SSM の配信不能
診断のため `aws ssm send-command` で `docker ps` などを送信したが、Pending のまま動かず。
SSM の状況を見ると以下が確定：

```
EC2 instance state         : running
EC2 status check           : 3/3 OK
SSM PingStatus             : ConnectionLost
LastPingDateTime           : 19:13:06（CD 開始直前で更新停止）
```

GitHub Actions 側でも CD コマンド（ECR pull 系の `c1c6f359...` など）が
24 連続 Pending のまま動いていなかった。

### 3. stop/start 後のクラッシュループ
[026 即時復旧手順](026_ssm_zombie_online_undeliverable.md) に従い、以下を実施：

1. GitHub Actions の進行中ジョブを Cancel
2. 残留 SSM コマンド 2件を `cancel-command`
3. `aws ec2 stop-instances` → 約3分で `stopped`
4. `aws ec2 start-instances` → 約2分で running + 3/3 OK
5. SSM PingStatus = Online を**連続3回**確認（[025](025_ssm_pending_after_recovery.md) の教訓）
6. 60秒の安定化待機
7. カナリアコマンド `echo canary-ok` を発行

カナリアは Pending → InProgress（39秒目）まで進んだが、**そこから 8 分以上 InProgress のまま完了しない**異常を観測。
並行して HTTP 疎通も悪化していった：

| 時刻 | :80 | :8000 | :8001 | :8080 |
|------|-----|-------|-------|-------|
| start 直後 | 200 | TCP open / HTTP無応答 | 200 | **TCP closed** |
| 90秒後 | 200 | 同左 | 同左 | TCP open / HTTP無応答 |
| 120秒後 | **000** | TCP open / HTTP無応答 | **000** | TCP closed |

最終的に**全ポートが HTTP 000（応答なし）**まで悪化。

### 4. EC2 console-output の決定的証拠
`aws ec2 get-console-output --latest` で取得したカーネルログに、**veth インターフェイスが
次々と作成・削除を繰り返す**典型的な Docker コンテナ再起動ループの痕跡があった。

```
[123.55s] br-bce8c2ba47b8: port 1(veth6f809a7) entered disabled state
[123.81s] br-bce8c2ba47b8: port 1(vethcc0d64a) entered blocking state
[124.35s] eth0: renamed from veth168aef4
[144.27s] br-bce8c2ba47b8: port 1(vethcc0d64a) entered disabled state
[144.47s] br-bce8c2ba47b8: port 1(vethe60e79b) entered blocking state
[145.04s] eth0: renamed from vethabed2c5
```

異なる veth 名（`6f809a7` → `cc0d64a` → `e60e79b`）が**約20秒間隔で次々登場**しており、
同一サービスのコンテナが**作成 → 失敗 → 削除 → 再作成**を繰り返している。

### 5. AWS 側障害の並行確認
本件と並行して AWS Health Dashboard を確認。観測時点で以下の障害が公開されていた。

| 障害 | リージョン |
|------|-----------|
| Multiple services (UAE) | ME-CENTRAL-1 |
| Multiple services (Bahrain) | ME-SOUTH-1 |

いずれも中東紛争に起因するリージョン限定障害。AWS 公式は
「**Asia Pacific 含む他リージョンへ移行を推奨**」と案内しており、
ap-southeast-2（シドニー）への波及は記載なし。
よって本件は AWS 側障害ではなく**こちら側の問題**で確定。

## 根本原因（推定）

### 直接原因：CD が中断された状態で systemd の up が走った
GitHub Actions の CD は以下の流れで EC2 上の Docker を更新する：

```
1. ssm send-command: docker compose down --remove-orphans
2. ssm send-command: ECR から最新イメージを pull
3. ssm send-command: docker compose up -d --remove-orphans
```

今回は **2（ECR pull）の段階で SSM 配信が止まった**まま GitHub Actions ジョブを Cancel。
EC2 視点では、`down` は完了していたかもしれないが pull は中途半端、
新旧イメージが混在した状態で SSM コマンドが宙ぶらりんになった。

その後の stop/start で systemd の `amazia.service` が起動時に
`docker compose up -d --remove-orphans` を実行（[008](008_containers_not_restart_after_ec2_reboot.md) で導入）。
**孤児コンテナ・残留ネットワーク・破損したイメージ参照**などが残っていれば、
[023 の name conflict](023_docker_compose_name_conflict_orphan.md) と同じ機序で起動失敗 → restart loop に陥る。

### SSM カナリアが InProgress 滞留した理由（推定）
- restart loop 中は CPU / メモリ / ディスク I/O が逼迫する
- 無料枠 t2.micro / t3.micro は 1GB メモリしかなく、SSM Agent が応答待ちタスクを
  捌けなくなる
- `echo canary-ok` という極端に軽いコマンドでも完了報告が返らないのは、
  Agent → SSM サービスへのコールバック自体が遅延している証左

## なぜ CI で検知できなかったか

- CD のキャンセル + SSM 配信中断 + EC2 stop/start という**特殊な操作シーケンス**でしか発生しない
- 023 の `--remove-orphans` 修正は「`down` も `up` も両方付ける」までは入っているが、
  pull が中途半端に終わった場合の残骸（壊れたイメージタグ・dangling network）には対処していない
- ローカル CI では Docker デーモンが毎回クリーンなため、過去の compose 実行履歴の残骸が
  原因となるパターンは再現できない

## 暫定対応

EC2 を停止して夜間放置（メモリ枯渇症状の悪化を回避、課金もゼロ）。
復旧は翌日に実施する。

## 翌日の復旧手順（予定）

```bash
# 1. EC2 を起動して running + 3/3 OK + SSM Online を確認
aws ec2 start-instances --instance-ids i-024a0748df78fc93e

# 2. SSM Session Manager（コンソールから直接接続）で EC2 にログイン
#    → SSM send-command 経由ではなく、対話的に状態を確認

# 3. 残骸の完全掃除（023 の即時復旧手順を全コンテナに拡張）
sudo docker rm -f amazia-core amazia-console amazia-mysql amazia-market 2>/dev/null || true
sudo docker network rm amazia_default 2>/dev/null || true

# 4. dangling イメージとビルドキャッシュを削除（壊れた pull の残骸対策）
sudo docker image prune -f
sudo docker system df  # 容量確認

# 5. compose で立て直し
cd /home/ssm-user/amazia
sudo docker compose up -d --remove-orphans

# 6. 各コンテナのログを確認（restart loop が止まったかチェック）
sudo docker compose ps
sudo docker compose logs --tail=50
```

## 再発防止

| 観点 | 対策 |
|------|------|
| CD 中断時の残骸対策 | systemd unit の `ExecStart` に `image prune -f` を加えるかは要検討（破壊的なので慎重に） |
| SSM 障害時の手動復旧手順 | 026 の即時復旧手順に「カナリア InProgress 滞留 = restart loop 疑い → console-output で veth ループ確認」を追記 |
| Health Dashboard の並行確認 | 026 補足の教訓を実践済み（最初の段階で AWS 側障害の有無を切り分けられた） |
| メモリ逼迫の根本対策 | Spring Heap (`-Xmx`) 制限・コンテナ常駐数削減・無料枠 t2.micro での同居数の見直しを別タスク化 |
| 関連トラブル | 008（compose 自動起動）・022（Undeliverable）・023（name conflict / 孤児）・025（リカバリ後 Pending）・026（ゾンビ Online）・017（無料枠リソース最適化） |

## 補足

### EIP の救済効果
今回 stop/start を行ったが、EIP `eipalloc-0b24d7212795aab3b`（`13.54.203.95`）が
EC2 にアタッチされていたため Public IP は変動せず、DuckDNS の再設定は不要だった。
[004 の IP 変動問題](004_ec2_ip_changed_after_restart.md) は EIP で恒久的に解消されている。

### 未アタッチ EIP の課金懸念
調査中に `describe-addresses` で**未アタッチ EIP が 4 本**残っていることを確認した
（`13.237.232.101` / `15.135.8.121` / `16.176.58.160` / `3.24.48.133`）。
未アタッチ EIP は約 \$0.005/h ≒ \$3.6/月/個 課金されるため、
[017 課金最適化](017_aws_cost_unused_resources.md) と整合させて別途解放する必要がある。
