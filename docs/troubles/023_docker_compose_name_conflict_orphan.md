# 023: docker-compose up が孤児コンテナとの name conflict で失敗

## ステータス
✅ 解決済（2026-05-05）

## 発症箇所
GitHub Actions `Deploy to EC2` ワークフロー
`EC2 - Docker停止・ECR pull・Docker起動` ステップ
（[`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml) L186 付近）

## 症状
デプロイの SSM コマンドが Failed で終了。
`StandardErrorContent` にコンテナ作成のコンフリクトが記録されていた。

```
 Container amazia-mysql Stopping
 Container amazia-mysql Stopped
 Container amazia-mysql Removing
 Container amazia-mysql Removed
 Network amazia_default Removing
 Network amazia_default Resource is still in use
...
 Container amazia-core Creating
 Container amazia-core Error response from daemon: Conflict.
   The container name "/amazia-core" is already in use by container
   "91c18246898fa516afb0654af83187bebdfbdf0bd32e7fa7ce6791e881d9d1f0".
   You have to remove (or rename) that container to be able to reuse that name.
failed to run commands: exit status 1
```

ECR pull は成功しており、`amazia-mysql` の停止・削除も成功していた。
しかし `amazia_default` ネットワークは "Resource is still in use" で削除に失敗し、
続く `amazia-core` の作成で同名の旧コンテナとコンフリクトしていた。

## 根本原因
デプロイ時の `docker-compose down` に `--remove-orphans` が付いていなかった。

`docker-compose` は **現在の compose ファイルに定義されているサービス** に紐づくコンテナしか
停止・削除しない。何らかの理由（前回デプロイ時の compose ファイル差分・手動操作・
中断された compose 実行）で compose の管理から外れた "孤児コンテナ" が残ると、
`down` ではそれを掃除できず、ネットワークも参照されたまま削除できない。

今回は `91c18246...` の `amazia-core` が孤児として残留しており、
`amazia_default` ネットワーク削除を阻害 → 次の `up` で同名コンフリクトに至った。

なお systemd unit (`/etc/systemd/system/amazia.service`) の `ExecStart` 側には
`--remove-orphans` が入っていたが（008 で導入）、
**デプロイ経路の `docker-compose down` / `up` 側には入っていなかった** のが穴。

## なぜ CI で検知できなかったか
- 孤児コンテナの発生は Docker デーモンの状態に依存する事象で、ローカル CI では再現しない。
- `docker-compose up` 自体はローカルでも CI でも単独では成功するため、PR 時点では落ちない。
- 本来は EC2 上でしか起きない「過去の compose 実行履歴の残骸」が誘因。

## 修正内容
[`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml) の起動ステップで、
`docker-compose down` / `docker-compose up -d` の双方に `--remove-orphans` を付与。

```diff
- "cd /home/ssm-user/amazia && docker-compose down || true",
+ "cd /home/ssm-user/amazia && docker-compose down --remove-orphans || true",
  ...
- "cd /home/ssm-user/amazia && APP_KEY=... CORS_ALLOWED_ORIGINS=... docker-compose up -d"
+ "cd /home/ssm-user/amazia && APP_KEY=... CORS_ALLOWED_ORIGINS=... docker-compose up -d --remove-orphans"
```

`down` 側で孤児を掃除し、`up` 側は保険として
（compose ファイルから除外されたサービスのコンテナが残っていても自動削除される）。

### 即時復旧手順（手動）
EC2 上で詰まったときは以下で抜ける。

```bash
docker rm -f amazia-core amazia-console amazia-mysql 2>/dev/null || true
cd /home/ssm-user/amazia && docker-compose up -d --remove-orphans
```

孤児コンテナは **複数サービスで連鎖的に発生する** ことがある。
`amazia-core` だけ消して `up` すると、次に `amazia-console` で同じコンフリクトが出て
再度 Failed になる事例を確認済み（2026-05-05 SSM 実行時に再現）。
最初から compose 配下の全コンテナをまとめて `rm -f` するのが確実。

`2>/dev/null || true` を付けているのは、対象コンテナが存在しない場合でも
スクリプトを止めずに `up` まで進めるため。

## 再発防止
| 観点 | 対策 |
|------|------|
| デプロイ系の compose 操作 | `down` / `up` の両方で `--remove-orphans` を必ず使う |
| systemd unit との整合 | systemd 側はすでに `--remove-orphans` 済み（008）。デプロイ側もこれに揃えた |
| 孤児コンテナの早期検知 | デプロイ後ヘルスチェック（既設）で 502/接続不能を検知できるため、新規ステップは不要 |
| 手動操作の禁止事項 | EC2 上で `docker run` / `docker rename` を直接行わない（compose 経路のみ使う） |

## 補足
- `--remove-orphans` は破壊的に見えるが、対象は "compose ファイルに定義されていないが
  プロジェクトラベル `com.docker.compose.project=amazia` を持つコンテナ" のみ。
  通常運用では孤児が発生しないため副作用は無い。
- 今回 `amazia_default` ネットワーク削除失敗が "Resource is still in use" で出たのが
  最初の手がかり。ネットワーク削除エラーを見たら孤児コンテナを疑うこと。
