import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ImageGallery from './ImageGallery';

describe('ImageGallery', () => {
  it('画像配列が空のとき NOIMAGE プレースホルダー画像を表示', () => {
    render(<ImageGallery images={[]} alt="x" />);
    expect(screen.getByTestId('image-gallery-main')).toBeInTheDocument();
  });

  it('placeholder 指定時はプレースホルダーテキストを表示', () => {
    render(<ImageGallery placeholder="色とサイズを選択してください" />);
    expect(screen.getByText('色とサイズを選択してください')).toBeInTheDocument();
    expect(screen.queryByTestId('image-gallery-main')).not.toBeInTheDocument();
  });

  it('複数画像時、サムネイルクリックでメイン画像が切替', async () => {
    const user = userEvent.setup();
    const images = ['/img/1.png', '/img/2.png', '/img/3.png'];
    render(<ImageGallery images={images} alt="x" />);

    expect(screen.getByTestId('image-gallery-main')).toHaveAttribute('src', '/img/1.png');

    await user.click(screen.getByTestId('image-gallery-thumb-2'));
    expect(screen.getByTestId('image-gallery-main')).toHaveAttribute('src', '/img/3.png');
  });

  it('画像が1枚のときサムネイルバーは表示しない', () => {
    render(<ImageGallery images={['/img/only.png']} alt="x" />);
    expect(screen.queryByTestId('image-gallery-thumb-0')).not.toBeInTheDocument();
  });
});
