
# フェーズ12：ワークフロー機能（承認フロー）

## ステータス
🔲 未着手  

## 範囲
- Amazia Console  
- Amazia Core  
- DB設計

## 機能概要
- Amazia Console にワークフロー（承認フロー）機能を追加する
- 一般ユーザーが行う以下の操作は「申請 → 承認 → 反映」のプロセスを必須とする
  - 商品の公開
  - 在庫数の変更
  - 価格の変更
- 管理者は承認権限を持ち、権限者による直接変更はワークフロー不要

## 機能詳細

### ワークフロー一覧画面（Amazia Console）
- 申請一覧を表示
- ステータス別フィルタ（申請中 / 承認済 / 却下 / 取り下げ）
- 申請内容の詳細確認
- 承認・却下操作（管理者のみ）

### ワークフロー対象操作
- **商品公開**
  - 一般ユーザー：申請が必要
  - 管理者：即時反映可能  
- **在庫数変更**  
  - 一般ユーザー：申請が必要  
  - 管理者：即時反映可能  
- **価格変更**  
  - 一般ユーザー：申請が必要  
  - 管理者：即時反映可能  

### ワークフロー処理フロー
1. 一般ユーザーが変更を申請
2. 申請情報テーブルにレコード登録
3. 管理者が承認または却下
4. 承認時のみ対象データへ反映

### ロール別権限者
1. 一般ユーザー：権限無
2. スーパーバイザー：権限無
3. 管　理　者：権限有
4. 上位管理者：権限有
5. エターナルフォースバイザー：権限有

#### 制約事項
- 申請中のレコードは更新不可  
- 取り下げは「申請者本人」または「権限者」が可能  
- 権限者（管理者）による変更はワークフローを経由しない

## ワークフロー状態遷移図（横着）
### 商品の公開
1.商品登録(価格やSKU設定など)
  - 下書き or 申請中
    - 作成する際に、保存と申請ボタンを用意する。
    - 保存ボタン⇒下書きとしてただただ保存
    - 申請ボタン⇒申請先をモーダル画面で表示⇒選択⇒登録
      - スーパーバイザーを選択
      - 申請中は更新できない。
        - 取り下げは申請者と管理者であれば可能
    - 申請時のレコード登録
      - workflow_requests_detailに2レコード登録する。
        - step_number = 1 , step_number = 2 で　2レコード
        - workflow_requests_idでworkflow_requestsとリレーションする。
          - 当然、workflow_requestsと1対N
        - step_number = 1 の申請先idと申請先(和名)は登録する。
          - step_number = 2 の申請先idと申請先(和名)は空欄で登録
        - step_number = 1,2　の　承認者idと承認者(和名)を空欄で登録
        - ワークフロー表示ラベルを和名で持つ(スナップショット)
          - step_number = 1  "スーパーバイザー："
          - step_number = 2  "管理者:"
            - ワークフロー一覧画面の表示に使用する。
2.承認フェーズOne
  - ワークフロー一覧画面
    - ワークフロー単位で表示
      - 申請先の2レコードを1行の1セルで表示
      - 承認者の2レコードを1行の1セルで表示
        - 例(空欄でも表示した方が見通しが良い。)
          申請先　                    承認者
           スーパーバイザー：[和名]     スーパーバイザー：
           管理者:                     管理者:
  - ワークフロー詳細画面
    - ワークフローの内容を表示
      - 承認ボタンと否認ボタンを用意する。
        - 承認ボタン⇒申請先をモーダル画面で表示⇒選択⇒登録
          - 管理者を選択
            - workflow_requests_detailのレコードを更新
            - step_number = 1 の承認者idと承認者(和名)を更新
            - step_number = 2 の申請先idと申請先(和名)を更新(Null⇒実値)
            - step_number = 2 の承認者idと承認者(和名)を空欄のまま
  - ステータスは申請中のまま
3.承認フェーズTwo
  - ワークフロー一覧画面
    - ワークフロー単位で表示
  - ワークフロー詳細画面
    - ワークフローの内容を表示
      - 承認ボタンと否認ボタンを用意する。
        - 承認ボタン
          - ステータスは承認済
            - workflow_requests_detailのレコードを更新
            - step_number = 2 の承認者idと承認者(和名)を更新(Null⇒実値)

#### まとめ
▼ 初期登録
  下書き保存 → workflow_requests は作らない
  申請 → workflow_requests + detail（2レコード）作成
  detail.step1.status = pending
  detail.step2.status = waiting（まだ開始していない）

▼ 承認フェーズ1（スーパーバイザー）
  承認 → detail.step1.status = approved
  次のステップを pending にする
  detail.step2.status = pending
  workflow_requests.status は pending のまま

▼ 承認フェーズ2（管理者）
  承認 → detail.step2.status = approved
  workflow_requests.status = approved
  承認後の反映処理へ

▼ 否認
  どの step でも reject されたら
  workflow_requests.status = rejected
  detail の該当 step.status = rejected
  以降の step は waiting のまま

▼ 取り下げ
  workflow_requests.status = canceled
  detail の全step.status = canceled

### 在庫数の変更(ヒューマンエラーや盗人の対応になる)
step_number = 1：スーパーバイザー
step_number = 1：管理者
step_number = 2：上位管理者
※step_number が同じ場合は「並列」とみなす

### 価格の変更
step_number = 1：スーパーバイザー
step_number = 2：管理者


## DB設計（追加）

### workflow_requests テーブル（新規）

| カラム | 説明 |
| --- | --- |
| id | PK |
| target_type | product / price / stock |
| target_id | 対象ID |
| requested_by | 申請者 |
| completed_at | 完了日時（NULL＝未完了） |
| result_type | approved / rejected / canceled |
| created_at | 申請日時 |
| updated_at | 更新日時 |

### workflow_requests_detail テーブル（新規）

| カラム | 説明 |
| --- | --- |
| workflow_requests_id | 親ID |
| step_number | 1, 2 , 3 |
| target_role | supervisor / admin | 申請先以外でも承認できるようにする拡張を想定 |
| destination_user_id | 申請先id |
| destination_name | 申請先名（スナップショット） |
| approver_user_id | 承認者id |
| approver_name | 承認者名（スナップショット） |
| status | pending / approved / rejected / waiting |
| updated_at | 更新日時 |

※必要に応じて詳細は調整

## JSONスキーマ
{
  "target_type": "product",
  "target_id": 12345,
  "fields": [
    {
      "field": "price",
      "before": 1200,
      "after": 1500
    },
    {
      "field": "stock",
      "before": 10,
      "after": 20
    }
  ],
  "meta": {
    "requested_at": "2025-01-01T12:00:00",
    "requested_by_name": "山田太郎",
    "reason": "キャンペーン対応のため"
  }
}

## 反映方式
同期でええ

##

## 技術検討事項
- ワークフローの対象操作をどこまで拡張するか  
- 差分情報の保持形式（JSON / 専用テーブル）  
- 承認後の反映処理を同期・非同期どちらで行うか  
- ロール・権限設計（一般 / 管理者 / スーパーバイザーなど）

## TDDテストケース（Amazia Core / JUnit）
- 申請が workflow_requests に登録されること  
- 承認時に対象データへ正しく反映されること  
- 申請中の対象データが更新不可であること  
- 取り下げが正しく処理されること  

## TDDテストケース（Amazia Console / PHPUnit）
- 一般ユーザーが申請すると一覧に表示されること  
- 管理者が承認・却下できること  
- 権限者による直接変更がワークフローを経由しないこと  
