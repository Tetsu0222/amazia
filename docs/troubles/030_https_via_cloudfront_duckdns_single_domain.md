# 030: HTTPS化を ALB ではなく CloudFront + desec.io / 1ドメイン構成で実装した経緯

## ステータス
✅ AWS 構築完了（2026-05-06）／ GitHub Variables 設定とデプロイ実行待ち

## 最終構成
- **正規 URL**: `https://www.amazia-portfolio.dedyn.io/`
- **DDNS プロバイダ**: desec.io（無料・`*.dedyn.io`）
- **CloudFront ディストリビューションドメイン**: `d30j0e0y4f4ybd.cloudfront.net`
- **オリジン**: `origin.amazia-portfolio.dedyn.io`（A レコードで `13.54.203.95` を指す）
- **ACM 証明書**: `arn:aws:acm:us-east-1:741011674945:certificate/1cd548c6-cbde-4bd3-8fa0-65e8dcd76b6a`

## 発症箇所
- 設計書 [phaseX-3_https_via_cloudfront.md](../design/phaseX/phaseX-3_https_via_cloudfront.md)
- フェーズ11設計書 §3「HTTPS 化」

## 症状
本トラブルは「不具合」ではなく **設計判断の経緯記録**。
当初フェーズ11設計書 §3 では HTTPS 化を **ALB + ACM + 独自ドメイン（`*.amazia.example.com`）** で実装する想定だった。実装直前に以下の問題が顕在化したため、構成を見直した。

- ALB は **無料枠なし・存在するだけで時間課金（約 $16/月）**
- 独自ドメイン取得費（年 $10〜）
- Route 53 ホストゾーン費用（$0.50/月）
- いずれも本プロジェクトの「無料枠完走を主軸とする方針」と矛盾

## 根本原因
原案は AWS のフルマネージド構成を前提に設計されていたが、本プロジェクトのコスト方針（恒久 $0 / 学習目的）と整合していなかった。

## 採用した構成
| 項目 | 採用案 | 不採用案 | 判断根拠 |
|------|--------|----------|----------|
| HTTPS 終端 | CloudFront + ACM | ALB + ACM | CloudFront は 1TB/月 永久無料枠あり |
| ドメイン | desec.io（`www.amazia-portfolio.dedyn.io`） | DuckDNS / 独自 TLD | DuckDNS は ACM 検証用 CNAME（プレフィックス付き）を作れず断念。desec.io は無料 + フル DNS 機能 |
| DNS | desec.io 提供 DNS（DNSSEC 対応） | Route 53 / DuckDNS API | ホストゾーン料金回避・ACM DNS 検証可 |
| サブドメイン | 1 ドメイン + パス分離（`/console/`, `/api/`） | サブドメイン分離 | CloudFront ディストリビューションを 1 つに集約・CORS 簡素化 |
| ホスト名 | `www.amazia-portfolio.dedyn.io`（ルートでなく www） | ルート `amazia-portfolio.dedyn.io` | DNS RFC でルートに CNAME は許可されない（NS と競合）。`www` ホストでこの制約を回避 |
| オリジン解決 | `origin.amazia-portfolio.dedyn.io`（DDNS で IP 経由） | EC2 IP 直接 | CloudFront は IP オリジンを許可しない（仕様変更） |
| オリジン暗号化 | CloudFront → EC2 は HTTP | 内部も HTTPS | 学習用途で許容、ポートフォリオで「次の改善余地」として明示 |
| Cloudflare Tunnel | 不採用 | — | 静的キャッシュ効率・EC2 アウト転送量・障害独立性で CloudFront 優位 |

## 設計書からの変更点
1. **Refresh Token Cookie Path**：当初案の `/console/` は広すぎ、refresh エンドポイント以外にも Cookie が同送される。`/console/api/auth/refresh` に絞る設計に修正。
2. **段階実装**：AWS 側作業（DuckDNS / ACM / CloudFront）はユーザー実施のため、コード側は GitHub Variables `HTTPS_ENABLED=true` で切替可能な構造とし、未設定なら従来の HTTP 直 IP 構成のまま動作するようにした。

## 修正内容
| ファイル | 変更 |
|---------|------|
| `nginx/amazia.cloudfront.conf` | 新規作成。default_server で 444・`server_name amazia.duckdns.org`・`/console/` 配下に Console SPA・`/console/api/` を Laravel へプロキシ・ヘルスチェック専用 `/__health` |
| `amazia-console/resources/vue/vite.config.js` | `base` を `VITE_BASE_PATH` 環境変数で指定（未設定時は `/`） |
| `amazia-console/resources/vue/src/router/index.js` | `createWebHistory(import.meta.env.BASE_URL)` で base に追従 |
| `amazia-console/resources/vue/src/features/auth/api/authApi.js` | `BASE = ${import.meta.env.BASE_URL}api`、未認証時 redirect も BASE_URL 連結に |
| `amazia-console/resources/vue/src/features/products/pages/ProductImport.vue` | `axios.post('/api/products/import')` を BASE_URL 連結に |
| `amazia-console/resources/vue/src/features/skus/pages/SkuList.vue` | 画像 src を BASE_URL 連結に |
| `amazia-core/src/main/java/com/example/auth/service/LoginService.java` | Cookie の `secure` / `domain` / `path` を `@Value` で注入 |
| `amazia-core/src/main/java/com/example/auth/service/RefreshTokenService.java` | 同上 |
| `amazia-core/src/main/resources/application-{dev,local}.properties` | `refresh-cookie.{secure,domain,path}` を環境変数で受ける既定値追加 |
| `amazia-core/src/test/resources/application-test.properties` | テスト用 `refresh-cookie.*` 既定値追加 |
| `amazia-core/src/test/java/com/example/auth/{Login,RefreshToken}ControllerTest.java` | Cookie の path / secure / domain アサートを追加（@Value 経由） |
| `docker-compose.yml` | `REFRESH_COOKIE_*` / `SESSION_SECURE_COOKIE` / `SESSION_DOMAIN` を Spring / Laravel に渡す |
| `amazia-console/phpunit.xml` | `SESSION_SECURE_COOKIE` / `SESSION_DOMAIN` のテスト用既定値追加 |
| `.github/workflows/deploy.yml` | `vars.HTTPS_ENABLED == 'true'` で Vue ビルド `VITE_BASE_PATH` / 起動時環境変数 / nginx 設定 / ヘルスチェック URL を切替 |

## なぜ CI で検知できなかったか
本件は機能不具合ではなく構成判断のため、CI による検知対象外。ただし、新規環境変数の追加に伴い `phpunit.xml` / `application-test.properties` への追記漏れが発生していれば検知できる仕組み（009 の教訓）は機能している。

## 再発防止
| 観点 | 対策 |
|------|------|
| インフラ選定とコスト方針の不整合 | 設計書フェーズ着手前に「無料枠で完走できる構成か」を必ず確認するチェックリストを `feedback_free_tier_first` メモリに記録済み |
| Cookie Path の設計ミス | Cookie は HttpOnly でも漏洩リスクは残るため、エンドポイントに最も近いパスに絞る（最小スコープ原則） |
| 段階的有効化の必要性 | コード変更と AWS 側作業が分離する場合、フィーチャーフラグ（本件では `HTTPS_ENABLED`）で切替可能にすることで、片方が未完でもデプロイを止めない |
| 新規環境変数のテスト追記 | 009 の教訓どおり `docker-compose.yml` ✅・`phpunit.xml` ✅・`application-test.properties` ✅ をセットで更新 |

## 残タスク
1. ✅ DuckDNS で `amazia.duckdns.org` 取得（後に desec.io に変更）
2. ✅ ACM（us-east-1）で `www.amazia-portfolio.dedyn.io` 証明書発行・DNS 検証完了
3. ✅ CloudFront ディストリビューション作成（オリジン：`origin.amazia-portfolio.dedyn.io` / プロトコル：HTTP）
4. ✅ CloudFront Behaviors 設定（優先度順：`/console/api/*` → `/console/*` → `/api/*` → `*`）
5. ✅ CloudFront に Alternate Domain Name `www.amazia-portfolio.dedyn.io` を紐付け、ACM 証明書を選択
6. ✅ desec.io で `www` の CNAME を CloudFront ドメインへ設定
7. 🔲 GitHub Variables に以下をセットしてデプロイ実行
   - `HTTPS_ENABLED=true`
   - `DEPLOY_DOMAIN=www.amazia-portfolio.dedyn.io`
8. 🔲 スマホで HTTPS アクセス確認、ログイン・パスワード再発行・購入フローを動作確認

---

## 追記（2026-05-06）：DuckDNS 不採用と desec.io 移行検討

### 発生した制約
ステップ5（ACM 証明書発行）の DNS 検証時に、ACM が要求する CNAME 名は `_xxxxxxxx.amazia.duckdns.org` のような **プレフィックス付きサブドメイン** だが、DuckDNS の無料プランは：

- 管理画面に TXT レコード入力欄がない
- `https://www.duckdns.org/update?...&txt=...` API は **`amazia.duckdns.org` 自身の TXT レコードしか更新できない**
- プレフィックス付きサブドメイン（`_xxxx.amazia.duckdns.org`）のレコードを作成する手段が存在しない

実際に `Resolve-DnsName -Name _xxxx.amazia.duckdns.org -Type CNAME` で NXDOMAIN（レコード不在）が確認された。

### 移行先候補
| 案 | ドメイン例 | コスト | プレフィックス付き CNAME |
|----|-----------|--------|------------------------|
| desec.io | `amazia.dedyn.io` | $0 | ⭕ 管理画面で追加可能 |
| CloudFront 既定証明書 | `dXXXXXX.cloudfront.net` | $0 | 不要（ACM 自体が不要） |

### 採用方針（2026-05-06）
1. **第一候補：desec.io（`amazia.dedyn.io`）に移行して ACM 検証を通す**
2. **フォールバック：desec.io でも問題があれば CloudFront 既定証明書を採用**し、ポートフォリオでは「無料枠縛りのため URL の見栄えは妥協した」と経緯を明示

設計書記載の `amazia.duckdns.org` は具体例として残しつつ、`DEPLOY_DOMAIN` 環境変数で吸収する設計のため、コード側は変更不要。

---

## 追記（2026-05-06 続き）：構築中に判明した追加制約

### 1. CloudFront の IP オリジン拒否
オリジンドメインに直接 EC2 IP（`13.54.203.95`）を入力したところ、CloudFront UI が「**発信元ドメインは IP アドレスであってはなりません**」とエラー。

**対処**：desec.io に **`origin.amazia-portfolio.dedyn.io`** という A レコードを追加（同じく `13.54.203.95` を指す）し、これをオリジンドメインに指定。

将来 ステップ8 で `amazia-portfolio.dedyn.io` の DNS を CloudFront に向ける際、もしオリジンに同じ名前を使っていると CloudFront → CloudFront の永久ループが発生する。`origin.*` を分離しておくことでこのリスクを根本的に回避。

### 2. ルートドメインに CNAME 不可（DNS RFC 制約）
ステップ8 で `amazia-portfolio.dedyn.io`（ルート）に CNAME を設定しようとしたところ、desec.io が「**競合するタイプの RR セットが存在します：データベース（NS）。CNAME と並行して他の RR セットは許可されていません**」とエラー。

これは DNS RFC 1034 の規定で、**ルートドメインには NS レコードが必須でありそれと CNAME は共存できない**という根本的な仕様。Route 53 や Cloudflare は「ALIAS」「CNAME flattening」という独自機能でこれを回避するが、desec.io は持たない。

**対処**：正規 URL を **`www.amazia-portfolio.dedyn.io`** に変更。`www` ホストなら NS との競合がないので CNAME を設定できる。
- ACM 証明書も `www.amazia-portfolio.dedyn.io` 用に再発行
- CloudFront の Alternate Domain Name も `www` に変更

これは大手サイト（amazon.com → www.amazon.com にリダイレクト）と同じ構成のため、ポートフォリオ品質を損なわない。

### 3. CloudFront のオリジンプロトコル既定値が HTTPS only
オリジン作成ウィザードの「**オリジン設定**」で「**推奨される原点設定を使用する**」を選択すると、デフォルトで HTTPS に倒される。EC2 は 443 を開けていないため、CloudFront → EC2:443 への接続がタイムアウトし **504 Gateway Timeout** を返す。

**対処**：オリジン編集画面で **プロトコル = HTTP のみ / HTTP ポート = 80** に修正。

### 4. ACM 証明書のリージョン
最初にうっかり ap-southeast-2（シドニー・EC2 と同じ）で証明書をリクエストしてしまったが、CloudFront は **us-east-1（バージニア北部）** で発行された証明書しか紐付けられない。

**対処**：us-east-1 でリクエストし直し。シドニーの証明書は削除予定。

---

## 教訓まとめ
| 観点 | 教訓 |
|------|------|
| ACM 証明書 | CloudFront 用は必ず **us-east-1** で発行 |
| CloudFront オリジン | IP 直接指定不可・**ホスト名（DDNS 等）必須** |
| CloudFront オリジン分離 | CloudFront 配信ドメインとオリジン解決ドメインは**分けるべき**（永久ループ回避） |
| DDNS 選定基準 | 「プレフィックス付き CNAME を作れるか」が ACM DNS 検証の必須要件 |
| ルート CNAME | DNS RFC で禁止。**`www` ホストに正規 URL を寄せる**のが定石 |
| CloudFront プロトコル | デフォルトの「推奨設定」は HTTPS 前提。**HTTP オリジンは明示的に HTTP only に変更** |

---

## トラブル経緯（時系列）
1. DuckDNS でドメイン取得・A レコード設定 → ✅ ステップ1 完了
2. ACM で `amazia.duckdns.org` 証明書をリクエスト → DuckDNS でプレフィックス付き CNAME 不可で **詰む**
3. desec.io に移行・`amazia-portfolio.dedyn.io` 取得・A レコード設定
4. ACM で `amazia-portfolio.dedyn.io` 再リクエスト → 発行成功 ✅
5. CloudFront ディストリビューション作成時、オリジンに EC2 IP を直接指定 → 拒否される
6. desec.io に `origin.amazia-portfolio.dedyn.io` A レコード追加 → 解決
7. CloudFront 経由動作確認 → **504 Gateway Timeout**
8. オリジンプロトコルが HTTPS only になっていたことが判明 → HTTP only に修正 → 動作確認 OK ✅
9. ルートに CNAME を設定しようとして **NS と競合エラー**
10. `www` サブドメイン構成に変更・ACM 証明書を `www.amazia-portfolio.dedyn.io` で再発行
11. CloudFront の Alternate Domain Name と SSL 証明書を `www` 用に切替 → **`https://www.amazia-portfolio.dedyn.io/` 完全動作** ✅
