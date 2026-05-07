// Backend / DB のいずれで空白が混入したかの調査が完了するまでの暫定対応。
// 除去後が元の半分以下に縮む場合（＝意味のある区切り空白の可能性が高い）は元の値を返す保守的ロジック。
export function normalizeName(raw) {
  if (!raw) return raw;
  const stripped = raw.replace(/\s+/g, '');
  return stripped.length * 2 <= raw.length ? stripped : raw;
}
