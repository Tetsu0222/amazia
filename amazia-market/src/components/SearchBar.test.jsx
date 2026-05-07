import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import SearchBar from './SearchBar';

const navigateMock = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => navigateMock };
});

function renderBar(initialPath = '/') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <SearchBar />
    </MemoryRouter>
  );
}

describe('SearchBar', () => {
  beforeEach(() => navigateMock.mockClear());

  it('エンターで /?q=… に遷移する', async () => {
    const user = userEvent.setup();
    renderBar();
    await user.type(screen.getByLabelText('商品検索'), 'テスト{Enter}');
    expect(navigateMock).toHaveBeenCalledWith('/?q=' + encodeURIComponent('テスト'));
  });

  it('検索ボタンクリックでも /?q=… に遷移する', async () => {
    const user = userEvent.setup();
    renderBar();
    await user.type(screen.getByLabelText('商品検索'), 'クリック');
    await user.click(screen.getByRole('button', { name: '検索' }));
    expect(navigateMock).toHaveBeenCalledWith('/?q=' + encodeURIComponent('クリック'));
  });

  it('空キーワードで送信すると / に遷移する', async () => {
    const user = userEvent.setup();
    renderBar();
    await user.click(screen.getByRole('button', { name: '検索' }));
    expect(navigateMock).toHaveBeenCalledWith('/');
  });

  it('URL の q を初期値として表示する', () => {
    renderBar('/?q=' + encodeURIComponent('初期値'));
    expect(screen.getByLabelText('商品検索')).toHaveValue('初期値');
  });
});
