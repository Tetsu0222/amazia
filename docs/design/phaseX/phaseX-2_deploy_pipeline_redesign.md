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
4. 画面が表示されるがAPIが応答しないクルクル状態になる
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

### 改善① EC2インスタンスサイズの見直し（最優先）

**対応内容：t3.micro → t3.small（2GB RAM）へ変更**

| | t3.micro | t3.small |
|--|---------|---------|
| vCPU | 2 | 2 |
| メモリ | **1 GB** | **2 GB** |
| 月額（ap-southeast-2） | ~$9 | ~$18 |

Spring Boot（~400MB）+ Laravel（~150MB）+ MySQL（~300MB）+ OS（~150MB）で
すでに1GBの限界。docker pullの余裕がゼロ。
t3.smallにすることでpull中のバッファが確保され、OOMが解消される。

**変更手順：**
```bash
# インスタンス停止
aws ec2 stop-instances --instance-ids i-024a0748df78fc93e --region ap-southeast-2
aws ec2 wait instance-stopped --instance-ids i-024a0748df78fc93e --region ap-southeast-2

# インスタンスタイプ変更
aws ec2 modify-instance-attribute \
  --instance-id i-024a0748df78fc93e \
  --instance-type "{\"Value\": \"t3.small\"}" \
  --region ap-southeast-2

# 起動
aws ec2 start-instances --instance-ids i-024a0748df78fc93e --region ap-southeast-2
```

---

### 改善② デプロイ手順の順序見直し

**対応内容：`pull → down → up` を `down → pull → up` に変更**

コンテナを落としてからpullすることで、pull中のメモリ使用量を大幅に削減できる。
ダウンタイムは発生するが、現状もデプロイ中はクルクルになっているため実質同じ。

```bash
# 現状（メモリ逼迫）
docker pull amazia-core   # コンテナ稼働中にpull → OOM
docker pull amazia-console
docker-compose down
docker-compose up -d

# 改善後（メモリ確保してからpull）
docker-compose down                  # 先にコンテナを落とす
docker pull amazia-core              # メモリに余裕がある状態でpull
docker pull amazia-console
docker-compose up -d
```

**変更箇所：** `.github/workflows/deploy.yml` SSM send-command① のコマンド順序

---

### 改善③ S3転送の軽量化

**対応内容：ソース全体のzip転送をdistファイルのみのsync転送に変更**

現状はソースコード・設定ファイル含む全体をzipしてEC2に転送・unzipしているが、
EC2側で実際に必要なのは以下のみ。

| 用途 | ファイル |
|------|---------|
| フロントエンド配信 | `amazia-market/dist/` |
| Console UI配信 | `amazia-console/public/vue/` |
| Nginx設定 | `nginx/amazia.conf` |
| Docker Compose設定 | `docker-compose.yml` |

```yaml
# 改善後イメージ（deploy.yml）

# Actions側：distと設定ファイルだけS3にsync
- name: S3にdistをsync
  run: |
    aws s3 sync amazia-market/dist/        s3://fullstack-renaissance-demo/dist/market/
    aws s3 sync amazia-console/public/vue/ s3://fullstack-renaissance-demo/dist/console/
    aws s3 cp nginx/amazia.conf            s3://fullstack-renaissance-demo/config/amazia.conf
    aws s3 cp docker-compose.yml           s3://fullstack-renaissance-demo/config/docker-compose.yml

# EC2側（SSM）：syncで差分のみ取得
aws s3 sync s3://fullstack-renaissance-demo/dist/market/   /var/www/amazia-market/
aws s3 sync s3://fullstack-renaissance-demo/dist/console/  /var/www/amazia-console/
aws s3 cp   s3://fullstack-renaissance-demo/config/amazia.conf     /etc/nginx/conf.d/amazia.conf
aws s3 cp   s3://fullstack-renaissance-demo/config/docker-compose.yml /home/ssm-user/docker-compose.yml
```

`s3 sync` は差分のみ転送するため、変更のないファイルをスキップできる。

---

### 改善④ SSMキュー詰まりの自動検知・リカバリ

**対応内容：デプロイ前にSSMステータスを確認し、詰まっていれば事前にEC2 stop/startする**

```yaml
# deploy.yml に追加するステップ（deploy前）
- name: SSMキュー健全性確認
  run: |
    STUCK=$(aws ssm list-commands \
      --instance-id i-024a0748df78fc93e \
      --region ap-southeast-2 \
      --query "length(Commands[?Status=='InProgress'])" \
      --output text)
    echo "InProgressコマンド数: $STUCK"
    if [ "$STUCK" -gt 0 ]; then
      echo "SSMキューが詰まっています。EC2を再起動してクリアします..."
      aws ec2 stop-instances --instance-ids i-024a0748df78fc93e --region ap-southeast-2
      aws ec2 wait instance-stopped --instance-ids i-024a0748df78fc93e --region ap-southeast-2
      aws ec2 start-instances --instance-ids i-024a0748df78fc93e --region ap-southeast-2
      # SSM Online待機
      for i in $(seq 1 18); do
        sleep 10
        STATUS=$(aws ssm describe-instance-information \
          --region ap-southeast-2 \
          --query "InstanceInformationList[?InstanceId=='i-024a0748df78fc93e'].PingStatus" \
          --output text)
        if [ "$STATUS" = "Online" ]; then echo "SSM Online確認"; break; fi
      done
    fi
```

---

## 対応優先順位と工数

| 優先 | 改善策 | 工数 | 効果 |
|------|--------|------|------|
| 1位 | ① インスタンスサイズ変更（t3.small） | 小（CLI数行） | OOMの根本解消 |
| 2位 | ② デプロイ順序見直し（down→pull→up） | 小（deploy.yml 1箇所） | メモリ逼迫の緩和 |
| 3位 | ④ SSMキュー自動検知・リカバリ | 中（deploy.ymlにステップ追加） | 詰まり時の自動復旧 |
| 4位 | ③ S3転送軽量化 | 中（deploy.yml全体的な変更） | 転送・展開時間の削減 |

①②は変更箇所が小さく即効性が高いため、先行して着手することを推奨する。

---

## 完了条件

- [ ] デプロイ後にSSMキューにInProgressが残らないこと
- [ ] EC2 stop/startなしでデプロイが完結すること
- [ ] デプロイ所要時間が5分以内に収まること
