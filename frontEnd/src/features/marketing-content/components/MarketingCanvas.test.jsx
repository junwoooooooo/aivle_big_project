import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import MarketingCanvas from './MarketingCanvas.jsx';

const result = {
  contentType: 'SOCIAL_POST', title: '출시 소식', body: '새 제품을 소개합니다.',
  callToAction: '자세히 보기', hashtags: [], legalReview: { warnings: [] },
};
const style = { accent: '#0f8878', scale: '1', align: 'LEFT', theme: 'DARK' };

describe('MarketingCanvas', () => {
  it('renders copy safely before an image exists', () => {
    render(<MarketingCanvas result={result} style={style} />);
    expect(screen.getByText('출시 소식')).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('renders the generated artifact with the copy in one preview', () => {
    render(<MarketingCanvas result={result} style={style} artifactUrl="https://example.test/generated.jpg" />);
    expect(screen.getByRole('img', { name: '출시 소식 마케팅 이미지' })).toHaveAttribute(
      'src', 'https://example.test/generated.jpg');
    expect(screen.getByText('새 제품을 소개합니다.')).toBeInTheDocument();
  });
});
