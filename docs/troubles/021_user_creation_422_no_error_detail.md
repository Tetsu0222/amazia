# 021: 社員登録 POST /api/users が 422 を返すがエラー詳細が見えない

## ステータス
✅ 解決済（2026-05-05）

## 発症箇所
- 画面: `/users/new`（社員登録フォーム）
- エンドポイント: `POST /api/users` → amazia-core `POST /api/users`

## 症状
社員登録フォームで送信すると 422 が返るが、レスポンスボディが以下の generic な内容のみで何が原因か判断できない。

```json
{
    "timestamp": "2026-05-05T03:50:30.108+00:00",
    "status": 422,
    "error": "Unprocessable Entity",
    "path": "/api/users"
}
```

## 根本原因
amazia-core（Spring Boot）にグローバル例外ハンドラー（`@RestControllerAdvice`）が存在しなかった。

- `@Valid` による Bean Validation 失敗（`MethodArgumentNotValidException`）→ Spring デフォルトのエラーレスポンスのみ返る（`message` / `errors` フィールドなし）
- `ResponseStatusException` によるサービス層の 422 投げ → `reason` フィールドが Spring デフォルトでは含まれない

## なぜ CI で検知できなかったか
- amazia-core の統合テストが存在しなかった（または 422 の場合のレスポンスボディ内容を検証していなかった）
- フロントエンドのエラー表示も実装されておらず、ユーザーが画面から原因を判断できなかった

## 修正内容
`amazia-core/src/main/java/com/example/shared/exception/GlobalExceptionHandler.java` を新規作成。

- `MethodArgumentNotValidException` → `errors` 配列（フィールド名 + メッセージ）を含む 422 レスポンス
- `ResponseStatusException` → `message` フィールドを含む該当ステータスのレスポンス

## 再発防止
| 観点 | 対策 |
|------|------|
| グローバル例外ハンドラー未実装 | Spring Boot プロジェクト初期に `shared/exception/GlobalExceptionHandler` を必ず作成する |
| エラーレスポンス内容の未検証 | API テストで 422 時のレスポンスボディ（`errors` / `message` フィールド）も assertする |
| フロントのエラー表示 | API エラー時はレスポンスの `errors[].message` または `message` をフォーム上に表示する |
