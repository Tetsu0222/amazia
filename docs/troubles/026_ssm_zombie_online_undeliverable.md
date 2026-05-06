# 026: PingStatus=Online でも SSM コマンドが Undeliverable になる「ゾンビOnline」

## ステータス
✅ 解決済（2026-05-06）— phaseX-3 HTTPS 化の複数回デプロイでカナリア方式 + リカバリ機構が安定稼働。022/024/025 とともに ✅ 解決済としてクローズ

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

---

## 補足: AWS SSM サービス障害時の挙動（2026-05-05 観測）

本対応のテストデプロイ実行中、ap-southeast-2 リージョンで AWS SSM サービス側に障害が発生していた。
本機構は以下のように振る舞い、**無限リカバリループに陥らず明示的に失敗** した。

### 観測されたシーケンス

```
1. 事前 PingStatus 確認 → ConnectionLost 検知
2. recover_ssm 実行（stop/start）
3. Online 連続検知 (streak=3) 成功 ← Agent 自体は復活する
4. 60秒安定化待機
5. カナリア送信 → 12回連続 Pending → Undeliverable 判定
6. ゾンビOnlineと判定し recover_ssm 実行（2回目）
7. 同様にカナリア失敗 → exit 1
```

### 切り分けの経緯（反省点込み）

ジョブ失敗時、最初は EC2 / Agent / VPC / SG など**自分側の構成要因を疑った**。
最終的にユーザーが **Google Translate のポップアップから AWS Health Dashboard を開き、障害ステータスを発見** した。
この時点で AWS 側の障害が原因と確定。

#### 反省点
**「自分側を疑い尽くしてから外を疑う」のは姿勢として正しいが、並行で疑える項目は並行で確認すべきだった。**
具体的には、最初の Undeliverable を観測した時点で:
- Agent ログ確認（自分側）
- セキュリティグループ確認（自分側）
- **AWS Service Health Dashboard 確認（AWS 側）**

を **並行して** 行えば、切り分けが10〜20分早かった。

### 学びの記録方針

「過去の失敗から学んで、次回は効率的に切り分ける」ためのチェックリストとして、
将来のトラブル対応では **以下を最初の5分で並行確認** する：

| 項目 | 確認方法 | 所要時間 |
|------|---------|---------|
| AWS Service Health Dashboard | https://health.aws.amazon.com/health/status の該当リージョン・サービス | 1分 |
| EC2 状態 | コンソールで running + 3/3 OK | 1分 |
| SSM PingStatus | `aws ssm describe-instance-information` | 1分 |
| セキュリティグループ Outbound | コンソールで 443/0.0.0.0/0 の有無 | 1分 |
| 最近のデプロイ・設定変更 | git log / CloudTrail | 2分 |

これは本プロジェクトのトラブルシュート全般に適用する原則として
`docs/troubles/lessons.md`（フェーズ20で新設予定）に転記する。

### 設計の妥当性

本機構は AWS 側障害下でも以下の性質を満たしていることが実環境で確認できた：

| 性質 | 結果 |
|------|------|
| 異常を確実に検知する | ✅ ConnectionLost も Undeliverable も検知 |
| 自動リカバリを試みる | ✅ stop/start を最大2回実行 |
| 無限ループに陥らない | ✅ 最大2回でジョブを失敗させる |
| 失敗時に運用者に明示的通知 | ✅ exit 1 で GitHub Actions が失敗通知 |
| ログから原因切り分け可能 | ✅ 024 の StatusDetails 出力で Undeliverable 確定 |

「リカバリが失敗した」という事実そのものが
**自分側の問題ではなく外部要因を疑うべきシグナル** として機能した点も含め、
022〜026 の連鎖対応で構築した機構は実用的な水準に達したと判断する。
