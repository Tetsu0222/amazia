# 025: SSM 自動リカバリ直後の send-command が Pending に滞留する

## ステータス
✅ 解決済（2026-05-06）— phaseX-3 HTTPS 化のデプロイで「Online 連続3回検知 + 60秒安定化待機」のリカバリ後シーケンスが期待通り動作することを実環境で確認済み（026 のカナリア方式とセットで稼働）

## 発症箇所
GitHub Actions `Deploy to EC2` ワークフロー
- `SSMキュー健全性確認・自動リカバリ` ステップ（[`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml) L79）
- 直後の `EC2 - Docker停止・ECR pull・Docker起動` ステップ（同 L186）

## 症状

`SSMキュー健全性確認・自動リカバリ` で EC2 stop/start が走り、
`SSM Online確認。デプロイを継続します。` まで通った直後の `send-command` が
**`Pending` のまま滞留** し、最終的に TimedOut で落ちる。

```
（リカバリステップのログ）
SSM PingStatus: ConnectionLost
判定: SSM未接続 (PingStatus=ConnectionLost)
EC2を再起動してSSMをリカバリします...
[1] SSM:
[2] SSM: Online
SSM Online確認。デプロイを継続します。

（直後のデプロイステップのログ）
CommandId: 767628ad-4cc0-4059-ab2d-a082018c85c4
[1] Status: Pending
[2] Status: Pending
[3] Status: Pending
... （最後まで Pending のまま）
```

## 根本原因

`describe-instance-information` の **`PingStatus=Online` は SSM Agent → SSM サービスへの
ハートビート成立のみを示す指標**であり、Agent 内部のコマンド受信ワーカーが
コマンドを受け取って実行できる状態になったこととは**別タイミング**。

EC2 stop/start 直後は以下が順次起動するが、Online 検知はそのうち最初の段階で成立する：

| 段階 | 状態 | PingStatus |
|------|------|------------|
| 1 | EC2 OS 起動 / SSM Agent プロセス起動 | NotRegistered → Online |
| 2 | SSM Agent ハートビート開始 | **Online**（ここで検知される） |
| 3 | コマンド受信ワーカー起動・MGS 接続確立 | Online |
| 4 | Docker daemon / IMDS credentials 提供開始 | Online |

旧実装は段階2で `break` していたため、段階3完了前に `send-command` を発行 →
コマンドが配信されず Pending 滞留 → TimedOut。

`PingStatus=Online` を1回検知しただけで「使える」と判定するのは早すぎた。

### 022 との違い

| 不具合 | 事象 | タイミング |
|--------|------|-----------|
| 022 | デプロイ開始時に SSM Agent が ConnectionLost で配信不能 | リカバリ機構が**未整備**だった時期 |
| 025 | リカバリ機構が動いた**直後**に Agent が温まりきらず配信不能 | リカバリ機構が**動作した結果**として発生 |

022 の修正で「リカバリを自動化」したが、リカバリ完了判定が甘かったために
**022 を踏まないようにしたら 025 を踏んだ** という連鎖。

## なぜ CI で検知できなかったか

- 通常のデプロイでは `PingStatus=Online` のまま稼働しているため、リカバリ経路自体が走らない
- `ConnectionLost` → `stop/start` → `Online` → `send-command` という
  特定シーケンスでのみ発生
- 段階3の完了を直接観測できる SSM API がない

## 修正内容

[`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml) のリカバリ後
Online 確認ループを以下のように強化：

```diff
-ONLINE=false
-for i in $(seq 1 18); do
+ONLINE_STREAK=0
+ONLINE_OK=false
+for i in $(seq 1 30); do
   sleep 10
   STATUS=$(aws ssm describe-instance-information ... --query "...PingStatus" --output text)
-  echo "[$i] SSM: $STATUS"
+  echo "[$i] SSM: ${STATUS:-(empty)} (streak=$ONLINE_STREAK)"
   if [ "$STATUS" = "Online" ]; then
-    ONLINE=true
-    break
+    ONLINE_STREAK=$((ONLINE_STREAK + 1))
+    if [ "$ONLINE_STREAK" -ge 3 ]; then
+      ONLINE_OK=true
+      break
+    fi
+  else
+    ONLINE_STREAK=0
   fi
 done

-if [ "$ONLINE" = "false" ]; then
+if [ "$ONLINE_OK" = "false" ]; then
   echo "ERROR: SSM Online連続検知タイムアウト（300秒）"
   exit 1
 fi
-echo "SSM Online確認。デプロイを継続します。"
+echo "SSM Online連続確認。コマンドパイプライン安定化のため60秒待機..."
+sleep 60
+echo "SSM 安定化待機完了。デプロイを継続します。"
```

主な変更点：

1. **連続3回 Online 検知** で初めて通過（10秒間隔なので最低20秒は連続 Online を要求）
2. **追加で 60秒 の安定化待機** を入れて、コマンド受信パイプラインの起動完了を待つ
3. ループ上限を 18→30 に拡張（300秒）
4. ストリークが途切れたらカウンタをリセット（一時的に Online 表示でも実態が伴わないケースを弾く）

### 即時復旧手順（手動）

リカバリ後に Pending 滞留が起きた場合：

```bash
# Pending しているコマンドをキャンセル
aws ssm cancel-command --command-id <COMMAND_ID> --region ap-southeast-2

# 60〜90秒待ってから GitHub Actions を再実行
```

## 再発防止

| 観点 | 対策 |
|------|------|
| Online 判定の厳格化 | `PingStatus=Online` の連続検知 + 追加待機を必須化 |
| Pending 滞留の検知 | デプロイステップ側で長時間 Pending が続いた場合に StatusDetails を出すロジックは 024 で対応済み |
| 関連 | 022（事前検知）, 001（SSM ConnectionLost 全般）, 024（Failed 時のログ強化）|

## 次のアクション
本修正後の次回デプロイ（特に EC2 が ConnectionLost 状態からの起動を踏むケース）で
- リカバリ後の Online 連続検知 + 60秒待機が機能するか
- その後の `send-command` が即座に `InProgress` に進むか

を観測し、解消が確認できたら本ドキュメントを ✅ 解決済 に更新する。
