# DB設計書

## 概要

テーブル単位のテーブル定義書とER図を格納する。

## ファイル一覧

| ファイル名 | テーブル名 | 論理名 | 所属システム |
|------------|------------|--------|------------|
| [TBL_users.md](TBL_users.md) | users | ユーザー | Console |
| [TBL_password_reset_tokens.md](TBL_password_reset_tokens.md) | password_reset_tokens | パスワードリセットトークン | Console |
| [TBL_sessions.md](TBL_sessions.md) | sessions | セッション | Console |
| [TBL_personal_access_tokens.md](TBL_personal_access_tokens.md) | personal_access_tokens | パーソナルアクセストークン | Console |

## ER図

- [ER_diagram.md](ER_diagram.md) — Mermaid記法によるER図（Consoleシステム）

## 追加ルール

- テーブル定義書はテーブル追加のたびに作成し、このREADMEに追記する。
- ファイル名は `TBL_<テーブル名>.md` 形式とする。
- マイグレーションファイルとの対応を各定義書に明記する。
