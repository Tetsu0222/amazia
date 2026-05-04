# フェーズ6：Excel一括登録機能

## 背景・動機

管理画面（amazia-console）のフェーズ5でリリース直後、運用の現実的な課題に直面する。

商品を1件ずつ手入力するフォームは個別の追加・編集には適しているが、リリース当日に初期商品データを投入する場面では現実的でない。実際の業務では商品台帳はExcelで管理されていることが多く、数十〜数百件を手入力していては担当者の工数が膨大になる。

そこで「既存のExcel台帳をそのままアップロードして一括登録できる機能」を、管理画面リリースと同時期に実装する。これは**管理側先行リリース → 段階的な機能追加 → 顧客側本リリース**というリアルなスケジュール設計の一部でもある。

---

## 実装方針

```
Vue（ドラッグ&ドロップUI） → Laravel（Excelパース + amazia-coreへ順次POST） → Spring Boot → MySQL
```

**amazia-core（Spring Boot）は変更しない。**  
既存の `POST /api/products` エンドポイントをLaravel側から繰り返し呼ぶ設計にすることで、バックエンドの変更を最小限に抑える。

---

## Excelフォーマット

1行目をヘッダー行として読み込む。

| name | description | price | stock |
|------|-------------|-------|-------|
| りんご | 青森産ふじりんご | 300 | 100 |
| みかん | 和歌山産みかん | 150 | 200 |

- `name` / `price` / `stock` は必須。欠けている行はスキップされ失敗一覧に表示される
- `description` は省略可
- サンプルファイル: `docs/sample_import.xlsx`

---

## 変更ファイル

### Laravel（amazia-console）

| ファイル | 変更内容 |
|---------|---------|
| `composer.json` | `maatwebsite/excel ^3.1` を追加 |
| `app/Http/Controllers/ImportController.php` | 新規作成。ファイル受信 → Excelパース → amazia-coreへPOST |
| `routes/api.php` | `POST /products/import` を追加 |

### Vue（amazia-console/resources/vue）

| ファイル | 変更内容 |
|---------|---------|
| `src/pages/ProductImport.vue` | 新規作成。ドラッグ&ドロップUI + 登録結果表示 |
| `src/pages/ProductList.vue` | 「一括登録（Excel）」ボタンを追加 |
| `src/router/index.js` | `/products/import` ルートを追加 |

---

## 処理の流れ

1. ユーザーが `/products/import` にアクセス
2. Excelファイルをドラッグ&ドロップまたはクリックで選択
3. 「一括登録を実行」ボタンを押す
4. Vue → `POST /api/products/import` (multipart/form-data) → Laravel
5. LaravelがExcelをパースし、1行ずつ `POST /api/products` → amazia-core
6. 全行処理後、成功件数と失敗一覧をJSONで返す
7. Vue上に「成功: N件」「失敗: N件（理由付き）」を表示

---

## ライブラリ選定理由

| 候補 | 選定/却下 | 理由 |
|------|---------|------|
| `maatwebsite/excel` | **選定** | Laravelとの親和性が高く、ヘッダー行を自動でキーにマッピングしてくれる |
| CSV手動パース | 却下 | 業務では.xlsx形式が標準。文字コードやカンマエスケープの問題もある |
| amazia-coreでExcel処理 | 却下 | Spring BootにApache POIを追加すると構成が複雑になる。Laravelがプロキシ層なので適切な処理場所 |

---

## デプロイ時の注意

`maatwebsite/excel` は内部でPHPのZipArchive拡張を使う。  
DockerfileにPHP拡張 `zip` が含まれていることを確認すること。

```dockerfile
RUN docker-php-ext-install zip
```
