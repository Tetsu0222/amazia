# 実装計画

## フェーズ一覧

| フェーズ | タイトル | ステータス | 詳細 |
|---------|---------|-----------|------|
| Phase 1 | 会員画面の実装 | ✅ 完了 | [↓ 詳細](#フェーズ1会員画面の実装) |
| Phase 2 | 管理画面から商品登録 | ✅ 完了 | [↓ 詳細](#フェーズ2管理画面から商品登録) |
| Phase 3 | Amaziaの骨格実装（フェーズ1＋2の統合） | ✅ 完了 | [↓ 詳細](#フェーズ3amaziaの骨格実装フェーズ12の統合) |
| Phase 4 | 商品情報CRUD（Amaziaの基本機能） | ✅ 完了 | [↓ 詳細](#フェーズ4商品情報crudamaziaの基本機能) |
| Phase 5 | フロントエンドのEC2公開（Nginx） | ✅ 完了 | [↓ 詳細](#フェーズ5フロントエンドのec2公開nginx) |
| Phase 6 | エクセルアップロードによる一括登録 | ✅ 完了（動作確認済 2026-05-03） | [phase6/](phase6/phase6_excel_import.md) |
| Phase 7 | 一括削除・一括編集 | 🔲 未着手 | [phase7/](phase7/) |
| Phase 8 | 商品マスタ機能 | 🔲 未着手 | [phase8/](phase8/) |
| Phase 9 | 商品マスタへの画像登録 | 🔲 未着手 | [phase9/](phase9/) |
| Phase X-1 | デプロイパイプライン高速化 | 🔲 未着手（随時） | [phaseX/](phaseX/phaseX-1_deploy_optimization.md) |
| Phase X-2 | デプロイパイプライン再設計 | ✅ 完了（2026-05-03） | [phaseX/](phaseX/phaseX-2_deploy_pipeline_redesign.md) |

---

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

---

## フェーズ6：エクセルアップロードによる一括登録

→ 詳細は [phase6/phase6_excel_import.md](phase6/phase6_excel_import.md) を参照。

---

## フェーズ7：一括削除・一括編集

→ 詳細は [phase7/](phase7/) を参照。

---

## フェーズ8：商品マスタ機能

→ 詳細は [phase8/](phase8/) を参照。

---

## フェーズ9：商品マスタへの画像登録

→ 詳細は [phase9/](phase9/) を参照。
