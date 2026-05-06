# 036: MUI v9 移行漏れによるブラウザコンソールの警告（Grid v1構文 + Stack props 透過）

## ステータス
✅ 解決済（2026-05-06）

## 発症箇所
- 画面: Amazia Market 全画面（AppHeader / ProductList / Checkout / CheckoutComplete / PurchaseHistory / Login / MyPage）
- ブラウザコンソール: 開発モード時に React の DOM props 警告が連発
- 関連ファイル: 後述「修正内容」を参照

## 症状
ブラウザの開発者ツール Console に以下の警告が複数出力される：

```
React does not recognize the `alignItems` prop on a DOM element.
（AppHeader.jsx:25 の <Stack alignItems="center"> 等が起点）
```

```
Received `true` for a non-boolean attribute `item`.
（ProductList.jsx:34 の <Grid item xs={...}> が起点）
```

機能は正常に動作するが、コンソールが警告で埋まる／本来 React が DOM へ渡すべきでない props が漏れている状態。

## 根本原因（2 系統）

### 系統 A: MUI Grid v1 構文の廃止
**MUI v9 で Grid v2 が標準化され、Grid v1 構文が完全廃止**されたが、フェーズ9・10 時点で書かれた `<Grid item xs={12} sm={6} md={4}>` 構文が残ったまま MUI v9 にアップグレードされていた。

| | 旧（v1 / フェーズ9・10で記述） | 新（v9 / 移行後の正解） |
|---|------|------|
| 構文 | `<Grid item xs={12} sm={6} md={4}>` | `<Grid size={{ xs: 12, sm: 6, md: 4 }}>` |
| `item` props | 必須 | 廃止 |
| ブレークポイント | `xs` / `sm` / `md` を直接 | `size` オブジェクトで指定 |

該当ファイル: [ProductList.jsx](../../amazia-market/src/features/products/pages/ProductList.jsx)

### 系統 B: MUI Stack の `alignItems` / `justifyContent` を直接 props で渡せない
**MUI v9 の Stack は `alignItems` / `justifyContent` を直接 props として受け付けず**、`sx={{...}}` 経由で渡す仕様に変わっている。

`node_modules/@mui/system/Stack/createStack.js` を読むと、Stack 内部の分割代入では `direction` / `spacing` / `divider` / `children` / `className` / `useFlexGap` のみ取り出され、それ以外（`alignItems` / `justifyContent` 等）はすべて `...other` に入って StackRoot（`<div>`）にそのまま forward される実装：

```js
const {
  component = 'div',
  direction = 'column',
  spacing = 0,
  divider,
  children,
  className,
  useFlexGap = false,
  ...other       // ← alignItems / justifyContent はここに入る
} = themeProps;

return jsx(StackRoot, {
  as: component,
  ownerState,
  ref,
  className: clsx(classes.root, className),
  ...other,      // ← div に直接渡される
  children: ...
});
```

そのため `<Stack alignItems="center">` と書くと React 19 が「div は alignItems という DOM 属性を認識しない」と警告を出す。

該当ファイル（7箇所）:
- [AppHeader.jsx](../../amazia-market/src/components/AppHeader.jsx)
- [Login.jsx](../../amazia-market/src/features/customer/pages/Login.jsx)
- [MyPage.jsx](../../amazia-market/src/features/customer/pages/MyPage.jsx)
- [Checkout.jsx](../../amazia-market/src/features/checkout/pages/Checkout.jsx)（2箇所）
- [CheckoutComplete.jsx](../../amazia-market/src/features/checkout/pages/CheckoutComplete.jsx)
- [PurchaseHistory.jsx](../../amazia-market/src/features/orders/pages/PurchaseHistory.jsx)

### 警告のスタックトレースが AppHeader.jsx を指していたミスリード
`alignItems` の警告自体は **どの Stack でも一斉に出るが**、スタックトレースには「最初にレンダリングされた箇所」=トップレベルの AppHeader が表示されていたため、AppHeader が原因に見えた。実際には全 7 箇所の Stack が同じ警告を生成していた。

## なぜ CI で検知できなかったか
- React の DOM props 警告は **`console.error` 出力のみで実行は失敗しない**ため、Vitest や React Testing Library の通常アサーションでは fail にならない。
- Vitest 出力をフィルタしても警告ログが出ていなかった（環境差）：本件は `npx vitest run` 出力を `grep -i "alignItems"` してもヒットしなかったが、ブラウザの React 19 ランタイムでは警告が出ていた。テスト環境（jsdom + React Testing Library）と実ブラウザ環境で **警告チェックの厳しさが異なる** のが理由。
- 警告を検出する Vitest の設定（`@testing-library/react` の `console.error` を fail に昇格させる設定）は **本プロジェクトに導入されていない**。
- ローカル開発時にブラウザを開いて Console タブを目視確認すれば見つかったが、フェーズ9・10 のレビュー段階ではコンソール警告までは確認していなかった。

## なぜ再発／放置されたか（後日分析用メモ）

> 本件はフェーズ9・10 から phase14 まで複数フェーズを跨いで放置された。後で原因分析する際の手がかりを以下に列挙：

### 仮説 1: トラブル文書蓄積が浅かった時期の見落とし
- フェーズ10 当時のトラブル文書は 014 番台（`014_sku_price_ui_not_implemented.md` 等）。**「ブラウザコンソール警告の確認」という観点が `test_insights.md` に明文化される前**だった可能性が高い。
- 確認方法: `git log --follow docs/ai_context/test_insights.md` で、コンソール警告チェックが追加された時期を確認する。

### 仮説 2: MUI バージョンアップ時の手順漏れ
- `@mui/material` を `^7` 系から `^9.0.0` に上げた commit を特定し、その PR で Grid v2 移行を意識したかどうか確認。
- 確認方法: `git log --all -p -- amazia-market/package.json | grep -B 2 -A 2 '@mui/material'` で `@mui` のバージョン変遷を追う。

### 仮説 3: 影響範囲が小さく見えた
- Grid v1 → v2 の影響を受けるのは ProductList.jsx 1 ファイルだけだったため、移行漏れの広がりが小さく、優先度が下がった可能性。
- 確認方法: 過去のコミットで `<Grid item` 構文を grep し、何ファイルあったか／いつ書かれたかを確認。

## 修正内容

### 系統 A: Grid v2 構文化
[ProductList.jsx:34](../../amazia-market/src/features/products/pages/ProductList.jsx#L34) を MUI Grid v2 構文に変更：

```diff
- <Grid item xs={12} sm={6} md={4} key={p.productId}>
+ <Grid size={{ xs: 12, sm: 6, md: 4 }} key={p.productId}>
```

`grep -r "Grid item\|Grid xs\|Grid sm\|Grid md" amazia-market/src` で他箇所に Grid v1 構文がないことも確認済み（0 件）。

### 系統 B: Stack の alignItems / justifyContent を sx 化（7 箇所）

例：
```diff
- <Stack direction="row" spacing={1} alignItems="center">
+ <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
```

```diff
- <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
+ <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
```

修正ファイル一覧：
- [AppHeader.jsx](../../amazia-market/src/components/AppHeader.jsx)
- [Login.jsx](../../amazia-market/src/features/customer/pages/Login.jsx)
- [MyPage.jsx](../../amazia-market/src/features/customer/pages/MyPage.jsx)
- [Checkout.jsx](../../amazia-market/src/features/checkout/pages/Checkout.jsx)（2箇所）
- [CheckoutComplete.jsx](../../amazia-market/src/features/checkout/pages/CheckoutComplete.jsx)
- [PurchaseHistory.jsx](../../amazia-market/src/features/orders/pages/PurchaseHistory.jsx)

確認 grep（0件のはず）:
```
grep -nE 'Stack[^>]*alignItems=' amazia-market/src
grep -nE 'Stack[^>]*justifyContent=' amazia-market/src
```

Vitest 49/49 グリーン（Stack の表示崩れなし）。

## 再発防止
| 観点 | 対策 |
|------|------|
| ライブラリ移行時のチェックリスト化 | MUI / React Router / その他 UI ライブラリのメジャーバージョンアップ時、**Migration Guide を確認した上で `git grep` ベースで旧構文を検索**する手順を `docs/ai_context/` に整備する。 |
| ブラウザコンソール警告の検知 | ローカル確認時に「ブラウザ Console タブに警告が出ていないか」を目視確認するチェックを `test_insights.md` のリリース前チェックリストに追加。 |
| Vitest での警告昇格（将来課題） | `@testing-library/react` のオプションで `console.error` をテスト失敗に昇格させる設定を検討。ただし MUI が出す既存の deprecation 警告と競合しないか調査が必要。 |
| トラブル文書の蓄積を時系列で振り返る | 本件のように **複数フェーズを跨いで放置されたバグ**は、その時期の `docs/troubles/` 蓄積量・`test_insights.md` 観点充実度と相関する可能性が高い。定期的に「過去の見落としパターンが今は防げる体制になっているか」をメタ評価する。 |

## 参考
- 本件で参照した Market ファイル: [ProductList.jsx](../../amazia-market/src/features/products/pages/ProductList.jsx)
- 関連設計書: なし（純粋に UI ライブラリの移行漏れ）
- MUI Migration Guide: https://mui.com/material-ui/migration/upgrade-to-grid-v2/
- 関連トラブル: [035 Market 購入ボタンで sku_id=undefined](035_market_checkout_sku_id_undefined.md)（同じく phase14 Step B-2 動作確認時に発見、別原因）

## 後日分析タスク（メタ評価のため）

> ユーザー要望: 「フェーズ10当時のトラブル文書蓄積でこのバグが防げたか」を評価したい

- [ ] フェーズ10 完了時点の `docs/troubles/` 蓄積数を確認（git log で 014 番台までだったか）
- [ ] フェーズ10 完了時点の `test_insights.md` に「ブラウザコンソール警告の目視確認」観点があったか確認
- [ ] `@mui/material` のバージョンアップ commit を特定し、Grid v2 移行を意識した形跡があったか確認
- [ ] phase14 で発見できた要因（Step B-2 でブラウザを開いて目視確認した）が、現在の知見蓄積の結果なのか単なる偶然なのかを切り分ける
