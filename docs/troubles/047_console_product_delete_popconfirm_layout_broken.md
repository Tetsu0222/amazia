# 047: Console 商品マスタの削除確認ポップアップのレイアウトが崩れる

## ステータス
✅ 解決済（2026-05-07）

## 発症箇所
- 画面：Amazia Console / 商品マスタ一覧
- URL：`http://<console-host>/products`
- コンポーネント：`amazia-console/resources/vue/src/features/products/pages/ProductList.vue`
- 操作：行ごとの「削除」ボタン押下時に表示される確認ポップオーバー（`a-popconfirm`）

## 症状
商品一覧の各行にある「削除」ボタンを押すと表示される `a-popconfirm` の中身が崩れて表示される。具体的には、

- タイトル「削除しますか？」だけがポップオーバー本体の左側にはみ出している
- 「キャンセル」「削除」の2ボタンが本来の横並びではなく縦に積まれて表示される
- 全体の枠（白い吹き出し）が画面右端寄りに極端に潰れた状態で開く

「削除」ボタン自体が画面右端にあり、Popconfirm のデフォルト配置（`top` センタリング）だとビューポート右辺にポップアップが押し出されて、内部要素が改行・縦積みになっていた。

## 根本原因
2点が重なっていた。

1. **Popconfirm の placement 未指定**：`a-popconfirm` は `placement` 未指定だと `top`（中央）配置となり、操作列が画面右端にあるテーブルでは popover の半分が画面外にはみ出る。Ant Design Vue は画面外に出る場合に位置補正を行うが、最小幅を確保しきれず内部 `.ant-popover-buttons` のボタンが折り返して縦積みになっていた。
2. **`@click.stop` の付与先が誤り**：行クリック展開（`expand-row-by-click`）と「削除」ボタンクリックの伝播を止める目的で `@click.stop` を `a-popconfirm` 自身に付けていた。Popconfirm はラッパーコンポーネントで実 DOM を持たないため `@click.stop` がスロット要素まで伝わらず、削除ポップアップを開く操作が行展開と混在して挙動が安定しなかった。

## なぜ CI で検知できなかったか
- ProductList.vue にコンポーネントテストが無く（フェーズ8 で UI テスト整備対象外）、表示レイアウトの回帰検知手段が無かった。
- Vitest／Jest による Popconfirm のレイアウト検証は実用上不可能（jsdom はレイアウトを計算しないため幅・配置の崩れは見えない）で、目視 E2E 以外では拾えない領域。

## 修正内容
`ProductList.vue` の行操作列の `a-popconfirm` を以下の方針で修正：

- `placement="topRight"` を明示し、削除ボタン上端の右揃えで開かせる（画面右端でも本体が画面内に確実に収まる）
- `@click.stop` を `a-popconfirm` ではなく内側の `<a-button>` に移動して、行展開イベントの抑制を確実にする

該当 diff：

```vue
<a-popconfirm
  title="削除しますか？"
  ok-text="削除"
  cancel-text="キャンセル"
  placement="topRight"
  @confirm="handleDelete(record.id)"
>
  <a-button size="small" danger @click.stop>削除</a-button>
</a-popconfirm>
```

## 再発防止
| 観点 | 対策 |
|------|------|
| Popconfirm 配置 | テーブル末尾の操作列で `a-popconfirm` を使うときは `placement="topRight"`（または `bottomRight`）を必ず指定する。デフォルトの `top` は画面右端で崩れる |
| イベント伝播停止 | `@click.stop` は実 DOM を持つ要素（`<a-button>` など）に付ける。Popconfirm/Popover/Tooltip など Vue ラッパーに付けても期待通りに伝播停止しない |
| UI 回帰観点 | 行展開（`expand-row-by-click`）と行内操作ボタン（編集／削除）を併用する画面は、操作ボタンの伝播停止と Popconfirm の配置を画面間で揃える。`SkuList` など同型画面（あれば）も同様の `placement` 指定を検討 |
