# 環境構築手順

## 前提条件

以下のツールをインストールしておくこと。

| ツール | 推奨バージョン |
|--------|--------------|
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) | 最新版 |

> Windows の場合、Docker Desktop のバックエンドとして WSL2 が必要です。  
> インストール時に「Use WSL2 instead of Hyper-V」を選択してください。

---

## リポジトリのクローン

```bash
git clone <リポジトリURL>
cd Amazia
```

---

## 環境変数

ローカル環境では `docker-compose.local.yml` に環境変数が直接記載されているため、`.env` ファイルの作成は不要です。

各サービスに設定されている主な環境変数は以下のとおりです。

| サービス | 変数名 | 値 |
|---------|--------|-----|
| amazia-core | `SPRING_DATASOURCE_URL` | `jdbc:mysql://mysql:3306/amazia` |
| amazia-core | `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:5174` |
| amazia-console | `APP_KEY` | `base64:EKpjSK...`（デフォルト値あり） |
| amazia-console | `DB_HOST` / `DB_DATABASE` | `mysql` / `amazia` |
| amazia-market | `VITE_API_BASE_URL` | `http://localhost:8080/api` |
| amazia-console-vue | `VITE_API_BASE` | `http://amazia-console:8000` |

---

## 起動

```bash
docker compose -f docker-compose.local.yml up --build
```

初回は `amazia-core` のビルド（Maven の依存解決）に数分かかります。2回目以降はキャッシュが効くため高速です。

---

## アクセス先

| サービス | URL |
|---------|-----|
| Amazia Market（React） | http://localhost:5173 |
| Amazia Console UI（Vue） | http://localhost:5174 |
| Amazia Console API（Laravel） | http://localhost:8000 |
| Amazia Core API（Spring Boot） | http://localhost:8080 |

---

## 停止

```bash
docker compose -f docker-compose.local.yml down
```

データを残したまま停止する場合は上記で問題ありません。  
ボリューム（DBデータ）ごと削除する場合は以下を使用してください。

```bash
docker compose -f docker-compose.local.yml down -v
```
