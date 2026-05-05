# フェーズ1〜5 実装詳細

## フェーズ1：会員画面の実装

### 範囲
- Amazia Market

### 機能概要
1. 会員がアクセス - Amazia Market
2. Auth - Amazia Market
3. TOP画面表示 - Amazia Market
4. 商品情報検索 - Amazia Market
5. 商品情報取得 - Amazia Core
6. 商品情報表示 - Amazia Market

### TDDテストケース（JUnit / Amazia Core）
- 商品一覧が取得できること（GET /api/products → 200 + JSONリスト）
- 存在しない商品IDを指定したとき404が返ること

---

## フェーズ2：管理画面から商品登録

### 範囲
- Amazia Console
- Amazia Core

### 機能概要
1. 社員がアクセス - Amazia Console
2. Auth - Amazia Console
3. TOP画面表示 - Amazia Console
4. 商品情報登録 - Amazia Console
5. 商品情報のDB登録 - Amazia Core
6. 商品情報登録結果 - Amazia Market

### TDDテストケース（JUnit / Amazia Core）
- 商品が登録できること（POST /api/products → 201 + 登録データ）
- 必須項目が欠けているとき400が返ること

---

## フェーズ3：Amaziaの骨格実装（フェーズ1＋2の統合）

### 範囲
- 全システム

### 機能概要
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

### TDDテストケース（JUnit / Amazia Core）
- Consoleで登録した商品がMarketの一覧に表示されること（統合確認）

### TDDテストケース（PHPUnit / Amazia Console）
- 商品登録フォームを送信するとCoreのAPIにリクエストが飛ぶこと

---

## フェーズ4：商品情報CRUD（Amaziaの基本機能）

### 範囲
- 全システム

### 機能概要
- 商品情報一覧画面
- 商品情報登録画面（フェーズ2の強化）
- 商品情報編集画面
- 商品情報削除画面

### TDDテストケース（JUnit / Amazia Core）
- 商品が更新できること（PUT /api/products/{id} → 200 + 更新後データ）
- 商品が削除できること（DELETE /api/products/{id} → 204）
- 削除済み商品を取得しようとすると404が返ること

### TDDテストケース（PHPUnit / Amazia Console）
- 編集フォームに既存データが初期表示されること
- 削除操作後に一覧画面にリダイレクトされること

---

## フェーズ5：フロントエンドのEC2公開（Nginx）

### 範囲
- Amazia Market
- Amazia Console UI
- AWS EC2（Nginx）

### 機能概要
1. EC2 に Nginx をインストール・設定
2. amazia-market を `npm run build` → EC2 に配置 → `http://<EC2-IP>` で配信
3. Console UI（Vue）を `npm run build` → EC2 に配置 → `http://<EC2-IP>:8001` で配信
4. GitHub Actions のデプロイ時に自動ビルド＆配置

### 完了条件
- `http://<EC2-IP>` → amazia-market（会員向け）
- `http://<EC2-IP>:8000` → amazia-console API
- `http://<EC2-IP>:8001` → Console UI（管理者向け）
- `http://<EC2-IP>:8080` → amazia-core API
