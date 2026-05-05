
# フェーズ12：ワークフロー機能（承認フロー）【改訂版 v2】

## ステータス
✅ 実装済（2026-05-05）

## 範囲
- Amazia Console  
- Amazia Core  
- DB設計

---

# 1. 機能概要

- Amazia Console にワークフロー（承認フロー）機能を追加する。
- 一般ユーザーが行う以下の操作は「申請 → 承認 → 反映」のプロセスを必須とする。
  - 商品の公開
  - 在庫数の変更
  - 価格の変更
- 管理者以上の権限者は承認権限を持ち、直接変更可能（ワークフロー不要）。

---

# 2. ロール定義（改訂）

| ロール | 承認権限 | 備考 |
| --- | --- | --- |
| 一般ユーザー | × | 申請のみ |
| スーパーバイザー | △ | ステップ1の承認対象 |
| 管理者 | ○ | ステップ1/2の承認対象 |
| 上位管理者 | ○ | ステップ2/3の承認対象 |
| エターナルフォースバイザー | ○ | 全ステップ承認可能 |

---

# 3. target_role と destination_user_id の関係（新規追記）

ワークフローの承認対象は **ロールベース** と **個別ユーザー指定** の両方に対応する。

| target_role | destination_user_id | 意味 |
| --- | --- | --- |
| supervisor | NULL | 「スーパーバイザー全員」が承認対象 |
| supervisor | 123 | 「ユーザーID=123 のスーパーバイザー」が承認対象 |
| admin | NULL | 「管理者全員」が承認対象 |
| admin | 456 | 「ユーザーID=456 の管理者」が承認対象 |

### 仕様まとめ
- **destination_user_id が NULL の場合**  
  → *target_role に属する全ユーザーが承認対象*

- **destination_user_id に値がある場合**  
  → *そのユーザーのみが承認対象*

これにより、  
- ロール承認（部署承認）  
- 個別承認（特定の担当者承認）  
の両方を実現する。

---

# 4. ワークフロー対象操作

### 商品公開
- 一般ユーザー：申請必須  
- 管理者以上：即時反映  

### 在庫数変更（改訂）
- step1（並列）：スーパーバイザー、管理者  
- step2：上位管理者  
- 並列ステップの承認条件：  
  **全員承認で次ステップへ進む**

### 価格変更
- step1：スーパーバイザー  
- step2：管理者  

---

# 5. ワークフロー処理フロー（改訂）

## 5.1 親ステータス定義

| status | 説明 |
| --- | --- |
| pending | 承認中 |
| approved | 全ステップ承認済 |
| rejected | いずれかのステップで否認 |
| canceled | 取り下げ |

`completed_at` は **approved / rejected / canceled に遷移した後、反映処理が成功したタイミングでセットする**。

---

## 5.2 ステップステータス定義

| status | 説明 |
| --- | --- |
| waiting | まだ開始されていない |
| pending | 承認待ち |
| approved | 承認済 |
| rejected | 否認 |
| canceled | 親が取り下げられた |

---

## 5.3 並列ステップの否認時の挙動（新規追記）

例：在庫変更の step1（並列）  
- supervisor（A）  
- admin（B）

### supervisor が reject した場合
- A.status = rejected  
- B.status = **waiting のまま**（承認不要扱い）  
- workflow_requests.status = rejected  
- UI 表示例：  
  - A：否認  
  - B：承認不要（他ステップで否認済）

### 理由
- 並列ステップは「全員承認で次へ進む」ため、  
  **1人でも否認した時点でワークフローは終了**  
- 他の pending ステップは承認不要となるため waiting のまま固定する。

---

## 5.4 取り下げ
- 申請者本人 or 権限者が可能  
- 承認済みステップがあっても取り下げ可能（反映前であれば）  
- 全 step.status = canceled  
- workflow_requests.status = canceled  
- completed_at は反映処理成功後にセット  

---

# 6. 対象データのロック仕様

### ロック対象
- 対象エンティティ全体をロックする

### ロック方式
- 楽観ロック（version カラム）  
- workflow_requests.status = pending の場合は更新不可（409 Conflict）

---

# 7. ワークフロー一覧画面（Amazia Console）

- ステータス別フィルタ（pending / approved / rejected / canceled）
- 並列ステップは同一 step_number としてまとめて表示
- 否認済ワークフローでは、未処理ステップを「承認不要」と表示

---

# 8. DB設計（改訂）

## 8.1 workflow_requests

| カラム | 説明 |
| --- | --- |
| id | PK |
| target_type | product / price / stock |
| target_id | 対象ID |
| requested_by | 申請者 |
| status | pending / approved / rejected / canceled |
| payload | JSON（差分情報） |
| completed_at | 完了日時（反映処理成功後） |
| created_at | 申請日時 |
| updated_at | 更新日時 |

---

## 8.2 workflow_requests_detail

| カラム | 説明 |
| --- | --- |
| id | PK |
| workflow_requests_id | 親ID |
| step_number | 1, 2, 3 |
| target_role | supervisor / admin / senior_admin / eternal_advisor |
| destination_user_id | NULL＝ロール全員、値あり＝個別指定 |
| destination_name | スナップショット |
| approver_user_id | 承認者ID |
| approver_name | 承認者名 |
| status | waiting / pending / approved / rejected / canceled |
| updated_at | 更新日時 |

---

# 9. JSONスキーマ（変更なし）

```json
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
```

---

# 10. 反映方式
- 同期処理  
- completed_at は **反映処理成功後** にセットする

---

# 11. 技術検討事項
- ロール承認と個別承認の両立（今回仕様で対応済）  
- 並列ステップの否認時の UI 表示  
- 排他制御（version カラム）  
- ロールマスタ化  

---

# 12. TDDテストケース（Amazia Core / JUnit）
- 並列ステップでの否認時、他ステップが waiting のままになること  
- completed_at が反映処理成功後にセットされること  
- target_role + destination_user_id の組み合わせテスト  

---

# 13. TDDテストケース（Amazia Console / PHPUnit）
- 並列ステップの否認時 UI 表示（承認不要）  
- ロール承認と個別承認の表示切替  
