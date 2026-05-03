# 不具合一覧

詳細分析：[20260503_trouble_analysis.md](../analysis/20260503_trouble_analysis.md)

## 一覧

| # | ドキュメント | 概要 | ステータス | 優先順位 |
|---|-------------|------|-----------|---------|
| 001 | [SSM ConnectionLost](001_ssm_connection_lost.md) | EC2 reboot後にSSMエージェントが回復しない | ✅ 解決済 | - |
| 002 | [MySQL host not allowed](002_mysql_host_not_allowed.md) | Dockerコンテナ間のMySQL接続拒否 | ✅ 解決済 | - |
| 003 | [SSM コマンドキュー詰まり](003_ssm_command_queue_stuck.md) | docker-compose --build によるSSMキュー詰まり | ✅ 解決済 | - |
| 004 | [EC2 IP変更によるCORSエラー](004_ec2_ip_changed_after_restart.md) | stop/start後のIP変動でCORS設定が無効化 | ✅ 解決済 | - |
| 005 | [Nginx 403 Forbidden](005_nginx_console_403.md) | Console UIへのアクセスが403を返す | ✅ 解決済 | - |
| 006 | [Composer platform ext missing](006_composer_platform_ext_missing.md) | ローカルでcomposer updateがext-gd/zipエラーで失敗 | ✅ 解決済 | - |
| 007 | [Excel一括インポート 422エラー](007_excel_import_422.md) | Excelアップロード時に422 Unprocessable Content | ✅ 解決済 | - |
| 008 | [EC2再起動後にコンテナが自動起動しない](008_containers_not_restart_after_ec2_reboot.md) | stop/start後にDockerコンテナがExitedのまま→502 | ✅ 解決済 | - |

## 再発防止アクション（未対応）

分析レポートで特定された再発防止策のうち、まだ実施されていないもの。

| 優先 | 内容 | 関連 | ステータス |
|------|------|------|-----------|
| 高 | デプロイ後ヘルスチェックステップの追加（HTTP 200確認） | 003・005 | ✅ X-2にて対応（[phaseX-2](../design/phaseX/phaseX-2_deploy_pipeline_redesign.md) 完了条件に含む） |
| 中 | 開発環境セットアップ手順書の整備（Dockerボリューム管理・Composer拡張注意事項） | 002・006 | ⬜ 未対応 |
| 低 | デプロイスクリプトへのファイル存在確認ステップの標準化 | 005 | ⬜ 未対応 |

> 優先度「高」のアクションは次の開発サイクルで対応することを推奨。
