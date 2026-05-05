# 026: PingStatus=Online でも SSM コマンドが Undeliverable になる「ゾンビOnline」

## ステータス
🟡 対応中（2026-05-05）

## 発症箇所
GitHub Actions `Deploy to EC2` ワークフロー
- `SSMキュー健全性確認・自動リカバリ` ステップ（[`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml) L80）
- 直後の `EC2 - Docker停止・ECR pull・Docker起動` ステップ（同 L186）

## 症状

EC2 は `running` + 3/3 チェックOK、SSM の `PingStatus` も `Online` を返している正常状態にも関わらず、
`send-command` で発行したコマンドが Pending のまま6分滞留 → `Failed` で落ちる。

024 のログ強化により StatusDetails を確認できるようになり、以下が確定：

```
[1] Status: Failed
===== StatusDetails =====
Undeliverable
===== StandardOutputContent =====

===== StandardErrorContent =====

```

すなわち **コマンドが SSM Agent に配信されていない**。
022 と同じ `Undeliverable` だが、PingStatus は正常という点が異なる。

### 発生条件（観測）
1. 直前のジョブを GitHub Actions UI からキャンセル
2. 7分未満で同ワークフローを再実行
3. EC2 状態は健全、PingStatus も Online

この条件で再現した。キャンセルされた前回コマンドが Agent 内部の MGS
（Message Gateway Service）セッションを破壊し、Agent はハートビートだけ生きているが
コマンド受信パイプラインが死んだ「ゾンビ Online」状態になっていたと推測。

## 根本原因

`PingStatus=Online` は **SSM Agent → SSM サービス間のハートビート成立** のみを示す指標で、
**コマンド配信パス（MGS セッション）の健全性** は別。

| 観測項目 | 何を保証するか |
|---|---|
| `EC2 instance state = running` | OS が動いている |
| `EC2 status check 3/3 OK` | OS / インスタンスメタデータが応答する |
| `PingStatus = Online` | SSM Agent プロセスが生きてハートビートしている |
| **（観測手段なし）** | **MGS セッションが生きてコマンドを受信できる** ← これが死ぬと Undeliverable |

つまり 022 で導入した PingStatus チェックでは「ゾンビ Online」を検知できず、
本来リカバリすべき状態を「正常」と誤判定してデプロイ本体に進んでしまっていた。

## なぜ CI で検知できなかったか

- 通常運用では「キャンセル直後の再実行」シーケンスを踏まないため発生しない
- AWS API には MGS セッションの状態を直接照会する手段がない
- 022 修正で「PingStatus=Online なら正常」という前提が成立していたが、
  本件はその前提を崩すケース

## 修正内容

[`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml) の
`SSMキュー健全性確認・自動リカバリ` ステップに **カナリアコマンド方式** を導入。

実コマンドを送る前に軽量な `echo canary-ok` を送り、配信成功を確認できなければ
ゾンビ Online と判定して stop/start リカバリに乗せる。

### 全体フロー

```
1. STUCK / PingStatus 確認
   ├─ 異常 → recover_ssm (stop/start + Online連続検知 + 60秒待機)
   └─ 正常 → 次へ

2. canary_check ("1回目")
   ├─ 配信成功 → デプロイ続行
   └─ 配信失敗 (Undeliverable / TimedOut / Pending滞留)
       ├─ recover_ssm
       └─ canary_check ("リカバリ後")
           ├─ 配信成功 → デプロイ続行
           └─ 配信失敗 → ジョブ失敗（手動調査が必要）
```

### 実装の要点

- `recover_ssm` と `canary_check` を bash 関数として共通化（リカバリ前後で再利用）
- カナリアは最大 12回×10秒 = 120秒で判定（健全時は10〜20秒で Success が返る）
- 120秒滞留した場合は `cancel-command` でキューから外してからリカバリへ
- リカバリ後のカナリアでも失敗したら `exit 1`（無限ループ回避）

### 即時復旧手順（手動）

ゾンビ Online を踏んだ場合の即時対応：

```bash
# 1. 詰まったコマンドをキャンセル
aws ssm cancel-command --command-id <COMMAND_ID> --region ap-southeast-2

# 2. EC2 を AWS コンソールから stop → start
#    （SSM 経由では再起動コマンドも届かないため AWS API / コンソールから直接）

# 3. running + 3/3 OK + PingStatus Online を確認後、ワークフロー再実行
```

## 再発防止

| 観点 | 対策 |
|------|------|
| 配信可能性の事前検証 | カナリアコマンドで実配信を確認してからデプロイ本体を送る |
| ゾンビ Online の検知 | PingStatus だけに頼らず、実コマンドの Success 到達を判定基準にする |
| キャンセル直後の再実行抑止 | ドキュメントに「キャンセル後は最低5分待つ」を記載検討 |
| 関連 | 001（ConnectionLost 全般）, 022（事前検知の最初の実装）, 024（ログ強化）, 025（リカバリ後 Pending）|

## 022 / 025 / 026 の関係

```
001: SSM ConnectionLost 全般 (手動 stop/start で復旧)
 ↓
022: デプロイ前の PingStatus 自動チェック+リカバリ機構を導入
 ↓
025: リカバリ後の Online 検知が早すぎて Pending 滞留 → 連続検知+60秒待機を追加
 ↓
026: PingStatus=Online でも Undeliverable になるケースがある → カナリア方式を導入 ← 本件
```

「リカバリ機構を強化するたびに次の隙間が露呈する」典型的な連鎖。
カナリアまで導入した本件で **配信の可否そのものを検証する** 段階に到達したため、
次の隙間が出るとすれば「カナリアは通るが本コマンドだけ失敗する」状況に限られる。

## 次のアクション

本修正後の次回デプロイで
- カナリアが Success を返してデプロイ本体が通常通り走るか
- ゾンビ Online を踏んだケースでも自動リカバリが機能するか

を観測。安定動作が確認できたら本ドキュメント・025・024 をまとめて ✅ 解決済 に更新する。
