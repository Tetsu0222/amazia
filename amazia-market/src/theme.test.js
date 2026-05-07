import { describe, it, expect } from 'vitest';
import theme from './theme';

describe('theme', () => {
  it('ヘッダー濃紺色が #0F1A2B である', () => {
    expect(theme.palette.header.main).toBe('#0F1A2B');
  });

  it('サブヘッダー色が #1B2838 である', () => {
    expect(theme.palette.header.sub).toBe('#1B2838');
  });

  it('ヘッダー文字色が白である', () => {
    expect(theme.palette.header.text).toBe('#FFFFFF');
  });

  it('ヘッダーホバー枠色が白である', () => {
    expect(theme.palette.header.hoverBorder).toBe('#FFFFFF');
  });

  it('アクセント（CTA）色が山吹色 #F0A93B である', () => {
    expect(theme.palette.accent.main).toBe('#F0A93B');
  });

  it('アクセント文字色がヘッダー濃紺と同じである', () => {
    expect(theme.palette.accent.contrastText).toBe('#0F1A2B');
  });

  it('価格・リンク色が #0066C0 である', () => {
    expect(theme.palette.link.main).toBe('#0066C0');
  });

  it('カードボーダー色が #DDDDDD である', () => {
    expect(theme.palette.border.card).toBe('#DDDDDD');
  });

  it('背景色が #EAEDED である', () => {
    expect(theme.palette.background.default).toBe('#EAEDED');
  });

  it('カード背景色が白である', () => {
    expect(theme.palette.background.paper).toBe('#FFFFFF');
  });

  it('既存 primary は MUI 互換のため青系を維持している', () => {
    expect(theme.palette.primary.main).toBe('#1976d2');
  });
});
