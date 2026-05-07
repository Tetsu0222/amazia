# 042: EC2 ディスクフル起因のゾンビOnline・カナリア配信失敗（旧 Docker イメージの蓄積）

## ステータス
✅ 解決済（2026-05-07）

## 発症箇所
GitHub Actions `Deploy to EC2` ワークフロー
`SSMキュー健全性確認・自動リカバリ` ステップ（[`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml) `canary_check` 第2段）
EC2 `i-024a0748df78fc93e`（13.54.203.95）

## 症状
CD ジョブログに以下が出力されて exit 1:

```
===== カナリアコマンド送信 (リカバリ後) =====
Canary CommandId: 4ce37028-e4d2-4869-92c2-491c6a2712e6
[canary 1] Failed
カナリア失敗: StatusDetails=Failed
ERROR: リカバリ後もカナリア配信失敗。手動調査が必要。
Error: Process completed with exit code 1.
```

第1段（PingStatus / キュー詰まり）は通過、`recover_ssm` の EC2 stop/start も完走、Online 連続検知も成功しているが、それでも `aws ssm send-command` で送った `echo canary-ok` が `Failed` で返ってくる。AWS マネジメントコンソールから SSM Session Manager で接続しようとしても接続できず、シリアルコンソールに切り替えると以下が無限に流れて操作不能：

```
[ 200.154938] systemd-journald[838]: Failed to open system journal: No space left on device
[ 200.194298] systemd-journald[838]: Failed to open system journal: No space left on device
...
```

つまり **OS 側でディスクフルが発生**しており、systemd-journald がジャーナルを開けず無限ループでエラーを吐き続け、SSM Agent も同じ理由でコマンドを実行できない状態（コマンドは届いているが exit 非0で返る ＝ 表面上「ゾンビOnline」に見える）。

## 根本原因

**ルートボリューム 8GB に対して `/var/lib/docker` が 4.8GB を占有 → ディスクフル**。

内訳（修復前の実測）：
- `/var/lib/docker/` 4.8GB
  - うち `Images`：33個 / 3.08GB（`RECLAIMABLE` 2.155GB ＝ 69% が未使用）
  - 稼働中コンテナのイメージは 3 個 / 928MB のみ
- 残り 30 個 ＝ 過去デプロイで取得した `amazia-core:latest` / `amazia-console:latest` の旧 SHA イメージ

CD のフローで `docker pull` → `docker compose up -d` を行うと、`latest` タグは新しい SHA に付け替わるが**旧 SHA のイメージは残り続ける**。amazia-core が ~250MB / amazia-console が ~150MB あり、フェーズ進行に伴うデプロイ回数が積み上がって枯渇した。

ディスクフルになると：
1. systemd-journald がジャーナルを書けない → 無限エラーループ
2. SSM Agent はプロセスとしては生存できる（PingStatus=Online）
3. しかし `AWS-RunShellScript` で渡された `echo canary-ok` を実行する際、ssm-agent がワーキングディレクトリ（`/var/lib/amazon/ssm/...`）に書き込めず、コマンドが exit 1 で終了
4. SSM 側からは `Status=Failed / StatusDetails=Failed` として返る（022・026 の Undeliverable とは別パターン）

EC2 stop/start を行ってもディスクの中身は変わらないため、`recover_ssm` を何度走らせても回復しない構造。

## なぜ CI で検知できなかったか

| 観点 | 理由 |
|------|------|
| ローカル CI | Docker のイメージ蓄積は **時間軸の長い問題**で、PR 単位のテストでは再現しない |
| 既存のヘルスチェック | デプロイ後 HTTP 200 確認は走るが、**ディスク使用率の閾値監視が無い** |
| canary_check のログ | `StatusDetails=Failed` のみ表示・stdout/stderr を取得していなかったため、シリアルコンソールに行くまで原因不明だった（024 で stdout/stderr の3点セット出力を追加したが、`canary_check` 内の失敗ハンドラに同じ改修が及んでいなかった） |
| CloudWatch | ディスクメトリクスは `procstat` プラグイン未導入なら標準では取得されない（無料枠完走方針で導入を見送っている） |

## 修正内容

### 1. 即時復旧（実施済）
1. EBS スナップショット取得（保険）：`aws ec2 create-snapshot --volume-id vol-0aa384c33ff792e4c`
2. ルートボリュームを **8GB → 16GB に拡張**：`aws ec2 modify-volume --size 16`（無料枠 30GB/月内）
3. EC2 stop/start で OS にパーティション拡張を反映（cloud-init による自動 growpart + xfs_growfs）
4. SSM 復活後、`docker system prune -af` で未使用イメージを一括削除 → **1.88GB 解放**

復旧後の状態：
- `/dev/nvme0n1p1` 16GB / 使用 5.9GB（37%）
- Images 3 個 / 1.2GB（`RECLAIMABLE` 8.45MB ＝ 0.7%）
- SSM カナリア配信 → Success

### 2. 再発防止：deploy.yml に prune ステップを追加
[`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml) の `EC2 - Docker停止・ECR pull・Docker起動` ステップで `docker compose up -d` 後に `docker image prune -af` を追加。

```diff
              $up,
+             "docker image prune -af && df -h /"
```

`docker image prune -af` は**稼働中コンテナが参照していないイメージのみ**を削除するため、現行バージョンを巻き込む心配はない。`df -h /` を併記してジョブログから使用率の推移を後から追えるようにする。

### 3. 再発防止：canary_check の失敗ログ強化
[`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml) の `canary_check` 関数で、`Failed` / `TimedOut` / `Cancelled` 時に **stdout / stderr / StatusDetails の3点セット** を出力するように変更（024 で本デプロイステップに入れた改修を `canary_check` 内にも適用）。

これにより、次回同種の事象が起きた際に「`echo canary-ok` を ssm-agent が実行できなかった」という事実がジョブログだけで判別可能になる。

## 再発防止
| 観点 | 対策 |
|------|------|
| イメージ蓄積 | デプロイ毎に `docker image prune -af` を自動実行（本修正） |
| 失敗時の調査効率 | `canary_check` 失敗時に stdout/stderr/StatusDetails を3点セット出力（本修正） |
| ディスク使用率の早期警告 | デプロイ末尾の `df -h /` 出力をジョブログに残す（本修正で対応） |
| ボリュームサイズ | ルート 16GB（無料枠 30GB/月内・余力 14GB） |
| 将来課題 | CloudWatch Agent で `disk_used_percent` を取り、80% 超で SNS / メール通知（無料枠 10カスタムメトリクス内で実装可） — 必要になったら検討 |

## 学び（operational_insights.md 候補）

- **`PingStatus=Online` だけでは SSM 健全性として不十分**は 026 で確立済みだが、本件はさらに踏み込んで「OS のディスクフルでも Online は維持される（ssm-agent プロセスが死なないため）」ことを示した。**実コマンド実行が成功して初めて健全**と判定する canary 方式の妥当性を再確認。
- **ディスクフルになると AWS マネジメントコンソールからの SSM Session Manager 接続も失敗**（接続初期化で writer が必要なため）。詰まると入れる経路が**シリアルコンソール経由のみ**になるため、SSH ポートを閉じている運用では特に予防が重要。
- **stop/start で直らないトラブルが存在する**：今回は「同じディスクが付いたまま起動するから当然直らない」のだが、CD 側の自動リカバリ（recover_ssm）が「再起動すれば直る」前提で組まれていたため、3 段目以降の対応（ディスク確認・拡張）が用意されていなかった。CD の自動リカバリは「リトライ可能な瞬間障害」と「再起動では直らない構造的障害」を分けて設計する必要がある。
