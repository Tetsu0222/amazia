import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import CategoryNav from './CategoryNav';

const navigateMock = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => navigateMock,
  };
});

describe('CategoryNav', () => {
  const CATEGORIES = ['すべて', 'ファッション', '家電', '食品・飲料', '本', 'ホビー', 'セール'];

  it('7カテゴリすべてが表示される', () => {
    render(<MemoryRouter><CategoryNav /></MemoryRouter>);
    CATEGORIES.forEach((label) => {
      expect(screen.getByRole('link', { name: label })).toBeInTheDocument();
    });
  });

  it('カテゴリリンクをクリックしても navigate が呼ばれない（ダミー）', async () => {
    const user = userEvent.setup();
    navigateMock.mockClear();
    render(<MemoryRouter><CategoryNav /></MemoryRouter>);
    await user.click(screen.getByRole('link', { name: 'ファッション' }));
    expect(navigateMock).not.toHaveBeenCalled();
  });
});
