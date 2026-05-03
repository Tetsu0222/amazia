# コーディング規約

大規模開発および AI 駆動開発を前提とした、変更に強く文脈が閉じた設計を標準とする。

---

## 1. 共通原則

### 1-1. Controller にビジネスロジックを書かない

Controller の責務は「入力受け取り → Service 呼び出し → 出力」のみ。

| 層 | 責務 |
|---|---|
| Controller | リクエスト受付・レスポンス整形 |
| Service | ビジネスロジック |
| Model / Entity | データ構造・DB アクセス |

**PHP（Laravel）：** ビジネスロジックは Service に寄せる。Fat Model を避ける。  
**Java（Spring）：** ビジネスロジックは Service に寄せる。Spring 慣習（by-layer）と整合させる。

### 1-2. バリデーションは config 駆動で共通化

独自定義したバリデーションルールを config に記述し、フレームワークのバリデーション機能から参照する。フレームワーク標準機能（Laravel FormRequest / Spring @Valid）を置き換えるのではなく、補完する形で使う。

**PHP（Laravel）：** config にルール定義 → FormRequest が読み込む  
**Java（Spring）：** application.yml にルール定義 → @Validated / Validator が参照する

---

## 2. フォルダ構成

### 2-1. ユースケース単位でフォルダを切る

```
上層: ドメイン名（例: Product / User）
中層: Controller / Service / Model / Trait（言語慣習に合わせる）
下層: 実ファイル
```

これ以上ネストしない。階層が深いほど AI の文脈理解コストが上がる。

**PHP の例：**
```
/app
├── Product
│   ├── Controller
│   │   └── /実ファイル群
│   ├── Service
│   │   └── /実ファイル群
│   └── ...etc
├── Shared (補足1)
│   ├── Exception
│   │   └── /実ファイル群
│   ├── Util
│   │   └── /実ファイル群
│   └── ...etc
├── Model (補足2)
│   └── Product
│       └── /実ファイル群
└── ...etc

/config
├── app
│   └── {ドメイン名}.php (/実ファイル群)
├── app.php (補足3)
└── ...実ファイル群

/resources
└── vue
    └── features
        ├── Product
        │   ├── api
        │   │   └── /実ファイル群(js)
        │   └── pages
        │       └── /実ファイル群(vue)
        └── ...etc

/routes
├── api
│   └── {ドメイン名}.php (/実ファイル群)
├── api.php (補足4)
├── web.php
└── console.php

/storage
└── Product
    ├── images
    │   └── {id} (補足5)
    │       └── /実ファイル群
    ├── excles
    │   └── /実ファイル群
    └── ...etc

```

**補足**
- 1.Shared に入れる条件は 2-3 を参照。
- 2.Laravel の慣習に合わせて Model は /app/Models に置く。
    - 中層でドメイン単位にまとめることでユースケースとの関連性を保つ。
    - coreでDB接続を行うためモデルを必要とするケースは少ないと想定
- 3.app.phpでドメインごとの config を読み込む。
    - return ['product' => require __DIR__.'/app/Product.php',];のように明示的な記載を必須とし、自動的に読み込みを行わない。
        - 自動読み込みだと「どこで読み込まれているか」を推測する必要があり、文脈理解コストが上がる。
        - 人間にとっても“全体像”が一目で分かる。
- 4.api.phpでドメインごとの routes を読み込む。
    - require __DIR__.'/api/Product.php'のように明示的な記載を必須とし、自動的に読み込みを行わない。
- 5.商品画像などのアップロードファイルは、/storage/Product/images/{id}/ のように、DB の ID をフォルダ名として保存する。
    - 1レコード＝1フォルダの構造にすることで、ファイル管理がシンプルになる。


**Java（Spring）の例：**
```
/main
└── /java
    └── /com.example.project
        ├── product
        │   ├── controller
        │   │   └── /実ファイル群
        │   ├── service
        │   │   └── /実ファイル群
        │   ├── entity
        │   │   └── /実ファイル群
        │   ├── repository
        │   │   └── /実ファイル群
        │   └── validator
        │       └── /実ファイル群
        │
        ├── inventory
        │   ├── controller
        │   │   └── /実ファイル群
        │   └── service
        │       └── /実ファイル群
        │
        ├── shared (補足1)
        │   ├── exception
        │   │   └── /実ファイル群
        │   ├── util
        │   │   └── /実ファイル群
        │   ├── entity (補足2)
        │   │   └── /実ファイル群
        │   └── repository (補足2)
        │       └── /実ファイル群
        │
        └── ...etc
    /Main.java
    /WebConfig.java
```

**補足**
- 1.Shared に入れる条件は 2-3 を参照。
  - 特定ドメイン固有の Entity・Repository・Validator はそのドメイン配下に置く。
- 2.Entity / Repository の配置基準：
  - 複数ドメインで利用される横断的な Entity / Repository → shared 配下に置く

### 2-2. ユースケースの粒度基準

第1層は**ドメイン名（名詞）**、第3層の実ファイルが**ユースケース単位（動詞+名詞）**となる。

**PHP の実例：**
```
/Product
    /Controller
        ListProductController.php
        CreateProductController.php
        UpdateProductController.php
        DeleteProductController.php
    /Service
        ListProductService.php
        CreateProductService.php
        UpdateProductService.php
        DeleteProductService.php
```

**Java の実例：**
```
/product
    /controller
        ListProductController.java
        GetProductController.java
        CreateProductController.java
        UpdateProductController.java
        DeleteProductController.java
        BulkDeleteProductController.java
        BulkUpdateStockController.java
        GetProductStatusController.java
    /service
        ListProductService.java
        GetProductService.java
        CreateProductService.java
        UpdateProductService.java
        DeleteProductService.java
        BulkDeleteProductService.java
        BulkUpdateStockService.java
        GetProductStatusService.java
```

**ルール**
- 1ファイル = 1ユースケース
- ファイル名にドメイン名を含める（冗長でもOK）
    - AI が「Product のサービス」を検索しやすくなる。
    - フォルダ名にドメイン名があるので、人間も迷わない。


### 2-3. Shared の扱い

複数のユースケースで使う共通処理は `Shared/` にまとめる。

**Shared に入れる条件：**
- 2つ以上のユースケースで使う
- ドメインに依存しない

**Shared に入れてはいけないもの：**
- ドメイン固有のロジック
- ユースケース固有のルール
- 特定の集約に依存する処理

**依存方向：** `Shared` → 各ユースケース（逆は禁止）

---

## 3. config 駆動設計

### 3-1. config 化すべきもの

- 外部 API のエンドポイント
- バリデーションルール（独自定義分）
- 権限・ロール定義
- 機能フラグ（ON/OFF）
- 定数・閾値
- メールテンプレート

### 3-2. 条件付きで config 化してよいもの

- 分岐条件（if の判定値）
- 画面表示の切り替え
- ワークフローのステップ定義

### 3-3. config 化すべきでないもの

- ビジネスロジック
- 複雑な計算
- ドメインルール
- モデルの振る舞い

---

## 4. テスト規約

### 4-1. 環境変数はテストにも明示する

新規環境変数を追加したら `phpunit.xml`（PHP）/ `application-test.properties`（Java）に必ずテスト用の値を追記する。テスト内で URL や設定値をハードコードせず、`config()` / `@Value` 経由で取得する。

### 4-2. 異常系テストを書く

正常系だけでなく以下を必ずカバーする。

- 外部 API が 500 / 404 を返す場合
- 必須項目が欠けている場合
- 不正な値が入力された場合

### 4-3. 新規環境変数追加時のチェックリスト

- [ ] `docker-compose.yml` の該当サービスに追記
- [ ] `phpunit.xml` または `application-test.properties` にテスト用値を追記
- [ ] テストコードが `config()` / `@Value` 経由で値を参照していることを確認

---

## 5. 言語・フレームワーク別の補足

### PHP（Laravel）

- `Model` にロジックを寄せすぎると Fat Model になる → Service に寄せる
- バリデーションは `FormRequest` を使い Controller をシンプルに保つ
- 外部 API エンドポイントは `config/services.php` で一元管理する

### Java（Spring）

- Spring は by-layer 構成が慣習 → ユースケース単位で切る場合はパッケージ戦略を最初に決める
- バリデーションは `@Valid` / `@Validated` を使い Controller に直接書かない
- 外部設定は `application.yml` または `@ConfigurationProperties` で一元管理する

### React / Vue（フロントエンド）

- API エンドポイントは `src/api/` 配下に集約し、コンポーネントから直接 fetch しない
- コンポーネントにビジネスロジックを書かない（表示ロジックのみ）
