import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ResolvedRevisionCard, RevisionRequestCard } from './RevisionRequestCard.jsx';

const REQUEST = {
  id: 1,
  category: 'ADVERTISING_AND_MARKETING',
  anchorSectionCode: 'PRODUCT_SERVICE',
  anchorQuote: '악취 30% 개선을 핵심 광고 카피로 사용한다.',
  rationale: '실증 자료 없는 수치 광고는 표시광고법 위반 소지가 있습니다.',
  suggestions: [
    { id: 11, label: 'A', newText: '공인기관 실증 시험 완료 후 광고를 개시한다.' },
    { id: 12, label: 'B', newText: '수치를 제외하고 탈취 성능 강화 설계로 표기한다.' },
  ],
};

describe('RevisionRequestCard', () => {
  it('선택한 수정안으로 승인 핸들러를 정확히 1회 호출한다 — 자동 재검토 경로 없음', () => {
    const onAccept = vi.fn();
    const onDismiss = vi.fn();
    render(<RevisionRequestCard request={REQUEST} onAccept={onAccept} onDismiss={onDismiss} />);

    expect(screen.getByText(/광고·마케팅 수정 요청/)).toBeInTheDocument();
    expect(screen.getByText(REQUEST.anchorQuote)).toBeInTheDocument();
    expect(screen.getByText(/자동 반영되지 않습니다/)).toBeInTheDocument();

    // B안 선택 후 적용
    fireEvent.click(screen.getAllByRole('radio')[1]);
    fireEvent.click(screen.getByRole('button', { name: '이 수정안 적용' }));

    expect(onAccept).toHaveBeenCalledTimes(1);
    expect(onAccept).toHaveBeenCalledWith(1, 12);
    expect(onDismiss).not.toHaveBeenCalled();
  });

  it('무시 버튼은 dismiss 핸들러만 호출한다', () => {
    const onAccept = vi.fn();
    const onDismiss = vi.fn();
    render(<RevisionRequestCard request={REQUEST} onAccept={onAccept} onDismiss={onDismiss} />);
    fireEvent.click(screen.getByRole('button', { name: '무시' }));
    expect(onDismiss).toHaveBeenCalledWith(1);
    expect(onAccept).not.toHaveBeenCalled();
  });
});

describe('ResolvedRevisionCard', () => {
  it('해결된 요청은 삭제되지 않고 "v{n}에서 해결" 라벨로 남는다', () => {
    render(<ResolvedRevisionCard request={{ ...REQUEST, resolvedInVersion: 2 }} />);
    expect(screen.getByText('v2에서 해결')).toBeInTheDocument();
    expect(screen.getByText(REQUEST.anchorQuote)).toBeInTheDocument();
  });
});
