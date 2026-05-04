
# フェーズ9：商品マスタへの画像登録（複数画像対応・別テーブル版）

## ステータス
🔲 未着手

## 範囲
- Amazia Console（管理画面）
- Amazia Market（ECサイト）
- Amazia Core（API）
- DB設計
- S3ストレージ

---

# 1. 機能概要
- Amazia Console から **商品に複数の画像をアップロード** できるようにする
- 画像は **PNG固定・200KB以下・800px以内**
- 保存先は **S3（private）**
- Amazia Market では商品一覧・詳細画面で画像を表示
- 並び順（sort_order）によりメイン画像を制御
- 画像未登録の場合はプレースホルダー表示

---

# 2. 機能詳細

## 2.1 画像アップロード（Amazia Console）

### 2.1.1 アップロード仕様
- 対応形式：**PNG（image/png）**
- 最大ファイルサイズ：**200KB**
- 最大解像度：**800×800 px**
- 画像枚数：**複数可（上限10枚）**
- バリデーション：
  - MIMEチェック（image/png）
  - 拡張子チェック（.png）
  - 容量チェック（200KB以下）
  - 解像度チェック（800px超はリサイズ or エラー）
- 保存パス命名規則：  
  `products/{productId}/{uuid}.png`

### 2.1.2 リサイズ処理
- 800pxを超える場合はサーバー側で自動リサイズ  
- PNG形式で再保存  
- 画質劣化なし（PNGは非可逆圧縮）

### 2.1.3 並び順（sort_order）
- 1 がメイン画像  
- Console側でドラッグ＆ドロップによる並び替えを想定  
- sort_order は 1 から連番で保持

---

## 2.2 画像表示（Amazia Market）

### 2.2.1 商品一覧
- メイン画像（sort_order=1）をサムネイル表示  
- サムネイルサイズ：**300×300 px**  
- アスペクト比：**contain**  
- Lazy Load 対応

### 2.2.2 商品詳細
- 全画像を sort_order ASC で表示  
- 最大表示サイズ：800px以内

### 2.2.3 プレースホルダー
- `/assets/img/noimage.png`

---

# 3. DB設計

## 3.1 products テーブル（変更なし）
商品本体の情報のみ保持。

## 3.2 product_images テーブル（新規）

| カラム名 | 型 | 説明 |
|---------|----|------|
| id | BIGINT PK | 画像ID |
| product_id | BIGINT FK | 紐づく商品ID |
| image_path | VARCHAR(300) | S3のパス（例：products/123/uuid.png） |
| sort_order | INT | 表示順（1がメイン） |
| created_at | DATETIME | 登録日時 |
| updated_at | DATETIME | 更新日時 |

### インデックス
- `idx_product_images_product_id_sort`  
  → 商品ページの画像取得が高速

---

# 4. S3設計

## 4.1 バケット構成
- バケット名：`amazia-product-images`
- パス：`products/{productId}/{uuid}.png`

## 4.2 セキュリティ
- バケットは **private**
- 署名付きURLでアップロード（有効期限5分）
- IAMロールは最小権限：
  - `s3:PutObject`
  - `s3:GetObject`

## 4.3 CORS設定
- POST / PUT を許可  
- Origin は Console のみ

---

# 5. API仕様（Amazia Core）

## 5.1 GET /products/{id}/images
- 指定商品の画像一覧を sort_order ASC で返す

## 5.2 POST /products/{id}/images
- PNG画像アップロード  
- バリデーション  
- S3保存  
- product_images に1行追加  
- sort_order は最大値+1

## 5.3 PUT /product_images/{id}/sort
- 並び順変更  
- sort_order を更新

## 5.4 DELETE /product_images/{id}
- S3削除  
- DB削除

---

# 6. 非機能要件

## 6.1 パフォーマンス
- 200KB以下のため高速表示  
- Lazy Load で初期ロード軽量化

## 6.2 ストレージ
- S3無料枠（5GB/月）で十分  
  → 200KB × 20,000枚 ≒ 4GB

## 6.3 運用
- 商品削除時に画像も削除（外部キー or バッチ）  
- 不要画像のクリーンアップバッチ（任意）

---

# 7. TDDテストケース

## 7.1 Amazia Core（JUnit）
- PNG画像が正常に登録できる  
- 200KB超 → エラー  
- MIMEが image/png 以外 → エラー  
- 解像度800px超 → リサイズされる  
- 複数画像が登録できる  
- sort_order が正しく付与される  
- 並び順変更が正しく反映される  
- 画像削除時にS3とDBが両方削除される  
- 商品削除時に画像も削除される  

## 7.2 Amazia Console（PHPUnit）
- 複数画像アップロードUIのテスト  
- 並び順変更のテスト  
- PNG以外のアップロードでエラー  
- 200KB超でエラー  
- 画像削除のテスト  

---

# 8. 今後の拡張
- 動画対応（mp4） ※AWSの無料ではやめておこう。
- WebP対応（軽量化）
- カラー別画像（SKU単位）
- CloudFront導入
