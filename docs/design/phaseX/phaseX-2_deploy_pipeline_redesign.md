# フェーズX-2：デプロイパイプライン再設計

## ステータス
🔴 未着手（優先度：即時対応）

## 位置付け
時系列フェーズ（1〜9）に依存しない横断的改善フェーズ。
機能開発と並行して優先着手する。

---

## 背景・なぜ今やるか

デプロイのたびに以下が発生しており、運用として成立していない状態。

1. `docker pull` 中にEC2（t3.micro / 1GB RAM）のメモリが逼迫
2. OOMKillerがSSMエージェントのプロセスを強制終了
3. SSMコマンドキューがInProgressで詰まり、以降のコマンドが届かない
4. 画面は表示されるがAPIが応答しないクルクル状態になる
5. 手動でEC2 stop/start → docker-compose up -d が必要

これは個別の対症療法（コマンド順序の調整・ポーリング改善）では解消できない
**インフラ設計レベルの問題**であり、設計単位での見直しが必要。

---

## 現状の問題整理

| # | 問題 | 原因 | 影響 |
|---|------|------|------|
| 1 | docker pull中にSSMが死ぬ | t3.micro 1GBでコンテナ稼働中にpullするとOOM | デプロイのたびにSSMキュー詰まり |
| 2 | down前にpullしている | pull→down→upの順序ミス | メモリ逼迫を自ら悪化させている |
| 3 | S3経由でソース全体をzip転送 | 不要なファイルも毎回転送・unzip | 転送・展開に無駄な時間 |
| 4 | SSMキュー詰まりの自動回復なし | 詰まっても検知・リカバリする仕組みがない | 毎回手動復旧 |

---

## 改善策

### 改善①②（セットで機能する・必ず同時に実施すること）

> **重要：①と②は単独では不完全。①だけ実施しても②が伴わないと再びOOMが発生する。**

#### 改善① EC2インスタンスサイズの見直し

**対応内容：t3.micro → t3.small（2GB RAM）へ変更**

| | t3.micro | t3.small |
|--|---------|---------|
| vCPU | 2 | 2 |
| メモリ | **1 GB** | **2 GB** |
| 月額（ap-southeast-2） | ~$9 | ~$18 |

**メモリ使用量の試算：**

| プロセス | 常駐メモリ（推定） |
|---------|----------------|
| Spring Boot | ~400MB |
| Laravel + PHP | ~150MB |
| MySQL | ~300MB |
| OS + Docker | ~200MB |
| **常駐合計** | **~1,050MB** |
| docker pull時の一時消費 | +500MB〜1GB |

t3.small（2GB）であっても、**②の順序（down→pull→up）を守る前提でギリギリ機能する**。
②を守らずコンテナ稼働中にpullすれば2GBでも同様のOOMが起きうる。

**変更手順：**
```bash
# インスタンスIDは環境変数・Secretsから取得すること（後述）
aws ec2 stop-instances --instance-ids $EC2_INSTANCE_ID --region ap-southeast-2
aws ec2 wait instance-stopped --instance-ids $EC2_INSTANCE_ID --region ap-southeast-2
aws ec2 modify-instance-attribute \
  --instance-id $EC2_INSTANCE_ID \
  --instance-type "{\"Value\": \"t3.small\"}" \
  --region ap-southeast-2
aws ec2 start-instances --instance-ids $EC2_INSTANCE_ID --region ap-southeast-2
```

---

#### 改善② デプロイ手順の順序見直し

**対応内容：`pull → down → up` を `down → pull → up` に変更**

コンテナを落としてからpullすることで、pull中のメモリ使用量を大幅に削減する。
ダウンタイムは発生するが、現状もデプロイ中はクルクルになっているため実質同じ。

```bash
# 現状（OOM発生パターン）
docker pull amazia-core    # コンテナ稼働中にpull → OOM
docker pull amazia-console
docker-compose down
docker-compose up -d

# 改善後（down→pull→up）
docker-compose down         # 先にコンテナを落としてメモリを解放
docker pull amazia-core
docker pull amazia-console
docker-compose up -d
```

**変更箇所：** `.github/workflows/deploy.yml` SSM send-command① のコマンド順序

---

### 改善③ S3転送の軽量化

**対応内容：ソース全体のzip転送をdistファイルのみのsync転送に変更**

EC2側で実際に必要なファイルは以下のみ。

| 用途 | ファイル |
|------|---------|
| フロントエンド配信 | `amazia-market/dist/` |
| Console UI配信 | `amazia-console/public/vue/` |
| Nginx設定 | `nginx/amazia.conf` |
| Docker Compose設定 | `docker-compose.yml` |

```bash
# Actions側：distと設定ファイルだけS3にsync
aws s3 sync amazia-market/dist/        s3://fullstack-renaissance-demo/dist/market/  --delete
aws s3 sync amazia-console/public/vue/ s3://fullstack-renaissance-demo/dist/console/ --delete
aws s3 cp nginx/amazia.conf            s3://fullstack-renaissance-demo/config/amazia.conf
aws s3 cp docker-compose.yml           s3://fullstack-renaissance-demo/config/docker-compose.yml

# EC2側（SSM）：syncで差分のみ取得
aws s3 sync s3://fullstack-renaissance-demo/dist/market/   /var/www/amazia-market/  --delete
aws s3 sync s3://fullstack-renaissance-demo/dist/console/  /var/www/amazia-console/ --delete
aws s3 cp s3://fullstack-renaissance-demo/config/amazia.conf /etc/nginx/conf.d/amazia.conf
aws s3 cp s3://fullstack-renaissance-demo/config/docker-compose.yml /home/ssm-user/docker-compose.yml
```

**`--delete` フラグについて：**

`s3 sync` のデフォルトはS3にないファイルをローカルで削除しない。
Viteのキャッシュバスティング（ハッシュ付きファイル名）を使っているため、
`--delete` なしでは古いjsファイルがEC2に残り続けディスクを圧迫する。

| フラグ | 動作 | 採用判断 |
|--------|------|---------|
| `--delete` なし | 古いファイルが残る・ディスク圧迫 | ❌ 不採用 |
| `--delete` あり | 古いファイルを削除 | ✅ 採用 |

**ロールバック方針：**

`--delete` を使うとロールバック時に前バージョンのファイルが消えている。
現時点でのロールバック戦略は「再デプロイ（git revert → push）」とし、
S3に前バージョンを保持するバージョニングは将来対応とする。

> ロールバックが即時必要な場面では S3バージョニングの有効化を検討すること。

---

### 改善④ SSMキュー詰まりの自動検知・リカバリ

**対応内容：デプロイ前にSSMキューの健全性を確認し、詰まっていれば自動リカバリする**

**設計上の注意点：**
- InProgressの件数で判定すると「現在正常デプロイ中のコマンド」も誤検知する
- 判定基準は「一定時間以上（15分超）InProgressのまま変化しないコマンドが存在するか」とする
- SSM Online確認ループはタイムアウト時に `exit 1` で明示的に失敗させる

```yaml
# deploy.yml に追加するステップ（deploy前）
- name: SSMキュー健全性確認
  run: |
    # 15分以上前からInProgressのコマンドを詰まりと判定
    THRESHOLD=$(date -u -d '15 minutes ago' +%Y-%m-%dT%H:%M:%S 2>/dev/null \
      || date -u -v-15M +%Y-%m-%dT%H:%M:%S)

    STUCK=$(aws ssm list-commands \
      --instance-id ${{ secrets.EC2_INSTANCE_ID }} \
      --region ap-southeast-2 \
      --query "length(Commands[?Status=='InProgress' && RequestedDateTime<'${THRESHOLD}'])" \
      --output text)

    echo "15分超InProgressコマンド数: $STUCK"

    if [ "$STUCK" -gt 0 ]; then
      echo "SSMキューが詰まっています。EC2を再起動してクリアします..."
      aws ec2 stop-instances \
        --instance-ids ${{ secrets.EC2_INSTANCE_ID }} --region ap-southeast-2
      aws ec2 wait instance-stopped \
        --instance-ids ${{ secrets.EC2_INSTANCE_ID }} --region ap-southeast-2
      aws ec2 start-instances \
        --instance-ids ${{ secrets.EC2_INSTANCE_ID }} --region ap-southeast-2

      # SSM Online待機（タイムアウト時は明示的に失敗）
      ONLINE=false
      for i in $(seq 1 18); do
        sleep 10
        STATUS=$(aws ssm describe-instance-information \
          --region ap-southeast-2 \
          --query "InstanceInformationList[?InstanceId=='${{ secrets.EC2_INSTANCE_ID }}'].PingStatus" \
          --output text)
        echo "[$i] SSM: $STATUS"
        if [ "$STATUS" = "Online" ]; then
          ONLINE=true
          break
        fi
      done

      if [ "$ONLINE" = "false" ]; then
        echo "ERROR: SSM Online確認タイムアウト（180秒）"
        exit 1
      fi
      echo "SSM Online確認。デプロイを継続します。"
    fi
```

---

### 改善⑤ インスタンスIDのSecrets化

**対応内容：ハードコードされているインスタンスIDを `secrets.EC2_INSTANCE_ID` に外出し**

現状、deploy.yml の複数箇所にインスタンスID `i-024a0748df78fc93e` が直書きされている。

**問題：**
- インスタンス再作成時に全箇所の更新漏れが起きる
- インスタンスIDが公開リポジトリに露出する

**対応：**
```yaml
# GitHub Secrets に登録
# キー名: EC2_INSTANCE_ID
# 値: i-024a0748df78fc93e

# deploy.yml での参照
--instance-ids ${{ secrets.EC2_INSTANCE_ID }}
```

deploy.yml 全体でインスタンスIDを `${{ secrets.EC2_INSTANCE_ID }}` に置換する。

---

## 対応優先順位と工数

> **①②は必ずセットで実施すること。どちらか単独では効果が不完全。**

| 優先 | 改善策 | 工数 | 効果 | 依存 |
|------|--------|------|------|------|
| **1位** | **①②セット** インスタンスサイズ変更 + デプロイ順序修正 | 小 | OOMの根本解消 | なし |
| **2位** | **⑤** インスタンスIDのSecrets化 | 小（deploy.yml全置換） | 運用負債の解消 | なし |
| **3位** | **④** SSMキュー自動検知・リカバリ | 中 | 詰まり時の自動復旧 | ①②完了後に導入推奨 |
| **4位** | **③** S3転送軽量化 | 中 | 転送・展開時間の削減 | ロールバック方針の確認後 |

---

## 完了条件

- [ ] EC2 stop/startなしでデプロイが完結すること
- [ ] デプロイ後にSSMキューにInProgressが残らないこと
- [ ] デプロイ所要時間が現状（約5分）から計測・比較し改善を確認すること
- [ ] インスタンスIDがdeploy.ymlにハードコードされていないこと
