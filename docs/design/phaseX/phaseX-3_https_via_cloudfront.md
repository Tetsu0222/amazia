# フェーズX-3：HTTPS化（CloudFront + DuckDNS / 1ドメイン構成）

## ステータス
🟡 AWS 構築完了・GitHub Variables 設定とデプロイ実行待ち（2026-05-06）

### 実構成（DuckDNS → desec.io / www サブドメイン構成に変更）
- 正規 URL: **`https://www.amazia-portfolio.dedyn.io/`**
- DDNS: desec.io（DuckDNS は ACM 検証用 CNAME を作れず断念）
- CloudFront オリジン: `origin.amazia-portfolio.dedyn.io` → `13.54.203.95`
- CloudFront ディストリビューションドメイン: `d30j0e0y4f4ybd.cloudfront.net`
- ACM 証明書（us-east-1）: `arn:aws:acm:us-east-1:741011674945:certificate/1cd548c6-cbde-4bd3-8fa0-65e8dcd76b6a`

経緯詳細は [トラブル030](../../troubles/030_https_via_cloudfront_duckdns_single_domain.md)。

## 位置付け
時系列フェーズ（1〜20）に依存しない横断的インフラ改善フェーズ。
フェーズ11設計書のステップ9（HTTPS化）に対応するが、設計判断が大きく変わるため
phaseX 系列として独立させ、実装後にフェーズ11設計書のステータスを更新する。

---

## 背景・なぜ今やるか

- 現状の動作確認 URL は `http://13.54.203.95` で **平文 HTTP**。
- 想定ユーザー操作はスマホからのアクセスを含むため、HTTPS 化は必須。
- フェーズ11設計書 §3 では ALB + ACM + 独自ドメイン（`*.amazia.example.com`）を前提としていたが、
  ALB は **無料枠なし・存在するだけで時間課金（約 $16/月）** が発生するため、
  「無料枠の範囲でどこまで完走できるか」を主軸とする本プロジェクトの方針と矛盾する。
- 設計書 §14.5 でも ALB 長期放置への警告が明示されており、トラブル017（AWS課金最適化）と
  整合させる形で、**ALB を採用しない HTTPS 化** に方針転換する。

---

## 設計判断のサマリ

| 項目 | フェーズ11原案 | 本フェーズの判断 | 判断理由 |
|------|---------------|----------------|---------|
| HTTPS 終端 | ALB + ACM | **CloudFront + ACM** | ALB は時間課金、CloudFront は 1TB/月の永久無料枠あり |
| ドメイン | `*.amazia.example.com`（独自） | **`amazia.duckdns.org`（DuckDNS）** | ドメイン取得費もゼロにするため |
| DNS | Route 53 想定 | **DuckDNS 提供 DNS** | Route 53 ホストゾーン（$0.50/月）も回避 |
| サブドメイン構成 | Console / Market / API を分離 | **1 ドメイン + パス分離** | CloudFront ディストリビューションを 1 つに集約し手数最小化 |
| オリジン暗号化 | 内部 HTTP のまま | 同左（CloudFront → EC2 は HTTP） | 学習用途で許容、ポートフォリオではセキュリティ判断として明示 |

**コスト試算：恒久 $0（CloudFront 1TB/月・1000 万リクエスト/月 の永久無料枠内）**

---

## 構成図

```
スマホ ──HTTPS──> CloudFront (amazia.duckdns.org)
                  │  ACM 証明書（us-east-1 必須）
                  │
                  ├─ Behavior 1: /console/api/*  ──HTTP──> EC2 :8000 (Laravel)
                  ├─ Behavior 2: /console/*      ──HTTP──> EC2 :8001 (Console Vue 静的)
                  ├─ Behavior 3: /api/*          ──HTTP──> EC2 :8080 (Core / Spring)
                  └─ Default:    /*              ──HTTP──> EC2 :80   (Market 静的)
```

### URL 体系

| URL | 振り先 | 用途 |
|------|--------|------|
| `https://amazia.duckdns.org/` | Market 静的（nginx :80） | 一般顧客 SPA |
| `https://amazia.duckdns.org/api/*` | Core / Spring（:8080） | Market が叩く API |
| `https://amazia.duckdns.org/console/` | Console Vue 静的（nginx :8001） | 管理画面 SPA |
| `https://amazia.duckdns.org/console/api/*` | Laravel（:8000） | 管理画面 API |

---

## 1ドメイン構成のメリット・留意点

### メリット
- CloudFront ディストリビューションが 1 つで済む（管理・無料枠消費の単純化）
- 同一オリジンになるため CORS 設定が大幅に簡素化される
- ACM 証明書も 1 枚で完結、ドメイン検証も 1 回

### 留意点
- Console を `/console/` 配下で動かすため、Vue Router / Vite の base path 変更が必要
- Cookie の Path を `/console/` に絞る必要あり（Market 側にリフレッシュトークン Cookie が漏れない設計）
- Behavior の優先順位設定を誤ると静的ファイルと API のルーティングが混線する

---

## ステップ一覧

| # | ステップ | 対象 | 内容 | 進捗 |
|---|---------|------|------|----|
| 1 | DuckDNS ドメイン取得 | 外部サービス | `amazia.duckdns.org` を取得、A レコードを EC2 IP に向ける（暫定） | 🔲 ユーザー作業 |
| 2 | EC2 オリジンの動作確認 | EC2 / nginx | HTTP のまま `http://amazia.duckdns.org/` で疎通確認 | 🔲 ユーザー作業 |
| 3 | nginx Host ヘッダ検証追加 | EC2 / nginx | `server_name amazia.duckdns.org;` を設定し直 IP アクセスを遮断 | ✅ 実装完了（amazia.cloudfront.conf） |
| 4 | Vue Router / Vite base path 変更 | Console | Console を `/console/` 配下で動かす対応 | ✅ 実装完了（VITE_BASE_PATH 環境変数で切替） |
| 5 | ACM 証明書発行（us-east-1） | AWS | DuckDNS の TXT レコードで DNS 検証 | 🔲 ユーザー作業 |
| 6 | CloudFront ディストリビューション作成 | AWS | オリジン：EC2 パブリック IP、デフォルト Behavior 設定 | 🔲 ユーザー作業 |
| 7 | CloudFront Behaviors 設定 | AWS | パスベース 4 ルートの振り分け、Cookie/Authorization 転送 | 🔲 ユーザー作業 |
| 8 | CNAME（Alternate Domain Name）紐付け | AWS / DuckDNS | CloudFront に `amazia.duckdns.org` を紐付け、DuckDNS の CNAME を CloudFront ドメインへ | 🔲 ユーザー作業 |
| 9 | アプリ側環境変数の更新 | Core / Console | `PASSWORD_RESET_URL` / `CORS_ALLOWED_ORIGINS` / `REFRESH_COOKIE_*` / `SESSION_*` | ✅ 実装完了（HTTPS_ENABLED フラグで切替） |
| 10 | 結合確認・スマホ動作確認 | 全体 | スマホで HTTPS アクセス、ログイン・パスワード再発行・Market 購入の各シナリオ確認 | 🔲 AWS 側完了後 |
| 11 | 設計書・ドキュメント更新 | docs | フェーズ11 §3 を本構成に合わせて更新、トラブル027 として経緯記録 | 🟡 phaseX-3 反映済み・フェーズ11 §3 / トラブル027 は AWS 切替後 |

---

## 実装メモ（2026-05-06 完了分）

GitHub Variables に `HTTPS_ENABLED=true` をセットすると、deploy.yml が以下を切替えてデプロイする：
- Vue ビルド時 `VITE_BASE_PATH=/console/` を渡す（Vue Router/Vite が自動で `/console/` 配下に追従）
- nginx 設定に `nginx/amazia.cloudfront.conf` を配置（直 IP アクセス 444 / `server_name amazia.duckdns.org` / `/console/` 配下に Console SPA / `/console/api/` を Laravel へプロキシ）
- docker-compose 起動時 `CORS_ALLOWED_ORIGINS=https://amazia.duckdns.org` / `PASSWORD_RESET_URL=https://amazia.duckdns.org/console/password/reset/confirm` / `REFRESH_COOKIE_SECURE=true` / `REFRESH_COOKIE_DOMAIN=amazia.duckdns.org` / `REFRESH_COOKIE_PATH=/console/api/auth/refresh` / `SESSION_SECURE_COOKIE=true` / `SESSION_DOMAIN=amazia.duckdns.org` を渡す
- ヘルスチェックを `https://amazia.duckdns.org/` 等の HTTPS URL で実施

`HTTPS_ENABLED` 未設定時は従来の HTTP 直 IP 構成のまま動作するため、AWS 側準備中も本番デプロイは継続可能。

---

## ステップ詳細

### ステップ 1：DuckDNS ドメイン取得

- DuckDNS（https://www.duckdns.org）でアカウント作成（GitHub / Google でログイン可）
- `amazia` サブドメインを取得（`amazia.duckdns.org`）
- 暫定で A レコードを EC2 のパブリック IP（`13.54.203.95`）に向ける
- DuckDNS のトークンは AWS Secrets Manager / GitHub Secrets には保存しない（読み取り専用情報のため）

**確認：** `dig amazia.duckdns.org` で EC2 IP が返る

---

### ステップ 2：EC2 オリジンの動作確認

- HTTP のまま `http://amazia.duckdns.org/` でアクセスし、Market が表示されることを確認
- `http://amazia.duckdns.org/api/products` で Core API 疎通確認
- `http://amazia.duckdns.org:8001/console/` で Console 疎通確認（パス変更後）

---

### ステップ 3：nginx Host ヘッダ検証追加

**目的：** CloudFront 経由以外のアクセスを遮断し、HTTPS バイパスを防ぐ。

**変更ファイル：** `nginx/amazia.conf`

```nginx
server {
    listen 80 default_server;
    server_name _;
    return 444;  # 想定外の Host は接続を切る
}

server {
    listen 80;
    server_name amazia.duckdns.org;
    # 既存の Market 設定をここに移す
    ...
}
```

**留意：** デプロイ後ヘルスチェック（deploy.yml）は IP 直叩きを行っているため、
Host ヘッダ付きで叩くか、ヘルスチェック専用パスを `default_server` で許容するか検討。

---

### ステップ 4：Vue Router / Vite base path 変更

**変更内容：**

- `amazia-console/resources/vue/vite.config.js` に `base: '/console/'` を追加
- Vue Router の `createWebHistory('/console/')` に変更
- Console 内の API 呼び出し base URL を `/console/api/` に変更
- nginx の Console 配信先を `/var/www/amazia-console/` から `location /console/` で expose

**テスト：**
- ローカル `npm run dev` で `/console/` にアクセスし、ルーティングが機能する
- `/console/login` などの直接アクセスでも 200 が返る（SPA フォールバック）

---

### ステップ 5：ACM 証明書発行（us-east-1）

**重要：CloudFront に紐付ける証明書は必ず us-east-1 リージョンで発行すること。**

- ACM コンソール（us-east-1）で `amazia.duckdns.org` の証明書をリクエスト
- 検証方法：DNS 検証（CNAME）
- ACM が指定する `_xxxxx.amazia.duckdns.org` の CNAME を DuckDNS の TXT レコードに登録

**留意：** DuckDNS の無料プランは TXT レコードを 1 件のみ設定可能。検証完了後は削除しても証明書は維持される。

---

### ステップ 6：CloudFront ディストリビューション作成

**設定値：**

| 項目 | 値 |
|------|---|
| オリジンドメイン | `13.54.203.95`（または DuckDNS ドメイン） |
| オリジンプロトコル | HTTP only |
| オリジンポート | 80 |
| ビューワープロトコル | Redirect HTTP to HTTPS |
| 許可 HTTP メソッド | GET, HEAD, OPTIONS, PUT, POST, PATCH, DELETE |
| キャッシュポリシー（API 用） | CachingDisabled |
| Origin Request Policy | AllViewer |
| 価格クラス | Use only North America and Europe → **要検討**（日本からのレイテンシ確認） |

---

### ステップ 7：CloudFront Behaviors 設定

優先順位の高い順に設定：

| 優先度 | Path Pattern | Origin | キャッシュ | Cookie 転送 | ヘッダ転送 |
|--------|-------------|--------|-----------|------------|-----------|
| 1 | `/console/api/*` | EC2:8000 | 無効 | 全転送 | Authorization, Host |
| 2 | `/console/*` | EC2:8001 | 有効（静的） | なし | Host |
| 3 | `/api/*` | EC2:8080 | 無効 | 全転送 | Authorization, Host |
| Default | `*` | EC2:80 | 有効（静的） | なし | Host |

**留意：** `/console/api/*` を `/console/*` より上位に置かないと API リクエストが静的配信に吸われる。

---

### ステップ 8：CNAME 紐付け

1. CloudFront ディストリビューションの「Alternate Domain Name (CNAME)」に `amazia.duckdns.org` を追加
2. ステップ 5 で発行した ACM 証明書を選択
3. CloudFront のドメイン名（例：`d1xxxx.cloudfront.net`）をコピー
4. DuckDNS の管理画面で A レコードを **削除**、CNAME として CloudFront ドメインを設定
   - DuckDNS が CNAME に未対応な場合は、CloudFront IP を A レコードで指定（IP は変わるため非推奨）

**確認：** `dig amazia.duckdns.org` で CloudFront ドメインへの CNAME が返る

---

### ステップ 9：アプリ側環境変数の更新

新規環境変数を追加する際は `docker-compose.yml` と `phpunit.xml` を必ずセットで更新する（009 教訓）。

| 変数名 | 旧値 | 新値 |
|--------|------|------|
| `PASSWORD_RESET_URL` | `http://13.54.203.95:8001/password/reset/confirm` | `https://amazia.duckdns.org/console/password/reset/confirm` |
| `CORS_ALLOWED_ORIGINS` | `http://13.54.203.95,http://13.54.203.95:5173` | `https://amazia.duckdns.org`（同一オリジンのため最小化） |
| `SESSION_SECURE_COOKIE` | false | **true** |
| `SESSION_DOMAIN` | (空) | `amazia.duckdns.org` |
| `REFRESH_COOKIE_SECURE` | false | **true** |
| `REFRESH_COOKIE_DOMAIN` | (空) | `amazia.duckdns.org` |
| `REFRESH_COOKIE_PATH`（Refresh Token Cookie） | `/api/auth/refresh` | `/console/api/auth/refresh` |

**Refresh Token Cookie Path の設計判断（2026-05-06 修正）：**
- 当初設計案では `/console/` と広めに設定する案だったが、Refresh Token Cookie が Console 配下の全リクエスト（静的ファイル含む）に同送されることになり、CloudFront へのリクエスト数を不要に増やす。
- Cookie の対象パスは Refresh エンドポイント `/console/api/auth/refresh` に限定する。HttpOnly のため Market（同一オリジン）から読み取れない安全性は保たれる。
- Spring 側は `REFRESH_COOKIE_PATH` 環境変数で受け、HTTP 直運用時は `/api/auth/refresh` を維持。

**deploy.yml 側：**
- `docker-compose up -d` 時に渡す環境変数を更新
- ヘルスチェックの URL を `https://amazia.duckdns.org/` に変更

---

### ステップ 10：結合確認・スマホ動作確認

**確認シナリオ：**

| # | 操作 | 期待結果 |
|---|------|---------|
| 1 | スマホで `https://amazia.duckdns.org/` にアクセス | Market が HTTPS で表示、証明書エラーなし |
| 2 | `http://amazia.duckdns.org/` にアクセス | HTTPS にリダイレクトされる |
| 3 | `https://13.54.203.95/` に直接アクセス | nginx が 444 で切断、または接続不可 |
| 4 | `https://amazia.duckdns.org/console/` にログイン | リフレッシュトークン Cookie が `Secure; Path=/console/` で発行される |
| 5 | パスワード再発行メールから新ドメインでパスワード更新 | 一連のフローが HTTPS で完結 |
| 6 | スマホ Safari で「保護された通信」と表示される | 鍵マーク表示 |

**CloudFront 無料枠監視：**
- 1TB/月・1000 万リクエスト/月 の枠内に収まることを CloudWatch で確認
- 超過時は CloudFront ディストリビューションを一時停止する運用を README に明記

---

### ステップ 11：設計書・ドキュメント更新

- フェーズ11設計書 §3「HTTPS 化」を本構成に書き換え、ステップ9を「✅ 実装完了」へ
- フェーズ11設計書 §3.2 のドメイン例を `amazia.duckdns.org/console/` 等に更新
- `docs/troubles/027_https_via_cloudfront_duckdns_single_domain.md` を新規作成
  - ALB を採用しなかった理由
  - DuckDNS を選んだ理由
  - 1 ドメイン構成にした理由
  - CloudFront → オリジン間を HTTP のままにしたセキュリティ判断
- `docs/troubles/README.md` 更新

---

## リスクと対策

| リスク | 対策 |
|--------|------|
| DuckDNS のサービス停止 | 別サブドメインサービス（No-IP / afraid.org）への切り替え手順を README に記録 |
| CloudFront 無料枠超過 | CloudWatch でリクエスト数監視、超過時はディストリビューション停止 |
| ACM 証明書の更新失敗 | ACM は自動更新だが、DuckDNS の CNAME が残っていることを年1回確認 |
| EC2 IP 変更で CloudFront オリジンが切れる | EC2 を Elastic IP 化（無料枠：起動中 1 個まで無料）または DuckDNS ドメインをオリジンに指定 |
| CloudFront → EC2 の HTTP 経路が傍受される | 学習用途では許容するが、ポートフォリオ説明文で「次の改善余地」として明示 |

---

## 期待効果

- スマホからの HTTPS アクセスが可能になる
- 設計書フェーズ11 §3 のステップ9 が完了し、フェーズ11全体のステータス整合が取れる
- 「無料枠縛りで ALB を採用せず CloudFront に切り替えた」設計判断がドキュメントに残り、
  ポートフォリオの差別化要素となる

---

## 参考：採用しなかった選択肢

| 案 | 不採用理由 |
|---|----------|
| ALB + ACM + 独自ドメイン | ALB が時間課金、無料枠完走方針と矛盾 |
| CloudFront 既定証明書（`*.cloudfront.net`） | URL が味気なく、ポートフォリオの見栄えで劣る |
| 自己署名証明書 | スマホで毎回警告が出る |
| Let's Encrypt + EC2 直 | 証明書自動更新の運用負荷、EC2 IP 直公開によるセキュリティ低下 |
| 独自 TLD（`.com` `.dev` 等）+ DuckDNS 不使用 | ドメイン取得費が発生、無料枠完走方針と矛盾 |
| サブドメイン分離（`console.amazia.duckdns.org` 等） | CloudFront ディストリビューションが複数になり手数が増える |
| Cloudflare Tunnel + EC2 直 | 静的配信のキャッシュ効率・EC2 負荷削減・障害独立性で CloudFront に劣る（詳細は次節） |

---

## Cloudflare Tunnel との比較詳細

「ALB を使わず HTTPS 化する」というゴールに対し、Cloudflare Tunnel は最有力の対抗案である。
本フェーズで CloudFront を採用した判断根拠を、学習目的を除外した純粋な技術観点で以下に整理する。

### 観点 1：パフォーマンスとキャッシュ効率

- CloudFront は東京・大阪エッジから配信され、価格クラス次第で日本ユーザーに最寄り配信が安定する。
- Market（静的 SPA）はエッジキャッシュが効くため、オリジン EC2 へのリクエストを大幅に削減できる。
- Cloudflare Tunnel もエッジキャッシュは効くが、Tunnel 経由の常時接続を維持する性質上、オリジン側の負荷削減効果は CloudFront より弱い。
- t2.micro / t3.micro 級の EC2 を想定する本構成では、エッジキャッシュによる EC2 負荷削減のメリットが大きい。

### 観点 2：EC2 リソースと無料枠の保護

- CloudFront 経由ならオリジン EC2 のアウト転送は実質キャッシュミス分のみとなり、AWS 無料枠（EC2 アウト 100GB/月）を超過しにくい。
- Cloudflare Tunnel は基本的に都度オリジンへ取りに行くため、EC2 アウト転送量がそのまま消費される。
- Cloudflare Tunnel は `cloudflared` デーモンを EC2 上で常駐させる必要があり、プロセス監視・自動再起動の運用負荷が増える（障害点が 1 つ増える）。
- CloudFront は AWS マネージドのため、EC2 で動かす常駐プロセスは増えない。

### 観点 3：可用性と障害独立性

- Amazia 全体は AWS 上に構築されているため、HTTPS 終端も AWS 内で完結させた方が外部 SaaS への可用性依存が増えない。
- Cloudflare Tunnel を採用すると、自サービスの可用性が「AWS の障害」に加えて「Cloudflare の障害」にも依存する。
- CloudFront はオリジン EC2 を再起動しても、キャッシュ済みコンテンツはエッジから返り続けるため、デプロイ中も静的配信は維持される。
- Cloudflare Tunnel は cloudflared が EC2 上にあるため、EC2 再起動でサイト全体がダウンする。

### 観点 4：将来の拡張性とデータ所在

- 将来 WAF を導入する場合、CloudFront に AWS WAF を貼るだけで済む。AWS Shield Standard（DDoS 防御）は CloudFront に自動適用・無料。
- Cloudflare の WAF も無料で強力だが、ログ・分析が AWS CloudWatch から分離されるため、運用基盤が二系統になる。
- CloudFront のアクセスログは S3 に出力でき、自 AWS アカウント内に閉じる。
- Cloudflare Tunnel 経由では全リクエストが Cloudflare のインフラを通過する（個人開発レベルでは些末だが、データ主権の観点で差がある）。

### Cloudflare Tunnel 側が優位な点（公平性のため記載）

- EC2 のインバウンドポートを完全に閉じられる（セキュリティグループで 80/443 を 0.0.0.0/0 に開ける必要がない）。
- 設定が極めて簡単（cloudflared をインストールしてトークンを設定するだけ）。
- これらの利点は「動的処理中心でキャッシュが効かないアプリ」「セキュリティ要件が厳しい社内ツール」では決定的だが、
  Amazia のような静的配信込みの一般公開サービスでは観点 1〜4 の優位性が上回ると判断した。
