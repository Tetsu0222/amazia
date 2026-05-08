# フェーズX-9：テスト分離設計の体系化と H2 共有問題の抹本対策

## ステータス
🟡 実装フェーズ移行確定（2026-05-08）

スコープ定義レビュー完了。**案 B（PoC → 全件適用）+ 規約化 + CI random 順序ジョブ追加** で実装に進む。**案 C（Testcontainers）は Phase 21 として分離**。

## 位置付け
時系列フェーズ（1〜20）に依存しない横断的品質改善フェーズ。phaseX-6（スキーマ層の構造的盲点対策）・phaseX-7（AI協働の構造的盲点対策）と同系列で、**テスト層の構造的盲点**に対する対策として位置付ける。

直接の起因はトラブル 051（CI 失敗：`ApplyScheduledPricesJobTest` の H2 テスト分離不足 + Market E2E のタイムアウト張り付き）の派生①〜③。**派生だけで `@Transactional` 一括付与・自衛コード追加・アサーション設計変更を 4 段階にわたり踏むことになり、「テストを通すこと」が「不具合を検知すること」を浸食する状況**が露呈した。

---

## 背景・なぜ今やるか

### 構造的盲点

```
H2 in-memory DB を @SpringBootTest 全クラスで共有（DB_CLOSE_DELAY=-1）
  └─ クラス分離は @Transactional ロールバックに依存
      └─ ただし @Transactional(REQUIRES_NEW) を経由する書き込みは貫通する
          └─ FaultInjectionLogger / BatchAlertNotifier / BatchExecutionRecorder 等
              └─ 件数アサーションを行う Repository テストが他テストの残置で破綻
                  └─ 自衛コード追加・アサーション設計変更で対症療法
                      └─ テストが「自テストの正しさ」だけ見るようになり、観測力が低下
```

### 派生①〜③で踏んだ症状の整理

| 派生 | 症状 | 即時対応 | 副作用 |
|---|---|---|---|
| ① | `ApplyScheduledPricesJobTest.APP_3` 失敗（テスト間汚染） | 当該クラスに `@Transactional` 付与 | なし |
| ② | `FaultInjectionLogRepositoryTest` / `NotificationSubscriptionRepositoryTest` 失敗（同型汚染） | 当該汚染源 2 クラスに `@Transactional` 付与 | なし |
| ③前半 | `expected: <1> but was: <3>` モグラ叩き | `@SpringBootTest` 全 46 クラスへ `@Transactional` 一括付与 | **6 クラスは設計上 revert**（マルチスレッド検証・REQUIRES_NEW 検証・テーブル間アーカイブ等） |
| ③後半 | `REQUIRES_NEW` 経由レコードが残置（`@Transactional` 貫通） | `FaultInjectionLogRepositoryTest` / `ConsoleNotificationRepositoryTest` に自衛コード追加・`APP_3` アサーションを「自テスト所有 ID」ベースに変更 | テスト間で「掃除する責任」が暗黙の結合になる |
| ③追加 | `INV_2` の before スナップショット計算ミス露呈 | productId フィルタ後の件数で取り直し | 健全な修正だが random 順序で初めて顕在化 |

### 現状の妥協点

1. **`@Transactional` 不在 6 クラス**は依然として他テストへの汚染源（自衛コード受け側で対処中）
2. **`@BeforeEach cleanupPriorXxx` パターン増殖**：テスト独立性のはずが「他テストの残置を掃除する」結合が増えた
3. **`scheduler-enabled=true` 13 クラス**は `@Scheduled` 発火の非決定性を抱えたまま
4. **CI Linux 順序依存**：ローカル random 5 回 PASS でも CI で踏むパターンがある（実際 4 回踏んだ）
5. **設計意図の明文化不足**：Javadoc に「クラスレベル `@Transactional` を付けない」と書いていたテストを Claude が一括付与で踏み潰した。規約として明文化されていれば防げた

### なぜ次フェーズで対応するか

- **症状療法の限界**：派生③後半の自衛コードは「件数アサーションを生むテストに毎回パッチを当てる」運用になりつつある。新規 Repository テスト追加のたびに同じ判断が必要で、**テストレビューの認知負荷が増大**
- **ポートフォリオ価値**：テスト分離は実務でも頻発する論点。phaseX-6 / phaseX-7 と並ぶメタ品質フェーズとして整理すれば**「実務的な構造的盲点に対して体系的対策を打てる」プロジェクト**として打ち出せる
- **AP-009 候補の確立タイミング**：phaseX-7 メモリ方針「新規パターンが 2 件以上累積したら AP として追記」のとおり、051 派生①〜③で **4 件累積**。AP-009 として正式化する適切なタイミング

### なぜ今すぐ実装ではなくスコープ定義に留めるか

- **無料枠完走方針**（メモリ `feedback_free_tier_first`）：抹本対策案 C（Testcontainers）はリソース・時間コスト高
- **着手判断材料が必要**：案 A〜D の比較・コスト見積もり・期待効果を提示してユーザーが**実施 or 技術的負債として記録**を判断する材料を揃える段階に留める

---

## 着手前提条件

- 051 派生①〜③の即時対応は完了済み（`@Transactional` 一括付与・自衛コード・アサーション変更）
- ローカル `mvn test -Dsurefire.runOrder=random` ×5 連続 PASS まで到達済み（最終修正後）
- 実装着手は本設計書のレビュー・案選定後に別タスクで切る

---

## スコープ

### スコープ内（本フェーズで扱う）

| # | 項目 | 内容 |
|---|---|---|
| S1 | テスト分離方針の体系化 | `@Transactional` 付ける/付けない判断軸の規約化、件数アサーションの自衛コード規約化、スナップショット差分の規約化を `test_insights.md` カテゴリ 7-2 に体系的に追加 |
| S2 | 抹本対策案の比較検討 | 後述「設計判断の選択肢」案 A〜D を比較・実機 PoC（軽量）・コスト試算・推奨案を提示 |
| S3 | AP-009 の正式化 | `ai_collaboration_antipatterns.md` に「テスト分離不足 + 単発 PR で類似クラス見落とし」を AP-009 として追加、`prompt_templates.md` に TPL-009 を双方向追加 |
| S4 | 既存の妥協点の解消（推奨案実施時のみ） | revert した 6 クラスの本来の検証意図を保ったまま分離する設計の再考、`scheduler-enabled=true` 13 クラスの非決定性排除 |
| S5 | CI への random 順序ジョブ追加 | `mvn test -Dsurefire.runOrder=random` を週次/PR チェックとして deploy.yml に追加検討（順序依存バグの早期検知） |

### スコープ外

- 全テストの再設計（コスト過大、本フェーズの目的は規約整備と抹本対策の選定）
- Market（Vitest）/ Console（PHPUnit）側のテスト分離見直し（本件は Core の H2 共有問題が起点）
- 本番 MySQL を使ったテスト基盤の構築（Testcontainers が候補に挙がるが、別フェーズで再検討）

---

## 設計判断の選択肢（実装案）

実装フェーズで選定する候補。本設計書ではスコープ定義として 4 案を提示するに留める。

### 案 A: `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` で context 毎テスト再生成
- **効果**：完全分離（H2 含む全 Bean が再生成される。ただし `DB_CLOSE_DELAY=-1` を解除しないと無効）
- **コスト**：テスト時間 5〜10 倍（524 件で現状 3 分 → 15〜30 分）
- **判断**：❌ CI 時間予算を破綻させる（GitHub Actions 無料枠は月 2,000 分）

### 案 B: `@Sql` でテスト前後に DB クリア
- **効果**：テストごとに対象テーブルだけ TRUNCATE → 確実な分離
- **コスト**：低（cleanup.sql ファイル 1 つ + クラスレベル `@Sql` アノテーション付与）
- **副作用**：FK 制約の解決順を考慮した TRUNCATE スクリプトが必要・初期データ（`test-data.sql`）の再投入も必要
- **判断**：⭐ 推奨候補（コスト低・効果高）

### 案 C: Testcontainers で per-class MySQL に切替
- **効果**：完全分離 + 本番との乖離も解消（H2 と本番 MySQL の DDL 互換性問題を根絶）
- **コスト**：高（Docker 必須・テスト時間増加・Testcontainers 学習コスト・CI ジョブ調整）
- **副作用**：ローカル開発で Docker 必須化（既に必須なので影響小）
- **判断**：⭐⭐ 中長期推奨（ポートフォリオ価値高・H2 ドリフト系の 027/037/038/044/045/049/050 系統を構造的に解決）

### 案 D: 現状の `@Transactional` + 自衛コード方式を維持・規約化のみ
- **効果**：低（症状療法の継続）
- **コスト**：最小（規約と AP-009 整備のみ）
- **判断**：🟡 妥協案（時間予算最優先の場合の選択肢）

### 案の組み合わせ（実装フェーズで具体化）
- **B + 規約化**：cleanup.sql と `@Sql` で Repository 層テストの分離を担保しつつ、Service/Controller テストは現状維持
- **C を Phase 21 等の独立フェーズに切り出し**：Testcontainers 移行はフェーズ規模が大きいので別タイミングで実施

---

## 実装 Step（確定）

実施順序は **②前 → ① → ②後 → ③ → ④分離** で進める（PoC 結果に引きずられない順序で規約化を先行させる）。

| Step | 内容 | 想定工数 | 備考 |
|---|---|---|---|
| 0 | 既存の妥協点の棚卸し（`@Transactional` 不在 6 クラス + scheduler-enabled 13 クラスの分類） | 0.5 日 | Phase 21 への引継ぎ事項としても整理 |
| 1 | **②前**：規約化のうち PoC 非依存部分を確定<br>・`@Transactional` 付ける/付けない判断軸<br>・件数アサーション規約<br>・AP-009 / TPL-009 を `ai_collaboration_antipatterns.md` / `prompt_templates.md` に追加 | 0.5 日 | `test_insights.md` カテゴリ 7-2 の規約のうち、cleanup.sql に依存しない部分 |
| 2 | **①**：案 B の PoC<br>・対象：`FaultInjectionLogRepositoryTest`（REQUIRES_NEW 貫通の自衛コード受け側）<br>・cleanup.sql 作成 + クラスレベル `@Sql` 適用<br>・効果測定：(a) 自衛コード削除可否 (b) ローカル random 5 回 PASS (c) 当該クラスのテスト時間増分 | 1 日 | FK 解決順 TRUNCATE と test-data.sql 再投入の知見を取得 |
| 3 | **②後**：規約化のうち PoC 依存部分を追記<br>・cleanup.sql の FK 解決順規約<br>・`@Sql` 適用基準（どの層のテストに適用するか） | 0.5 日 | PoC 結果を反映 |
| 4 | **②続き**：案 B の全件適用<br>・現状の自衛コードを段階的に cleanup.sql 方式へ置換<br>・`@Transactional` 不在 6 クラスの再評価 | 2〜3 日 | 案 C 選定時の Testcontainers PoC は本フェーズではやらない |
| 5 | **③**：CI への random 順序ジョブ追加<br>・**週次 cron** で `mvn test -Dsurefire.runOrder=random` を実行<br>・失敗時は **Issue を自動起票**するワークフロー<br>・PR 毎チェックには含めない（flaky で開発体験を損ねるため） | 0.5 日 | `deploy.yml` ではなく独立した `weekly-test-random-order.yml` を新設 |
| 6 | **④**：案 C を Phase 21 へ正式分離<br>・本フェーズで残った妥協点（`scheduler-enabled=true` 13 クラスの非決定性等）を Phase 21 引継ぎ事項として明記<br>・H2 ドリフト系（027/037/038/044/045/049/050）の構造的解決として位置付け | 0.5 日 | Phase 21 の設計書スコープ定義のみ作成 |
| **合計** | | **約 5.5〜6.5 日** | |

---

## コスト試算

**スコープ定義段階：恒久 $0**
- 本設計書作成のみで AWS リソース変更なし

**実装フェーズ移行時の追加コスト**：
- 案 B：$0（cleanup.sql は git 管理）
- 案 C：CI 実行時間増（GitHub Actions の無料枠範囲内に収まるかは PoC 必要）

---

## 完了の定義

### スコープ定義段階（完了）
- [x] 現状の妥協点の整理（派生①〜③で踏んだ修正の質を分類）
- [x] 抹本対策案 A〜D の比較表
- [x] 推奨案の方向性提示（B + C 中長期）
- [x] **ユーザー判断**：実装フェーズに進む（案 B + 規約化 + 週次 random CI、案 C は Phase 21 へ分離）

### 実装フェーズ完了の定義
- [ ] Step 0：妥協点棚卸し完了（`@Transactional` 不在 6 クラス + scheduler-enabled 13 クラスの分類表作成）
- [ ] Step 1：AP-009 / TPL-009 を `ai_collaboration_antipatterns.md` / `prompt_templates.md` に双方向リンクで追加
- [ ] Step 1：`test_insights.md` カテゴリ 7-2 に PoC 非依存規約を追記
- [ ] Step 2：`FaultInjectionLogRepositoryTest` で案 B PoC 完了（自衛コード削除可否を判定・ローカル random 5 回 PASS）
- [ ] Step 3：`test_insights.md` カテゴリ 7-2 に cleanup.sql 規約を追記
- [ ] Step 4：案 B 全件適用（自衛コード除去・必要なクラスへの `@Sql` 適用）
- [ ] Step 4：ローカル `mvn test -Dsurefire.runOrder=random` ×5 連続 PASS
- [ ] Step 5：`weekly-test-random-order.yml` 新設・失敗時 Issue 自動起票確認
- [ ] Step 6：Phase 21 設計書スコープ定義作成（残妥協点の引継ぎ事項を明記）
- [ ] CI 本番デプロイで一連のワークフローが PASS

---

## 関連ドキュメント

- [051_ci_apply_scheduled_prices_test_h2_isolation_and_market_e2e_timeout.md](../../troubles/051_ci_apply_scheduled_prices_test_h2_isolation_and_market_e2e_timeout.md) — 直接の起因
- [test_insights.md](../../ai_context/test_insights.md) — カテゴリ 7-2 に規約追記対象
- [ai_collaboration_antipatterns.md](../../ai_context/ai_collaboration_antipatterns.md) — AP-009 候補の追加対象
- [phaseX-6_post_deploy_schema_healthcheck.md](phaseX-6_post_deploy_schema_healthcheck.md) — 同系列のメタ品質フェーズ（スキーマ層）
- [phaseX-7_ai_collaboration_antipatterns.md](phaseX-7_ai_collaboration_antipatterns.md) — 同系列のメタ品質フェーズ（AI 協働層）
