# Amazia

## プロジェクト概要（Amazia）

Amazia は、**商品管理・在庫管理・EC フロントを一体化した学習用 EC システム**です。
React / Vue / Java / PHP といった複数の技術スタックを組み合わせ、
**実践的なアーキテクチャ設計・CI/CD・AWS 運用を体系的に学ぶこと**を目的としています。

一般的な EC システムと同様に、会員向けサイト（Market）、管理画面（Console）、
在庫・バッチ処理を担うコアシステム（Core）の 3 つで構成されます。

それぞれが独立した技術で実装されているため、フロントエンド・バックエンド・インフラを横断して学習できるのが特徴です。

なお、技術選定に関しては「色々触ってみたい」という開発者の純粋な欲求が多少にじみ出ていますが、
構成自体は実務でも通用する堅実なものを意識しています。

Amazia は、**学習・検証・改善を繰り返しながら成長していくプロジェクト**として設計されています。

---

## アーキテクチャ図

![アーキテクチャ図](docs/architecture.svg)

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

## ドキュメントマップ

```
docs/
├── setup.md                  # 環境構築手順（ローカル開発環境）
├── coding_guidelines.md      # コーディング規約（全システム共通）
├── architecture.svg          # システムアーキテクチャ図（全体構成）
├── cicd_pipeline.svg         # CI/CDパイプライン アーキテクチャ図
├── analysis/                 # 分析レポート
│   ├── README.md             # 分析一覧インデックス
│   └── 20260503_trouble_analysis.md
├── api_design/               # API定義
│   ├── Console_API.md        # Console API定義
│   ├── Market_API.md         # Market API定義
│   └── Core_API.md           # Core API定義
├── database_design/          # DB設計
│   ├── README.md             # テーブル一覧インデックス
│   ├── ER_diagram.md         # ER図（Console / Core）
│   ├── TBL_users.md
│   ├── TBL_password_reset_tokens.md
│   ├── TBL_sessions.md
│   ├── TBL_personal_access_tokens.md
│   ├── TBL_products.md
│   ├── TBL_product_images.md
│   ├── TBL_product_skus.md
│   ├── TBL_product_sku_prices.md
│   ├── TBL_product_sku_price_history.md
│   ├── TBL_product_sku_stocks.md
│   ├── TBL_product_sku_stock_transactions.md
│   └── TBL_product_sku_images.md
├── design/                   # 設計・実装計画
│   ├── implementation_plan.md    # フェーズ別実装計画（全体）
│   ├── phase6_10/            # フェーズ6から10のドキュメント ※フェーズ1～5は仮実装のため割愛
│   ├── phase11_20/           # フェーズ11から20のドキュメント
│   └── phaseX/               # フェーズX
└── troubles/                 # 不具合記録
    ├── README.md             # 不具合一覧・再発防止アクション
    └── 001〜009_*.md         # 個別不具合ドキュメント
```

> 画面遷移図、リポジトリ構成を作成予定

---

## セットアップ手順

環境構築の詳細手順は [docs/setup.md](docs/setup.md) を参照。

### クイックスタート

```bash
# 起動
docker compose -f docker-compose.local.yml up --build

# 停止
docker compose -f docker-compose.local.yml down
```

| サービス | URL |
|---|---|
| Amazia Market（React） | http://localhost:5173 |
| Amazia Console UI（Vue） | http://localhost:5174 |
| Amazia Console API（Laravel） | http://localhost:8000 |
| Amazia Core API（Spring Boot） | http://localhost:8080 |

---

## 環境変数について
** 工事中 ** 

---

## DB設計

テーブル定義書・ER図は [docs/database_design/README.md](docs/database_design/README.md) を参照。

---

## API定義

システム別のAPI定義は [docs/api_design/](docs/api_design/) を参照。

| システム | ファイル |
|----------|---------|
| Console | [docs/api_design/Console_API.md](docs/api_design/Console_API.md) |
| Market | [docs/api_design/Market_API.md](docs/api_design/Market_API.md) |
| Core | [docs/api_design/Core_API.md](docs/api_design/Core_API.md) |

---

## UIテンプレート方針

| システム | フレームワーク | UIライブラリ | 理由 |
|---|---|---|---|
| Amazia Market | React | Material UI (MUI) | ECサイト向けコンポーネントが豊富 |
| Amazia Console | Vue.js | Ant Design Vue | 管理画面・フォーム系に強い。ReactとVue両方を学ぶ狙いもある |

> UIはフェーズ4以降に整える。フェーズ1〜3はテンプレートを当てはめるだけにして、バックエンドのTDDに集中する。

---

## CI/CD パイプライン

```mermaid
flowchart TD
    DEV([開発者]) -->|git push| GH[GitHub\nmain branch]

    GH --> TC[test-core\nmvn clean test\nJUnit]
    GH --> TP[test-console\nphp artisan test\nPHPUnit]
    GH --> TM[test-market\nnpm run build\nビルド確認]

    TC --> DJ
    TP --> DJ
    TM --> DJ

    subgraph DJ[deploy ジョブ — 全グリーン後]
        direction TB
        S1[① docker build amazia-core\n   docker build amazia-console\n   → ECR push]
        S2[② amazia-market npm build\n   console Vue npm build\n   → dist 生成]
        S3[③ zip -r amazia.zip\n   → S3 upload]
        S4["④ SSM send-command ①（ポーリング待機）\nECR login → docker pull\n→ S3 unzip → docker-compose up -d"]
        S5["⑤ SSM send-command ②（ポーリング待機）\nnginx.conf コピー → dist コピー\n→ nginx reload"]
        S1 --> S4
        S2 --> S4
        S3 --> S4
        S4 --> S5
    end

    S1 -->|docker push| ECR[(Amazon ECR\namazia-core:latest\namazia-console:latest)]
    S3 -->|aws s3 cp| S3B[(Amazon S3\namazia.zip)]

    subgraph EC2[AWS EC2  13.54.203.95 Elastic IP]
        direction TB
        NGX[Nginx\n:80 → amazia-market\n:8001 → amazia-console UI]
        subgraph DC[Docker Compose]
            CORE[amazia-core\nSpring Boot :8080]
            CON[amazia-console\nLaravel :8000]
            DB[(MySQL :3306)]
            CORE --> DB
            CON --> DB
            CON --> CORE
        end
        NGX --> DC
    end

    ECR -->|docker pull| DC
    S3B -->|unzip| EC2
    S5 -->|SSM| EC2

    EC2 --> BM([Amazia Market\nhttp://13.54.203.95])
    EC2 --> BC([Amazia Console UI\nhttp://13.54.203.95:8001])
    EC2 --> BA([Amazia Core API\nhttp://13.54.203.95:8080])
```
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
| Phase 7 | 一括削除・一括編集 | ✅ 完了 |
| Phase 8 | 商品マスタ機能 | ✅ 完了 |
| Phase 9 | 商品マスタへの画像登録 | ✅ 完了 |
| Phase 10 | 在庫管理・価格管理・商品一覧改修 | ✅ 完了 |
| Phase 11 | Amazia Console ログイン画面 | 🔲 未着手 |
| Phase 12 | ワークフロー機能（承認フロー）| 🔲 未着手 |
| Phase 13 | Amazia Market ログイン・会員登録機能 | 🔲 未着手 |
| Phase 14 | 購入機能 | 🔲 未着手 |
| Phase 15 | 配送管理 | 🔲 未着手 |
| Phase 16 | UIデザイン改善 | 🔲 未着手 |
| Phase 17 | バッチ処理 | 🔲 未着手 |
| Phase 18 | 問い合わせ機能 | 🔲 未着手 |
| Phase 19 | お知らせ機能 | 🔲 未着手 |
| Phase 20 | ドキュメント整理 | 🔲 未着手 |
| Phase X-1 | デプロイパイプライン高速化 | 🔲 未着手（随時） |
| Phase X-2 | デプロイパイプライン再設計 | ✅ 完了 |

---

## コーディング規約

全システム共通の設計方針・コーディング規約は [docs/coding_guidelines.md](docs/coding_guidelines.md) を参照。

フォルダ構成・責務分離・config 駆動設計・テスト規約について定める。

---

## 改善と分析

不具合対応および分析結果をまとめたセクションです。  

不具合に関する情報は[docs/troubles/README.md](docs/troubles/README.md) を参照。

分析内容は[docs/analysis/README.md](docs/analysis/README.md) を参照。

---

## トラブルシュート
** 工事中 ** 
