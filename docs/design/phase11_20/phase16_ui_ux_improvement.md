
# フェーズ16：UIデザイン改善

## ステータス
🟡 着手中（Step 1 のうち「商品有効/無効スイッチ」実装完了 / 予約管理画面・UI 全般改善は未着手）

## 範囲
- Amazia Console  
- Amazia Market  
- UI/UX デザイン全般

## 機能概要
- Amazia Console と Amazia Market の UI を改善し、ユーザビリティと視認性を向上させる  
- Console は現行の雰囲気を維持しつつ、より洗練された管理画面へ  
- Market は Amazon の UI/UX を参考に、直感的で使いやすい EC サイトとして仕上げる

---

# Step 1：予約管理画面の追加と商品有効/無効スイッチ

## 1-1. 予約管理画面（Console）

- 予約状態の商品だけを表示する画面を新設する
- 売上管理から予約中の商品をフィルタリングで除外できるようにする
- 売上分析は予約商品を除外する
  - 「見込み表示」ボタン押下で予約も含めた集計に切り替える

## 1-2. 商品有効/無効スイッチ（Console 商品マスタ） ✅ 実装完了（2026-05-07）

### 背景・目的
現状、商品を Market から非表示にするには `publish_start` / `publish_end`（公開期間）を設定する必要がある。  
運用上「期間調整なしで、即時に Market から外したい／戻したい」というユースケースが頻発するため、公開期間とは独立した **手動の有効/無効スイッチ** を追加する。

### 設計方針
- `products` テーブルに **`is_active BOOLEAN NOT NULL DEFAULT TRUE`** を追加する
  - 既存の `status_code`（販売段階：`WAITING` / `RESERVATION` / `ON_SALE`）は **販売段階を表す軸** として残す
  - `is_active` は **Market 露出 ON/OFF を表す直交軸** として扱う
  - 例：`status_code = ON_SALE` かつ `is_active = FALSE` ＝「販売中扱いだが一時的に Market 非表示」
- Market 側の表示判定は **`is_active = TRUE` AND 公開期間条件** の AND 結合とする
  - 既存の `PreorderStatusService#isPublished()` の判定窓口に `is_active` チェックを追加する
- 公開期間（`publish_start` / `publish_end`）は従来通り「期間スケジュール」用途として残す（廃止しない）

### DB 変更
- `amazia-core/src/main/resources/schema.sql` に `ALTER TABLE products ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE` を追記（冪等）
- `Product` エンティティに `isActive` フィールドを追加
- `docs/database_design/TBL_products.md` のカラム表に追記

### API 変更（Core / Console）
- Core `GET/PUT /products/{id}` のレスポンス・リクエスト DTO に `isActive` を追加
- Core `GET /products`（Market 露出用エンドポイント）の WHERE 句に `is_active = TRUE` を追加
- Console `routes/api/Product.php` の Pass-through で `isActive` を透過
- 設計書 `docs/api_design/Core_API.md` / `Console_API.md` / `Market_API.md` を更新

### UI 変更（Console）
- `ProductForm.vue`：商品名のすぐ下、または「ステータス」項目のすぐ近くに **「Market 公開」スイッチ（a-switch）** を追加
  - ON＝有効（`is_active = TRUE`）／OFF＝無効（`is_active = FALSE`）
  - 既定値は ON
- `ProductList.vue`：一覧の各行に有効/無効バッジを表示し、無効商品はグレーアウト表示
- 一覧上部にフィルタ「有効のみ／無効のみ／すべて」を追加（既定：すべて）

### TDD テストケース
- Core
  - `is_active = FALSE` の商品は Market 一覧 API のレスポンスに含まれない
  - `is_active = TRUE` かつ公開期間内なら表示される
  - 公開期間外なら `is_active = TRUE` でも表示されない（AND 条件）
  - PUT で `isActive` を切り替えできる
- Console
  - 商品編集画面でスイッチを OFF にして保存すると永続化される
  - 一覧フィルタが正しく機能する
- Market
  - 無効化された商品は商品詳細 URL を直叩きしても 404 / 一覧から消える

---

# 機能詳細

---

## 🖥 Amazia Console（管理画面）

### UI改善方針
- 現行の雰囲気は維持しつつ、以下の観点で洗練させる  
  - 情報の階層化（見出し・カード・タブの活用）  
  - 一覧画面の視認性向上（行間・色・アイコン）  
  - 操作ボタンの統一（配置・色・サイズ）  
  - フォーム入力のガイド強化（プレースホルダー・バリデーション表示）  
  - レスポンシブ対応の最適化  

### 対象画面例
- 売上管理  
- 商品管理  
- ワークフロー管理  
- 配送管理  
- 操作履歴  

---

## 🛒 Amazia Market（ECサイト）

### UI改善方針
- **Amazon の UI に寄せる**  
  - ヘッダー構成（検索バー・カテゴリ・アカウント・カート）  
  - 商品一覧のカードデザイン  
  - 商品詳細ページのレイアウト（画像・価格・説明・購入ボタン）  
  - レビュー表示のスタイル  
  - カート画面・購入画面の導線  
  - 配送情報の選択 UI（Amazon の「お届け先を選択」風）  

### 改善対象
- TOPページ  
- 商品一覧  
- 商品詳細  
- カート  
- 決済画面  
- 購入履歴  
- 予約商品表示  

---

# 技術検討事項
- UIフレームワークの選定（Bootstrap / Tailwind / Vuetify / Chakra UI など）  
- ダークモード対応の要否  
- コンポーネント化による保守性向上  
- Market の Amazon 風 UI をどこまで再現するか（完全模倣は避ける）  
- スマホ最適化（特に Market はモバイル比率が高い想定）  
- アクセシビリティ（色覚対応・キーボード操作対応）

---

# TDDテストケース  
※UI改善フェーズのため、主に E2E / 表示確認系

## Amazia Console / PHPUnit（または Dusk）
- 一覧画面のレイアウトが崩れず表示される  
- ボタン配置が統一されている  
- フォームのバリデーション表示が正しく動作する  

## Amazia Market / PHPUnit（または Dusk）
- 商品一覧が正しいレイアウトで表示される  
- カートアイコン・検索バーが正常に動作する  
- 商品詳細ページのレイアウトが崩れない  
- モバイル表示で UI が最適化されている  
