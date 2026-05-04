# API定義書：Market

## 概要

| 項目 | 内容 |
|------|------|
| システム | Market（amazia-market） |
| 説明 | ユーザー向けECフロントエンド。現フェーズではルートファイル未作成。フェーズ13（Market認証）以降で実装予定。 |
| ステータス | 未実装（フェーズ13〜で追加予定） |

---

## 予定エンドポイント（フェーズ13以降）

| メソッド | パス | 説明 |
|----------|------|------|
| POST | `/api/auth/login` | Marketユーザーログイン |
| POST | `/api/auth/logout` | Marketユーザーログアウト |
| POST | `/api/auth/register` | Marketユーザー新規登録 |
| GET | `/api/products` | 商品一覧取得（公開） |
| GET | `/api/products/{id}` | 商品詳細取得（公開） |
| GET | `/api/orders` | 注文履歴取得 |
| POST | `/api/orders` | 注文作成 |

> 実装確定後、本ファイルを更新する。
