# Amazia

## システム全体像

### 会員向けサイト
会員向けフロント
- 名前：Amazia Market
- 言語：React

### 管理画面
管理者が商品・会員・注文を操作
- 名前：Amazia Console
- 言語：PHP

### コアシステム
在庫・物流・バッチ処理
- 名前：Amazia Core
- 言語：Java

---

## 言語選定について

### フロント（React）と管理画面（PHP）を分けた理由
フロントと管理画面は仕様変更が頻繁に発生する領域なので、変更に強い React をフロントに採用。

一方で管理画面はフォーム中心で、サーバーサイドレンダリングの PHP の方が実装・保守が軽い。

さらに、学習目的でフロントとバックを異なる技術で構築することで、技術選定の幅を広げる狙いもある。

> ※本来は React か PHP のどちらかに統一した方が、採用コスト・保守コストは下がる。
> 今回は「学習と技術理解のためにあえて分けている」。
> Reactを使ってみたいからでは断じてない。

### 在庫管理・バッチ処理を Java にした理由
在庫管理やバッチ処理は "スピードよりも確実性・堅牢性" が求められる領域。
Java は型安全性・エコシステム・実行速度・信頼性の面で非常に強く、長期運用に向いた技術。
さらに、Java エンジニアは市場に多く、採用しやすいという現実的なメリットもある。

> ※決して、色々な言語を混ぜた方が障害が発生しやすくなるので、TDDやCI/CDの勉強になりそうだと思ったわけではない。

---

## まとめ
実務ではチーム構成や採用コストを踏まえて技術を統一する判断も行いますが、今回は学習目的で複数技術を採用しています。

---

## DB設計

### productsテーブル（商品マスタ）

| カラム名 | 型 | 説明 |
|---|---|---|
| id | BIGINT (PK, AUTO_INCREMENT) | 商品ID |
| name | VARCHAR(255) | 商品名 |
| description | TEXT | 商品説明 |
| created_at | DATETIME | 登録日時 |
| updated_at | DATETIME | 更新日時 |

### product_pricesテーブル（価格履歴）

価格はスナップショットとして管理。注文時の価格を履歴として保持するため、商品マスタと分離。

| カラム名 | 型 | 説明 |
|---|---|---|
| id | BIGINT (PK, AUTO_INCREMENT) | 価格ID |
| product_id | BIGINT (FK) | 商品ID |
| price | INT | 価格（円） |
| valid_from | DATETIME | 有効開始日時 |
| valid_to | DATETIME | 有効終了日時（NULLは現在有効） |

### product_stocksテーブル（在庫状態）

在庫は「現在の状態」を示すため商品マスタと分離。バッチ処理での在庫操作の影響範囲を最小化。

| カラム名 | 型 | 説明 |
|---|---|---|
| id | BIGINT (PK, AUTO_INCREMENT) | 在庫ID |
| product_id | BIGINT (FK) | 商品ID |
| stock | INT | 在庫数 |
| updated_at | DATETIME | 更新日時 |

---

## API定義（Amazia Core）

### 商品情報

| メソッド | エンドポイント | 説明 | レスポンス |
|---|---|---|---|
| GET | /api/products | 商品一覧取得 | 200 + 商品リスト |
| GET | /api/products/{id} | 商品1件取得 | 200 / 404 |
| POST | /api/products | 商品登録 | 201 + 登録データ |
| PUT | /api/products/{id} | 商品更新 | 200 + 更新後データ |
| DELETE | /api/products/{id} | 商品削除 | 204 |
| POST | /api/products/bulk | 商品一括登録 | 201 + 件数 |

### リクエスト/レスポンス例

**POST /api/products（登録）**
```json
// リクエスト
{
  "name": "商品A",
  "description": "商品Aの説明",
  "price": 1000,
  "stock": 100
}

// レスポンス 201
{
  "id": 1,
  "name": "商品A",
  "description": "商品Aの説明",
  "price": 1000,
  "stock": 100,
  "created_at": "2026-05-03T10:00:00",
  "updated_at": "2026-05-03T10:00:00"
}
```

**GET /api/products（一覧）**
```json
// レスポンス 200
[
  { "id": 1, "name": "商品A", "price": 1000, "stock": 100 },
  { "id": 2, "name": "商品B", "price": 2000, "stock": 50 }
]
```

---

## UIテンプレート方針

| システム | テンプレート | 理由 |
|---|---|---|
| Amazia Market | Material UI (MUI) | ECサイト向けコンポーネントが豊富 |
| Amazia Console | Ant Design | 管理画面・フォーム系に強い |

> UIはフェーズ4以降に整える。フェーズ1〜3はテンプレートを当てはめるだけにして、バックエンドのTDDに集中する。

---

## 実装計画

### フェーズ1：会員画面の実装
1. 会員がアクセス - Amazia Market
2. Auth - Amazia Market
3. TOP画面表示 - Amazia Market
4. 商品情報検索 - Amazia Market
5. 商品情報取得 - Amazia Core
6. 商品情報表示 - Amazia Market

#### TDDテストケース（JUnit / Amazia Core）
- 商品一覧が取得できること（GET /api/products → 200 + JSONリスト）
- 存在しない商品IDを指定したとき404が返ること

### フェーズ2：管理画面から商品登録
1. 社員がアクセス - Amazia Console
2. Auth - Amazia Console
3. TOP画面表示 - Amazia Console
4. 商品情報登録 - Amazia Console
5. 商品情報のDB登録 - Amazia Core
6. 商品情報登録結果 - Amazia Market

#### TDDテストケース（JUnit / Amazia Core）
- 商品が登録できること（POST /api/products → 201 + 登録データ）
- 必須項目が欠けているとき400が返ること

### フェーズ3：Amaziaの骨格実装（フェーズ1＋2の統合）
1. 社員がアクセス - Amazia Console
2. Auth - Amazia Console
3. TOP画面表示 - Amazia Console
4. 商品情報登録 - Amazia Console
5. 商品情報のDB登録 - Amazia Core
6. 商品情報登録結果 - Amazia Market
7. 会員がアクセス - Amazia Market
8. Auth - Amazia Market
9. TOP画面表示 - Amazia Market
10. 商品情報検索 - Amazia Market
11. 商品情報取得 - Amazia Core
12. 商品情報表示（Amazia Consoleで登録したもの） - Amazia Market

#### TDDテストケース（JUnit / Amazia Core）
- Consoleで登録した商品がMarketの一覧に表示されること（統合確認）

#### TDDテストケース（PHPUnit / Amazia Console）
- 商品登録フォームを送信するとCoreのAPIにリクエストが飛ぶこと

### フェーズ4：商品情報CRUD（Amaziaの基本機能）
#### 範囲
- 3システム

#### 機能概要
- 商品情報一覧画面
- 商品情報登録画面（フェーズ2の強化）
- 商品情報編集画面
- 商品情報削除画面

#### TDDテストケース（JUnit / Amazia Core）
- 商品が更新できること（PUT /api/products/{id} → 200 + 更新後データ）
- 商品が削除できること（DELETE /api/products/{id} → 204）
- 削除済み商品を取得しようとすると404が返ること

#### TDDテストケース（PHPUnit / Amazia Console）
- 編集フォームに既存データが初期表示されること
- 削除操作後に一覧画面にリダイレクトされること

### フェーズ5：エクセルアップロードによる一括登録
#### 範囲
- Amazia Console
- Amazia Core

#### 機能概要
1. 商品情報一覧画面 - Amazia Console
2. エクセルアップロード - Amazia Console
3. ExcelをCSVに変換 - Amazia Console
4. CSVをJSONに整形 - Amazia Console
5. REST APIで一括登録リクエスト - Amazia Core
6. DB登録 - Amazia Core
7. 商品情報一覧画面（再表示） - Amazia Console

#### TDDテストケース（PHPUnit / Amazia Console）
- Excelファイルを正しくCSVに変換できること
- 不正なファイル形式をアップロードしたときエラーになること

#### TDDテストケース（JUnit / Amazia Core）
- 複数商品をまとめて登録できること（POST /api/products/bulk → 201 + 件数）
- 一部データが不正なとき、正常データだけ登録されること（部分成功）
