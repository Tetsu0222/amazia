
# フェーズ16.5：Market UI 改善（Amazon ライク化）

## ステータス
✅ 実装完了（2026-05-08）。Step 5 Phase B〔Console Pass-through〕は §5-4 方針変更により廃止。レスポンシブ手動確認シート（[phase16_5_responsive_check.md](phase16_5_responsive_check.md)）はユーザー側でブラウザ実機確認が残り。

## 範囲
- Amazia Market（フロントエンドのみが中心。カート機能の正式実装に伴い Core / Console にも一部 API・スキーマ追加あり）
- UI/UX デザイン全般

## 機能概要
フェーズ16では Console 側の管理画面 UI 改善を扱ったが、Market 側（一般購入者向け EC サイト）の UI 改善は「フェーズ16後段で扱う」とスコープ外に切り出されたまま積み残っていた。本フェーズ16.5 はその切り出し分を独立フェーズとして実装する。

Amazia Market を **Amazon 風のレイアウト・配色**に寄せ、初見の利用者でも「EC サイトとして見慣れた構造」で迷わず買い物できる体験を目指す。具体的には以下の3画面を扱う：

1. **共通ヘッダー** — 濃紺ヘッダー＋検索バー＋カテゴリナビゲーション＋カートアイコン
2. **商品一覧** — 高密度な商品カードグリッド・並び替え・キーワード検索
3. **商品詳細** — 左：画像ギャラリー／中央：商品スペック／右：購入ボックスの3カラムレイアウト

加えて **正式なカート機能**（複数商品をまとめて Checkout する導線）を本フェーズで実装する。これにより Market が「単品購入専用サイト」から「一般 EC サイト」へ昇格する。

## フェーズ分割の経緯
- 元々はフェーズ16「UIデザイン改善」の一部として計画されていた
- フェーズ16 が Console 側だけで Step 1〜13 まで膨張したため、Market 側を独立フェーズ16.5 として切り出した
- ナンバリングは「フェーズ16の続き」を示す `.5` とする（フェーズ17 以降は予定通り）

## 関連フェーズ
- フェーズ13：Market 認証（会員登録・ログイン）
- フェーズ14 / 14.5：予約販売・予約ステータス
- フェーズ15：配送管理（住所・配送先）
- フェーズ16：Console UI 改善（本フェーズの親フェーズ）

## 設計方針

### デザイン基本方針
- **配色は Amazon 配色を参考に独自トーン**：商標トラブル回避のため完全コピーは避ける
  - ヘッダー濃紺 `#0F1A2B`（Amazon `#131921` ベースに微調整）
  - サブヘッダー `#1B2838`
  - アクセント（買い物ボタン・カート数バッジ）は山吹色 `#F0A93B`（Amazon の `#FEBD69` ベース）
  - 価格・リンク色 `#0066C0`
  - これらは `theme.js` に集約し、コンポーネントから直接色コードを書かない
- **フォントは MUI デフォルト（Roboto / Noto Sans JP）を維持**：日本語可読性優先
- **レイアウト構造は Amazon 踏襲**：ヘッダー2段（メインバー＋サブナビ）／商品一覧の高密度グリッド／詳細ページの3カラム構成

### コーディング規約準拠
- API エンドポイントは `src/features/<domain>/api/` 配下に集約（規約 5. React/Vue 節）
- コンポーネントには表示ロジックのみ。Cart 状態管理は `CartContext` に分離
- 環境変数を新規追加した場合は `docker-compose.yml` と `phpunit.xml` をセットで更新（CLAUDE.md 基本ルール）
- 新規 DB テーブル / API は同フェーズ内で `docs/database_design/` `docs/api_design/` `ops/healthcheck/required_tables.txt` を更新（CLAUDE.md DB/API メンテルール）

### スコープ外（明確化）
- **複数注文管理画面の刷新**（既存 `PurchaseHistory` の見た目改善は後続フェーズへ）
- **レコメンド機能**（「この商品を見た人はこんな商品も…」は将来のフェーズへ）
- **レビュー・評価機能**（フェーズ22以降の検討事項）
- **カテゴリの実機能化**（DB に categories テーブル追加・商品との関連付け・Console カテゴリ管理画面）
  → 本フェーズではヘッダーのカテゴリバーは **固定文言で見た目のみ**、リンク先は無効（`<a href="#" />`）
- **抽選・ポイント機能**（フェーズ21）
- **メルマガ機能**（フェーズ22）

---

# Step 1：共通テーマ・カラーパレット定義

### 1-1. 背景・目的
現状の Market は MUI デフォルト（青系プライマリ）のままで Amazon 風の落ち着いた濃紺基調になっていない。各画面で色コードを直書きすると改修が散らばるため、最初に **テーマファイルへ集約**する。

### 1-2. 設計方針
- `amazia-market/src/theme.js` を新規作成し、`createTheme()` で配色を一元定義
- `main.jsx` で `<ThemeProvider theme={theme}>` でラップ
- パレット：
  | 用途 | キー | カラーコード |
  |---|---|---|
  | ヘッダー背景（濃紺） | `header.main` | `#0F1A2B` |
  | サブヘッダー背景 | `header.sub` | `#1B2838` |
  | ヘッダー文字色 | `header.text` | `#FFFFFF` |
  | ホバー枠色 | `header.hoverBorder` | `#FFFFFF` |
  | アクセント（CTA） | `accent.main` | `#F0A93B` |
  | アクセント文字 | `accent.contrastText` | `#0F1A2B` |
  | 価格・リンク | `link.main` | `#0066C0` |
  | カードボーダー | `border.card` | `#DDDDDD` |
  | 背景（薄グレー） | `background.default` | `#EAEDED` |
  | カード背景 | `background.paper` | `#FFFFFF` |
- 既存 `primary` は MUI 互換のため青系を維持しつつ、Amazon 風要素ではカスタムキー（`accent` / `header` / `link`）を参照する

### 1-3. UI 変更
- `App.jsx`：`<CssBaseline />` の上に `<ThemeProvider theme={theme}>` を追加
- 既存コンポーネント（`AppHeader` / `ProductList` / `ProductDetail`）は Step 2 以降のリニューアル時に新パレットへ差し替える

### 1-4. DB 変更 / API 変更
- なし

### 1-5. TDD テストケース
- `theme.test.js`（新規）：`theme.palette.header.main` が `#0F1A2B` であること等のスナップショット
- 既存テスト（`AppHeader.test.jsx` / `ProductList.test.jsx`）が `<ThemeProvider>` ラップ後も緑であること
- E2E：背景色・ヘッダー色のリグレッション目視確認

---

# Step 2：ヘッダー2段化（メインバー＋カテゴリサブナビ）

### 2-1. 背景・目的
現状ヘッダーは1段で「Amazia Market（タイトル）」「ログイン」「会員登録」しか持たない。Amazon ライクな印象には **濃紺のメインバー** と **その下のカテゴリサブナビ** の2段構成が必須。同時に検索バーとカート導線をメインバーに乗せる。

### 2-2. 設計方針
- 既存 `components/AppHeader.jsx` を **2段構成にリニューアル**
- 上段（メインバー、`AppBar` 高さ約 60px）：
  - 左：ロゴ「Amazia Market」（クリックで `/`）
  - 中央：**検索バー**（Step 3 で実装。Step 2 では空のテキストフィールド枠だけ）
  - 右：「アカウント＆リスト」（hover でドロップダウン：ログイン/会員登録 or マイページ/購入履歴/ログアウト）
  - 右端：**カートアイコン**（Step 5 で実装。Step 2 ではアイコンと「カート」文言のみ・件数バッジは 0 固定）
- 下段（サブヘッダー、高さ約 36px）：
  - **カテゴリバー（見た目のみ）**：「すべて」「ファッション」「家電」「食品・飲料」「本」「ホビー」「セール」を横並び表示
  - 各リンクはダミー（`onClick` は no-op、ホバーで枠線がつくが遷移しない）
  - 仕様コメントを `CategoryNav.jsx` 冒頭に明記：「フェーズ16.5 時点ではダミー。categories テーブル追加時に実機能化」
- `Layout` コンポーネントの構造：
  ```
  <AppBar position="static">
    <Toolbar>...メインバー...</Toolbar>
  </AppBar>
  <Box sx={{ bgcolor: 'header.sub' }}>
    <CategoryNav />
  </Box>
  <Outlet />
  ```

### 2-3. UI 変更
- `components/AppHeader.jsx` 全面書き換え（既存 props・useAuth 連携は維持）
- `components/CategoryNav.jsx`（新規）
- `components/AccountMenu.jsx`（新規）：右上の「アカウント＆リスト」ドロップダウン
  - 未ログイン時：「ログイン」「会員登録」
  - ログイン時：「マイページ」「購入履歴」「ログアウト」
  - メールアドレスは `xs` で非表示、`sm` 以上で「こんにちは、{email}」を小さく表示
- レスポンシブ：`xs` では検索バーをメインバーから2段目（カテゴリバーの上）に移して縦に積む

### 2-4. DB 変更 / API 変更
- なし

### 2-5. TDD テストケース
- `AppHeader.test.jsx`：
  - メインバーにロゴ・検索フィールド・カートアイコンが存在
  - 未ログイン時に「ログイン」「会員登録」が表示
  - ログイン時に「マイページ」「ログアウト」が表示・メールが表示
- `CategoryNav.test.jsx`（新規）：
  - 7カテゴリすべてが表示
  - 各カテゴリリンクをクリックしても `navigate` が呼ばれない（ダミー）

---

# Step 3：検索バー（実機能）

### 3-1. 背景・目的
カテゴリは見た目のみだが、**キーワード検索は実機能** とする（ユーザー回答に基づく）。Amazon の中央検索バーを再現し、商品名・説明文に対する簡易フィルタを提供する。

### 3-2. 設計方針
- 検索の実装方式は **クライアントサイドフィルタ**（小規模データ前提・現状の商品数では十分）
  - サーバー側 `GET /api/products/market` のレスポンスに対し、フロントで `productName.includes(keyword) || description.includes(keyword)` でフィルタ
  - 将来 100 商品超になったらサーバーサイド検索（`?keyword=` クエリ）に拡張可能なよう、検索関数を `searchProducts(products, keyword)` として `features/products/searchUtils.js` に独立させる
- ヘッダー検索バーは **`/?q=<keyword>` への遷移**で `ProductList` のキーワードを反映
  - URL に `?q=` を持たせることで、ブックマーク可能・履歴復元可能・SSR 移行時にも整合
- 検索フォームの動作：
  - エンター or 検索ボタンクリックで `navigate('/?q=' + encodedKeyword)`
  - `ProductList` 側で `useSearchParams()` から `q` を取得し `searchProducts()` でフィルタ
  - `q` が空 or 未指定なら全商品表示

### 3-3. UI 変更
- `components/SearchBar.jsx`（新規）：MUI `TextField` ＋ `IconButton(Search)`
  - 山吹色（`accent.main`）の検索ボタン、`Enter` キーでも submit
  - `placeholder="Amazia を検索"`
  - 左側にカテゴリプリセレクト（`Select`）を置くが、Step 2 のカテゴリ同様 **見た目のみ**（送信時は `q` のみ反映）
- `features/products/pages/ProductList.jsx`：
  - 上部に「『{q}』の検索結果（X件）」見出しを追加（`q` がある時のみ）
  - 結果0件時：「該当する商品がありません」と「すべての商品を見る」リンク

### 3-4. DB 変更
- なし

### 3-5. API 変更
- 本フェーズ：なし（クライアントサイドフィルタ）
- 将来拡張用に `Market_API.md` の `GET /api/products/market` 節へ「予定：`?keyword=` クエリ追加（フェーズXX）」と申し送りメモを残す

### 3-6. TDD テストケース
- `searchUtils.test.js`（新規）：
  - キーワードが商品名に含まれる商品だけ返す
  - キーワードが説明文に含まれる商品だけ返す
  - 大文字小文字を区別しない
  - 空キーワードは全件返す
- `SearchBar.test.jsx`（新規）：
  - エンターで `navigate('/?q=…')` が呼ばれる
  - 検索ボタンクリックでも同様
- `ProductList.test.jsx`（追記）：
  - `?q=テスト` 付きでアクセスすると「テスト」を含む商品だけ表示される

---

# Step 4：商品一覧の Amazon 風カードグリッド

### 4-1. 背景・目的
現状の `ProductList` は MUI Card を3カラムで並べる素直な実装で、Amazon の高密度・情報量重視のグリッドに比べて余白が大きく「シンプルすぎる」印象。これを Amazon 風に詰める。

### 4-2. 設計方針
- ブレークポイント別カラム数を変更：
  | breakpoint | 現行 | 改善後 |
  |---|---|---|
  | xs | 1 | 2 |
  | sm | 2 | 3 |
  | md | 3 | 4 |
  | lg | （なし） | 5 |
- カード内レイアウト：
  - 画像：高さ 200px → **180px に縮小**、`objectFit: contain`、背景白
  - 商品名：1行 noWrap → **2行 ellipsis**（Amazon 流に「途中で切れた商品名」を許容）
  - 価格：青色（`link.main`）で `¥X,XXX` 強調・「〜」は予約商品のみ表示
  - 在庫表示：「残りX点」に文言変更（緊張感を演出）
  - 「カートに入れる」**山吹色ボタン**をカード下部に追加（Step 5 で機能実装）
- カード全体の `Card` から `Paper` に変更し、ホバー時に薄影（Amazon 流）
- 並び替えセレクト（`Select`）を一覧上部に追加：
  - 「おすすめ順」（既定・APIレスポンス順）
  - 「価格の安い順」
  - 「価格の高い順」
  - 「新着順」（`createdAt` 降順）

### 4-3. UI 変更
- `features/products/pages/ProductList.jsx`：上記レイアウト変更
- `features/products/components/ProductCard.jsx`（新規）：1商品分のカードを切り出し
- `features/products/components/SortSelect.jsx`（新規）：並び替え

### 4-4. DB 変更
- なし

### 4-5. API 変更
- なし（並び替えはクライアントサイド）

### 4-6. TDD テストケース
- `ProductCard.test.jsx`（新規）：
  - 商品名・価格・「カートに入れる」ボタンが描画される
  - `ON_SALE` 商品は「残りX点」を表示
  - `PRE_ORDER` 商品は「発売日：YYYY-MM-DD」を表示
  - クリックで詳細ページに遷移
- `SortSelect.test.jsx`（新規）：
  - 4つの並び替え選択肢が表示
  - 選択変更で `onChange` が呼ばれる
- `ProductList.test.jsx`（追記）：
  - 「価格の安い順」を選ぶと `minPrice` 昇順で並ぶ

---

# Step 5：カート機能（正式実装）

### 5-1. 背景・目的
現状の Market は「商品詳細 → 購入する → Checkout」の **単品購入のみ**で、複数商品を一度に買えない。Amazon 風 EC サイトとしては致命的な機能不足のため、本フェーズで **carts / cart_items テーブル**を Core に追加し、本格的なカート機能を実装する。

### 5-2. データモデル

#### `carts` テーブル（新規・Core）
| カラム | 型 | NULL | 既定値 | 備考 |
|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO | PK |
| `customer_id` | BIGINT | NO | - | FK → `market_customers.id` |
| `created_at` | TIMESTAMP | NO | NOW() | |
| `updated_at` | TIMESTAMP | NO | NOW() | 行更新時に書き換え |

UNIQUE (`customer_id`) — 1顧客1カート。

#### `cart_items` テーブル（新規・Core）
| カラム | 型 | NULL | 既定値 | 備考 |
|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO | PK |
| `cart_id` | BIGINT | NO | - | FK → `carts.id`（CASCADE DELETE）|
| `sku_id` | BIGINT | NO | - | FK → `skus.id` |
| `quantity` | INT | NO | 1 | 1以上 |
| `is_preorder` | BOOLEAN | NO | FALSE | 予約フラグ（追加時に Market 側で判定）|
| `added_at` | TIMESTAMP | NO | NOW() | |

UNIQUE (`cart_id`, `sku_id`, `is_preorder`) — 同一 SKU・同一フラグは1行・数量で集約。
INDEX (`cart_id`)。

### 5-3. API 変更（Core 新設）

> **実装時パス変更（2026-05-08）**: 当初設計の `/api/carts/me` は CSRF 検証範囲（`MarketCsrfFilter` の `PROTECTED_PREFIX = /api/customer/`）外となるため、他 Market API（`/api/customer/orders` 等）と整合させて **`/api/customer/carts`** 配下に統一した。

| メソッド | パス | 概要 |
|---|---|---|
| GET | `/api/customer/carts/me` | 自分のカート取得（未ログイン時 401）|
| POST | `/api/customer/carts/me/items` | カートに SKU 追加（既存なら数量加算）|
| PUT | `/api/customer/carts/me/items/{itemId}` | 数量変更 |
| DELETE | `/api/customer/carts/me/items/{itemId}` | カートから削除 |
| DELETE | `/api/customer/carts/me` | カート全削除（Checkout 完了時に呼ぶ）|

リクエスト/レスポンス DTO は `CartResponse` / `CartItemRequest` / `UpdateCartItemRequest` を新設。`CartResponse` は SKU 詳細・商品名・色サイズ・単価・在庫・小計・合計を返す。画像（`mainImage`）は商品ごとに別ファイル取得経路を持つため Cart レスポンスからは省略し、Market 側で `getMarketProduct(productId)` 結果を流用する方針とした。

### 5-4. API 変更（Console Pass-through）

> **実装時方針変更（2026-05-08）**: 当初 Console Pass-through を新設する想定だったが、調査の結果 **Market 顧客 API（`/api/customer/me` / `/api/customer/orders` 等）はすべて Core を直接呼び出している**（Vite `vite.config.js` で `/api/customer/*` を `core:8080` にプロキシ、CloudFront 本番では同等の Behavior 振り分け）。Cart API も同じ `/api/customer/carts` 配下のため、**Console Pass-through は新設しない**。Phase B はスキップ。
>
> 商品系 API（`/api/products/...`）は引き続き Console 経由（既存方針）だが、認証付き Market API（`/api/customer/...`）は Core 直接が一貫した方針。

### 5-5. UI 変更（Market）
- `features/cart/`（新規ディレクトリ）：
  - `api/cart.js`：`getMyCart` / `addToCart` / `updateQuantity` / `removeFromCart` / `clearCart`
  - `context/CartContext.jsx` ＋ `useCart.js`：カート状態（`items` / `totalCount` / `totalPrice`）の Provider
  - `pages/CartPage.jsx`：`/cart` ルート。商品一覧テーブル・数量変更・削除・「レジに進む」ボタン
- `App.jsx`：
  - `<CartProvider>` を `<AuthProvider>` の内側にラップ
  - `<Route path="/cart" element={<ProtectedRoute><CartPage /></ProtectedRoute>} />` 追加
- `components/AppHeader.jsx`：
  - カートアイコンに件数バッジ（`useCart().totalCount`）。0件時は数字非表示
  - クリックで `/cart` へ遷移
- `features/products/components/ProductCard.jsx`：
  - 「カートに入れる」ボタン押下で `addToCart(skuId, 1)` を呼ぶ
  - 一覧では SKU 選択がないため、**「カートに入れる」は SKU が1つしかない商品のみ活性化**。複数 SKU 商品は「選択して購入」ボタンで詳細ページへ
  - 在庫切れ・SOLD_OUT・NOT_STARTED 系はボタン非活性
- `features/products/pages/ProductDetail.jsx`：
  - 既存「購入する」ボタンの隣に「カートに入れる」ボタンを追加
  - 「購入する」は従来どおり Checkout 直行（カート経由しない・1商品即決）
  - 「カートに入れる」は `addToCart` を呼び、画面右上に Snackbar で「カートに追加しました」を表示
- `features/checkout/pages/Checkout.jsx`：
  - 既存の単品 Checkout（`?product_id=&sku_id=&quantity=`）は **そのまま残す**（後方互換）
  - 新規モード：`?from=cart` のとき `useCart().items` の全アイテムを Checkout 対象に。完了時に `clearCart()` を呼ぶ
- `features/cart/pages/CartPage.jsx`：
  - 表形式で各 SKU を表示（画像・商品名・色サイズ・単価・数量Select・小計・削除ボタン）
  - 右側カラムに合計金額・「レジに進む（X 点）」ボタン → `/checkout?from=cart` へ

### 5-6. DB 設計書 / API 設計書 / required_tables.txt の更新
CLAUDE.md の DB/API メンテルールに従い、本フェーズ内で以下を更新する：
- `docs/database_design/TBL_carts.md`（新規）
- `docs/database_design/TBL_cart_items.md`（新規）
- `docs/database_design/README.md`（一覧表に追記、Core システム側に分類）
- `docs/database_design/ER_diagram.md`（Mermaid 図に `carts` `cart_items` を追加・`market_customers` `skus` とのリレーション）
- `docs/api_design/Core_API.md`：`/api/customer/carts/me` 系5本を追記
- `docs/api_design/Market_API.md`：`/api/customer/carts/me` 系を追記
- ~~`docs/api_design/Console_API.md`：Pass-through 5本を追記~~ → §5-4 の方針変更により不要
- `ops/healthcheck/required_tables.txt`：`carts` `cart_items` を追記
- `amazia-core/src/main/resources/schema.sql`：`CREATE TABLE IF NOT EXISTS carts ... cart_items ...` を追記

### 5-7. TDD テストケース

#### Core
- `CartControllerTest`：
  - 未ログイン（JWT なし）で 401
  - 自分以外の `customer_id` で他人のカートにアクセスできない（仕様上 `/me` のみのため経路自体存在しない）
  - 同一 SKU を2回 POST すると数量が加算され、`cart_items` の行は1行のまま
  - `is_preorder = TRUE` と `is_preorder = FALSE` は別行（UNIQUE 制約に `is_preorder` 含む）
  - PUT で数量0以下を送ると 400
  - DELETE で削除した SKU は GET で消えている
- `CartServiceTest`：
  - 在庫を超える数量で POST すると `InsufficientStockException`
  - SOLD_OUT 商品は POST で `ProductNotPurchasableException`

#### Console
- §5-4 方針変更により Pass-through 廃止。テスト対象なし。

#### Market（Vue/React テスト）
- `useCart.test.jsx`：
  - 初期状態で空・`addToCart` 後にアイテムが追加される
  - 同一 SKU で追加すると数量が加算される
  - `removeFromCart` で削除される
- `CartPage.test.jsx`：
  - 空カート時に「カートは空です」表示
  - アイテム表示・数量変更・削除が動く
  - 「レジに進む」で `/checkout?from=cart` に遷移
- `Checkout.test.jsx`（既存に追記）：
  - `?from=cart` モードでカートのアイテムが全て表示される
  - 注文完了時に `clearCart()` が呼ばれる

---

# Step 6：商品詳細ページの3カラム化

### 6-1. 背景・目的
現状の `ProductDetail` は「画像（左 300px）＋ 情報（右）」の2カラムで、購入ボタンが情報カラムの一番下にある。Amazon の詳細ページは **左：画像ギャラリー／中央：商品スペック／右：購入ボックス**の3カラムで、購入ボックスが常に視野内にある。

### 6-2. 設計方針
- レイアウト（`md` 以上）：
  ```
  +-----------+---------------------+----------------+
  |           |                     | [購入ボックス] |
  | 画像      | タイトル・色サイズ   |   ¥1,500       |
  | ギャラリー| 価格・説明          | 数量 [1▼]      |
  |           |                     | [カートに入れる]|
  |           |                     | [今すぐ買う]    |
  +-----------+---------------------+----------------+
  ```
- ブレークポイント：`md` 以上で3カラム、`sm` 以下では従来どおり縦積み
- 購入ボックスは `Paper` で囲み枠線・薄影。`position: sticky; top: 16px` でスクロール追従
- 購入ボックス内の項目（Amazon 風）：
  - 価格（大きめ・赤系強調なし、青基調を保つ）
  - 「在庫あり」「残りX点」「在庫切れ（再入荷予約受付中）」「予約受付中」のステータス表示
  - 「お届け予定：YYYY-MM-DD」（発売日 or 今日 +3日 のスタブ表示・実機能はフェーズX-5 リードタイム連携時）
  - 数量セレクト（1〜10）
  - 「カートに入れる」ボタン（山吹色）
  - 「今すぐ買う」ボタン（オレンジ系・既存の「購入する」を改名）
- 中央カラムの色・サイズ選択は既存 `ToggleButtonGroup` を踏襲

### 6-3. UI 変更
- `features/products/pages/ProductDetail.jsx`：3カラムレイアウト化
- `features/products/components/PurchaseBox.jsx`（新規）：右カラムを切り出し
- `features/products/components/ImageGallery.jsx`（新規）：左カラムを切り出し
- 「お届け予定」のスタブ表示は `getEstimatedDeliveryDate(product)` ユーティリティ（`features/products/deliveryEstimate.js`）に分離。後続フェーズでリードタイム実装時に差し替え

### 6-4. DB 変更 / API 変更
- なし

### 6-5. TDD テストケース
- `ImageGallery.test.jsx`：サムネイルクリックでメイン画像が切替
- `PurchaseBox.test.jsx`：
  - 数量セレクトで1〜10が選べる
  - 「カートに入れる」で `addToCart(skuId, quantity)` が呼ばれる
  - 「今すぐ買う」で `/checkout?product_id=&sku_id=&quantity=` に遷移
  - 在庫切れ時はカート追加・購入ボタンが非活性
  - 予約商品は「今すぐ予約」表示・`&preorder=1` が付与される
- `ProductDetail.test.jsx`（既存改修）：
  - 3カラムレンダリングのスナップショット
  - レスポンシブ：`window.innerWidth = 600` で縦積みになる

---

# Step 7：レスポンシブ・E2E 確認

### 7-1. 背景・目的
Step 1〜6 でレイアウトが大きく変わるため、各ブレークポイント（`xs` / `sm` / `md` / `lg`）で崩れていないか・既存 E2E 系のシナリオが緑になるかを確認する。

### 7-2. 確認項目
- ブレークポイント別の手動確認シート（`docs/design/phase11_20/phase16_5_responsive_check.md` を新設し、各 Step 完了時にチェック）
  - xs（375px）：iPhone SE 想定
  - sm（600px）：タブレット縦
  - md（900px）：タブレット横・ノートPC小
  - lg（1280px）：デスクトップ
- E2E（既存 Playwright があれば）：
  - 商品一覧 → 詳細 → カート追加 → カートページ → Checkout → 完了 のゴールデンパス
  - 検索 → 結果0件 → 「すべての商品を見る」 → 一覧に戻る
- リグレッション：
  - 単品 Checkout（`?product_id=&sku_id=&quantity=`）が壊れていない
  - 予約購入（`&preorder=1`）が壊れていない
  - ログイン・会員登録・パスワードリセット・購入履歴が壊れていない

### 7-3. DB 変更 / API 変更
- なし

### 7-4. TDD テストケース
- 既存テスト群（`AppHeader.test.jsx` / `ProductList.test.jsx` / `ProductDetail.test.jsx` / `Checkout.test.jsx` 等）が緑であることを CI で担保
- 新規追加テストは Step 1〜6 で個別に網羅済み

---

## 申し送り（後続フェーズへ）
- **カテゴリ実機能化**：本フェーズではダミーのため、`categories` テーブル追加・商品マスタへの `category_id` カラム追加・Console カテゴリ管理画面・Market サブヘッダーリンクの実遷移を後続フェーズで実装する
- **サーバーサイド検索**：商品数 100 を超えた段階で、`GET /api/products/market?keyword=` のサーバーサイドフィルタに移行する
- **お届け予定の実装**：`PurchaseBox` のお届け予定はスタブ表示。phaseX-5（都道府県別リードタイム）実装後に `getEstimatedDeliveryDate()` を実機能に差し替える
- **レコメンド・レビュー機能**：本フェーズではスコープ外。フェーズ22以降で検討
- **購入履歴ページのリニューアル**：本フェーズではスコープ外（テーマ適用のみ。レイアウトは現行）

## まとめ：実装順とフェーズ完了の定義

### 推奨実装順
1. Step 1（テーマ集約）— 最初にやらないと後続全 Step が散らばる
2. Step 2（ヘッダー2段化）— Step 3〜5 のヘッダー要素の入れ物
3. Step 4（一覧カードグリッド）— ユーザーが最初に見る画面の印象を変える
4. Step 3（検索バー実機能）— Step 2/4 完了後に検索結果表示が動く
5. Step 5（カート機能）— DB / API 含む大物。中盤に置いて他に影響波及させる
6. Step 6（詳細3カラム）— Step 5 のカート追加 UI と組み合わせ
7. Step 7（レスポンシブ確認）— 全 Step 完了後の総点検

### フェーズ完了の定義
`docs/ai_context/test_insights.md` の「まとめ」セクション準拠に加え、本フェーズ固有として：
- [x] 全 Step の TDD テストケースが緑（Core 358 件 / Market 151 件）。Console は §5-4 方針変更により Pass-through 廃止のため対象外
- [x] `docs/database_design/` に `TBL_carts.md` `TBL_cart_items.md` を新設・README / ER 図反映
- [x] `docs/api_design/Core_API.md` にカート系 API 反映（`Console_API.md` は Pass-through 廃止のため不要・`Market_API.md` は §5-3 で言及済み）
- [x] `ops/healthcheck/required_tables.txt` に `carts` `cart_items` 追加
- [x] `amazia-core/src/main/resources/schema.sql` に `carts` / `cart_items` の `CREATE TABLE IF NOT EXISTS` 追加（冪等）
- [ ] レスポンシブ確認シート（`phase16_5_responsive_check.md`）の手動確認項目はユーザー側で実施（実機ブラウザ DevTools での目視）
