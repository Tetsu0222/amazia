# Amazia

## アーキテクチャ図

![アーキテクチャ図](docs/architecture.svg)

---

## ドキュメントマップ

```
docs/
├── architecture.svg          # システムアーキテクチャ図
├── analysis/                 # 分析レポート
│   └── 20260503_trouble_analysis.md
├── design/                   # 設計・実装計画
│   ├── implementation_plan.md    # フェーズ別実装計画（全体）
│   ├── phase6/               # フェーズ6：Excel一括登録
│   ├── phase7/               # フェーズ7：一括削除・一括編集
│   ├── phase8/               # フェーズ8：商品マスタ機能
│   └── phase9/               # フェーズ9：商品画像登録
└── troubles/                 # 不具合記録
    ├── README.md             # 不具合一覧・再発防止アクション
    ├── 001_ssm_connection_lost.md
    ├── 002_mysql_host_not_allowed.md
    ├── 003_ssm_command_queue_stuck.md
    ├── 004_ec2_ip_changed_after_restart.md
    ├── 005_nginx_console_403.md
    ├── 006_composer_platform_ext_missing.md
    └── 007_excel_import_422.md
```

---

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
今回は学習目的で複数技術を採用しています。

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

| システム | フレームワーク | UIライブラリ | 理由 |
|---|---|---|---|
| Amazia Market | React | Material UI (MUI) | ECサイト向けコンポーネントが豊富 |
| Amazia Console | Vue.js | Ant Design Vue | 管理画面・フォーム系に強い。ReactとVue両方を学ぶ狙いもある |

> UIはフェーズ4以降に整える。フェーズ1〜3はテンプレートを当てはめるだけにして、バックエンドのTDDに集中する。

---

## 実装計画

フェーズ別の詳細は [docs/design/implementation_plan.md](docs/design/implementation_plan.md) を参照。

| フェーズ | タイトル | ステータス |
|---------|---------|-----------|
| Phase 1 | 会員画面の実装 | ✅ 完了 |
| Phase 2 | 管理画面から商品登録 | ✅ 完了 |
| Phase 3 | Amaziaの骨格実装（フェーズ1＋2の統合） | ✅ 完了 |
| Phase 4 | 商品情報CRUD（Amaziaの基本機能） | ✅ 完了 |
| Phase 5 | フロントエンドのEC2公開（Nginx） | ✅ 完了 |
| Phase 6 | エクセルアップロードによる一括登録 | ✅ 完了 |
| Phase 7 | 一括削除・一括編集 | 🔲 未着手 |
| Phase 8 | 商品マスタ機能 | 🔲 未着手 |
| Phase 9 | 商品マスタへの画像登録 | 🔲 未着手 |
