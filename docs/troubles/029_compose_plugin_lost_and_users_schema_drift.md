# 029: docker compose plugin 消失 + users スキーマ齟齬による全停止

## ステータス
✅ 解決済（2026-05-05）

## 発症箇所
- EC2 (`i-024a0748df78fc93e`) 上の Amazia 全体
- 観測 URL：`http://13.54.203.95/`（Market）/ `:8000`（Laravel）/ `:8001`（Console nginx）/ `:8080`（Spring）

## 症状

[トラブル028](028_cd_ssm_undeliverable_then_container_crashloop.md) の翌日復旧手順を進める過程で、以下の連鎖を確認した。

1. EC2 起動直後に SSM PingStatus=Online でも Session Manager のシェルが返らない
2. AWS Health Dashboard では本リージョン (ap-southeast-2) に該当障害なし → 自分側の問題で確定
3. console-output に `veth` が約 20 秒周期で生成・破棄を繰り返すコンテナ restart loop の痕跡
4. systemd `amazia.service` の journal に `Failed to start ... Main process exited, code=exited, status=125` と **`docker --help` のヘルプ全文ダンプ**

## 根本原因

3 つの独立した問題が重なって全停止に至った。

### 原因1：docker compose v2 プラグインが消失

```
$ docker compose version
docker: 'compose' is not a docker command.

$ ls /usr/libexec/docker/cli-plugins/
docker-buildx          # docker-compose プラグインが存在しない

$ dnf list installed | grep -E "docker|compose"
docker.x86_64    25.0.14-1.amzn2023.0.3    @amazonlinux
（docker-compose-plugin パッケージがインストールされていない）
```

Amazon Linux 2023 の標準リポジトリでは `dnf install docker` だけでは compose プラグインは入らない。
過去のデプロイ（5月初旬時点で `/usr/libexec/docker/cli-plugins/docker-buildx` のタイムスタンプが 5/1〜5/2）と
比較すると、**compose プラグインだけが何らかのタイミングで消失**した。AL2023 のリポジトリには
`docker-compose-plugin` パッケージが存在しないため、再インストールでは復旧不能。

systemd unit の ExecStart は `docker compose up -d --remove-orphans` で v2 形式を前提としていたため、
`compose` がサブコマンドとして認識されず docker がヘルプを出して exit 125 で死亡。
これが [028 §症状](028_cd_ssm_undeliverable_then_container_crashloop.md) で観測された restart loop の真因。

### 原因2：環境変数ファイル `.env.production` が不在

systemd unit は `EnvironmentFile=-/home/ssm-user/amazia/.env.production` を参照していたが、
このファイルが EC2 上にどこにも存在しなかった（先頭 `-` で「無くてもエラーにしない」指定だったため
unit 起動は阻害されず、原因が見えにくかった）。

`docker-compose.yml` 側は `JWT_SECRET: ${JWT_SECRET}` を **デフォルト値なしで参照** しているため、
`.env.production` が無いと Spring/Laravel が空文字で起動しようとする。
GitHub Actions の `deploy.yml` 側でも `JWT_SECRET` を渡している痕跡がなく、
GitHub Secrets にも `JWT_SECRET` は登録されていなかった。

### 原因3：`users` テーブルのスキーマ齟齬

amazia DB の `users` テーブルには Laravel 標準の最小カラム
（`id`, `name`, `email`, `email_verified_at`, `password`, `remember_token`, `timestamps`）
しか存在せず、Spring が `data.sql` で期待する業務カラム
（`employee_id`, `password_hash`, `role_id`, `active_flag`, `failed_attempts`, `locked_until`）が
丸ごと欠落していた。

加えて `roles` / `permissions` / `role_permissions` の 3 テーブルも MySQL volume に存在せず、
[018](018_core_startup_permissions_table_not_exist.md) で対処したはずの状態が再発している
（mysql volume `amazia_mysql_data` 自体は維持されていたため、`docker compose down -v` で
消えたわけではない。経緯不明だが、結果として未作成状態に戻っていた）。

Laravel migration ファイル（[`0001_01_01_000000_create_users_table.php`](../../amazia-console/database/migrations/0001_01_01_000000_create_users_table.php)）も
業務カラムを定義していないため、`migrate:fresh` 等が走ると同じ齟齬が再現する。

## なぜ CI で検知できなかったか

- compose プラグイン消失：EC2 の OS 環境変化に依存し、CI（GitHub Actions ランナー）では再現しない
- `.env.production` 不在：CI の Docker 起動では渡し方が異なるため検知不可
- `users` スキーマ齟齬：CI は H2 インメモリ DB を使用し、本番 MySQL で何が ALTER されているかを検証していない
  ([018](018_core_startup_permissions_table_not_exist.md) と同根)

## 修正内容

### 1. docker compose v2 プラグインを Docker 公式バイナリで配置

```bash
sudo mkdir -p /usr/libexec/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/libexec/docker/cli-plugins/docker-compose
sudo chmod +x /usr/libexec/docker/cli-plugins/docker-compose
docker compose version  # → Docker Compose version v2.x
```

### 2. `.env` ファイルを作成（切り分け用の一時値）

```bash
cd /home/ssm-user/amazia
sudo bash -c 'cat > .env <<EOF
JWT_SECRET=<openssl rand -base64 64 で生成した値>
APP_KEY=base64:EKpjSK2pqqo9qyW10J/MCkO5b06NnVSXE618mLVN4cY=
CORS_ALLOWED_ORIGINS=http://13.54.203.95,http://13.54.203.95:5173
EOF'
```

`docker compose` は同ディレクトリの `.env` を自動で読むため、systemd unit の
`EnvironmentFile=-.env.production` を変更せずとも環境変数が渡る。

### 3. 不足テーブルを mysql に直接作成

```sql
CREATE TABLE IF NOT EXISTS roles (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL
);
CREATE TABLE IF NOT EXISTS permissions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  screen_id VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(200) NOT NULL
);
CREATE TABLE IF NOT EXISTS role_permissions (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  FOREIGN KEY (role_id) REFERENCES roles(id),
  FOREIGN KEY (permission_id) REFERENCES permissions(id)
);
```

### 4. `users` に業務カラムを ALTER 追加

Laravel の `users.id` が `BIGINT UNSIGNED` のため、FK 整合のために `roles` 等も UNSIGNED に揃える。

```sql
ALTER TABLE users
  ADD COLUMN employee_id     VARCHAR(50)  NULL UNIQUE AFTER id,
  ADD COLUMN password_hash   VARCHAR(255) NULL         AFTER password,
  ADD COLUMN role_id         BIGINT UNSIGNED NULL     AFTER password_hash,
  ADD COLUMN active_flag     BOOLEAN NOT NULL DEFAULT TRUE AFTER role_id,
  ADD COLUMN failed_attempts INT     NOT NULL DEFAULT 0    AFTER active_flag,
  ADD COLUMN locked_until    DATETIME NULL              AFTER failed_attempts;

ALTER TABLE role_permissions
  DROP FOREIGN KEY role_permissions_ibfk_1,
  DROP FOREIGN KEY role_permissions_ibfk_2;
ALTER TABLE roles            MODIFY id BIGINT UNSIGNED AUTO_INCREMENT;
ALTER TABLE permissions      MODIFY id BIGINT UNSIGNED AUTO_INCREMENT;
ALTER TABLE role_permissions MODIFY role_id BIGINT UNSIGNED NOT NULL,
                             MODIFY permission_id BIGINT UNSIGNED NOT NULL;
ALTER TABLE role_permissions
  ADD CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES roles(id),
  ADD CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id) REFERENCES permissions(id);
ALTER TABLE users
  ADD CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(id);
```

### 5. インスタンスタイプを一時 t3.small に昇格

restart loop 発生中の SSM 応答阻害（カナリア InProgress 滞留）の真因がメモリ枯渇ではなく
compose プラグイン消失だったことが事後に判明したが、調査時点では切り分け目的で t3.small に昇格して
SSM のシェル応答性を確保した。t3.micro 戻しはメモリ余裕の観点で別途検討
（[phaseX-4 メモリ最適化](../design/phaseX/phaseX-4_memory_optimization_for_t3_micro.md) として独立タスク化）。

## 検証

復旧後、以下を確認した。

| 確認項目 | 結果 |
|---------|------|
| `docker compose ps` で 3 コンテナとも `Up`、mysql は `(healthy)` | ✅ |
| `:80` (Market nginx) | 200 |
| `:8000` (Laravel) | 200 |
| `:8001` (Console nginx) | 200 |
| `:8080/api/products` (Spring) | 200 |
| `:8080/api/auth/login` POST（バリデーション） | 400（期待通り） |
| `systemctl restart amazia.service` 経由の起動 | `active (exited)` で正常完了 |
| メモリ使用量（t3.small / 1913MB 中） | 927MB used、利用可能 822MB |

## 再発防止

| 観点 | 対策 |
|------|------|
| compose プラグイン消失検知 | デプロイ前のヘルスチェックに `docker compose version` の確認を追加（[deploy.yml](../../.github/workflows/deploy.yml) 改修候補） |
| compose プラグインの恒久配置 | EC2 user data またはセットアップスクリプトに `curl` 経由インストールを組み込み、AL2023 標準リポジトリ非依存にする |
| `.env.production` の管理 | GitHub Secrets に `JWT_SECRET` を登録し、`deploy.yml` で `.env` ファイルを生成する手順を追加。トラブル009 の教訓（環境変数追加時は docker-compose.yml と phpunit.xml をセットで更新）を `.env` 生成にも適用 |
| `users` スキーマ齟齬の根本対応 | Laravel migration に業務カラム追加 migration を新設するか、Spring `data.sql` 側で `INSERT IGNORE` 前に `CREATE TABLE IF NOT EXISTS` 相当を記述する。本トラブルでは ALTER 直打ちで応急処置のみ |
| メモリ枯渇による SSM 応答阻害 | t3.small 一時昇格は再現性の高い切り分け手段。長期的には [phaseX-4](../design/phaseX/phaseX-4_memory_optimization_for_t3_micro.md) で Spring Heap 制限と Swap 設定により t3.micro 復帰を試みる → **2026-05-06 phaseX-4 完了。`-Xmx384m` + Swap 2GB で t3.micro 復帰、軽負荷試験 180/180 200・OOM なし** |
| 関連トラブル | [008](008_containers_not_restart_after_ec2_reboot.md)（systemd unit 導入）/ [018](018_core_startup_permissions_table_not_exist.md)（permissions テーブル不在）/ [023](023_docker_compose_name_conflict_orphan.md)（孤児コンテナ）/ [028](028_cd_ssm_undeliverable_then_container_crashloop.md)（restart loop 観測） |

## 補足

### トラブル028 との関係
028 §推定原因では「CD 中断による Docker 残骸 + メモリ枯渇」を主因と推定したが、実際の真因は本ドキュメント原因1（compose プラグイン消失）であった。028 のステータスは本ドキュメント解決をもって ✅ 解決済に更新する。

### `deploy.yml` の `docker-compose` v1 形式
[deploy.yml:266](../../.github/workflows/deploy.yml#L266) は `docker-compose up`（ハイフン形式 = v1）を呼んでいるが、systemd unit は `docker compose`（スペース形式 = v2）を呼んでいる。今回の復旧では v2 プラグインを配置したため整合は取れているが、deploy.yml 側も将来的に v2 形式へ寄せておくのが望ましい。

### t3.small 一時運用と課金
2026-05-05 23:00 頃に EC2 を停止。t3.small で稼働した時間は実質 1 時間程度のため、課金影響は誤差範囲。phaseX-4 で t3.micro 復帰見込みを付けてから再起動する想定。
