import { Badge } from '@mui/material';

/**
 * フェーズ19: 未読数バッジ。`count === 0` のときは children のみ（バッジなし）を返す。
 *
 * @param {{ count: number, color?: string, children?: React.ReactNode }} props
 */
export default function UnreadBadge({ count, color = 'default', children }) {
  if (!count || count <= 0) return children ?? null;
  return (
    <Badge
      badgeContent={count}
      color={color}
      overlap="circular"
      max={99}
    >
      {children}
    </Badge>
  );
}
