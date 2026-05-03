## 問題
Excelファイルをアップロードして「一括登録を実行」ボタンを押すと、「アップロードに失敗しました」と表示される。

## ログ
```
POST http://13.54.203.95:8001/api/products/import
Status Code: 422 Unprocessable Content
content-length: 150
content-type: multipart/form-data; boundary=----WebKitFormBoundary...
```

リクエストの `content-length` が 150 バイトと極端に小さく、Excelファイルの実体が送信されていなかった。

## 原因1：Ant Design fileList のオブジェクト構造の誤解

`a-upload-dragger` コンポーネントは `fileList` にファイルを格納する際、生の `File` オブジェクトをそのままではなく、以下の構造でラップする。

```js
{
  uid: '-1',
  name: 'sample.xlsx',
  status: 'done',
  originFileObj: File { ... }  // ← 実際のFileオブジェクトはここ
}
```

修正前のコードは `fileList.value[0]` をそのまま `FormData.append('file', ...)` に渡していたため、`File` オブジェクトではなくラッパーオブジェクトが文字列化されて送信された。これがcontent-length: 150バイトの正体。

```js
// 修正前（誤）
formData.append('file', fileList.value[0]);

// 修正後（正）
const file = fileList.value[0].originFileObj ?? fileList.value[0];
formData.append('file', file);
```

## 原因2：mimes バリデーションのDockerコンテナ内での不安定動作

Laravelの `mimes:xlsx,xls` バリデーションはPHPの `finfo` 拡張によるMIMEタイプ検出に依存する。
Alpine LinuxベースのDockerコンテナ環境では、`libmagic` のデータベースが不完全なため、
正しいxlsxファイルでも異なるMIMEタイプ（`application/zip` 等）として検出され弾かれることがある。

```php
// 修正前（不安定）
'file' => 'required|file|mimes:xlsx,xls',

// 修正後（明示的なMIMEタイプ指定）
'file' => 'required|file|mimetypes:application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel,application/octet-stream',
```

## 原因3：ルート定義の順序

`/products/import` が `/products/{id}` より後ろに定義されていたため、Laravelが `import` を `{id}` の値として解釈するリスクがあった（実際のエラーには影響しなかった可能性があるが、設計上の問題として修正）。

```php
// 修正前（リスクあり）
Route::get('/products/{id}', ...);
Route::post('/products/import', ...);  // "import" が {id} に拾われる可能性

// 修正後（静的ルートを先に定義）
Route::post('/products/import', ...);  // 先に定義
Route::get('/products/{id}', ...);
```

## 解決策
上記3点を同時に修正し、コミット `d0de16b2` としてデプロイ。

## 結果
- Excelファイルが正しくサーバーに届くようになった
- バリデーションが通りPhpSpreadsheetによるパース処理が実行される

## 補足
- Ant Designのアップロード系コンポーネント（`a-upload`, `a-upload-dragger`）を使う際は、必ず `.originFileObj` から実際のFileオブジェクトを取り出すこと
- Laravelの `mimes` バリデーションはコンテナ環境で壊れやすい。本番用Dockerfileに `file` コマンド（libmagic）が正しく入っているか確認するか、`mimetypes` で明示指定するのが安全
