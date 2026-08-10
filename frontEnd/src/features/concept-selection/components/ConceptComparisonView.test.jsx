import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import ConceptCard from './ConceptCard.jsx';
import ConceptComparisonTable from './ConceptComparisonTable.jsx';

const model = (id, title) => ({
  conceptId: id, slotNumber: Number(id), title, summary: `${title} 설명`, legalStatusLabel: '구현 가능', tags: ['B2B 중심'],
  differentiator: '차별점', targetCustomer: '기업', operatingModel: '직영', revenueModel: '구독', requiredControls: '고지', risks: '수요',
  coreProblem: '문제', coreValue: '가치', userFlow: '신청 · 이용', platformRole: '중개', features: '예약', operatingDifficulty: '낮음',
  partnerDependency: '해당 없음', initialScope: '지역 시작',
});

describe('concept selection card and compare view', () => {
  it('exposes compare/preferred controls and renders comparison rows without a score or ranking', () => {
    const onToggle = vi.fn();
    const onPrefer = vi.fn();
    render(<><ConceptCard model={model('1', '첫 컨셉')} compared preferred={false} compareDisabled={false} onToggleCompare={onToggle} onPrefer={onPrefer} onDetails={vi.fn()} />
      <ConceptComparisonTable models={[model('1', '첫 컨셉'), model('2', '둘째 컨셉')]} /></>);

    fireEvent.click(screen.getByRole('checkbox', { name: '비교 대상' }));
    fireEvent.click(screen.getByRole('radio', { name: '선택 후보로 표시' }));
    expect(onToggle).toHaveBeenCalledWith('1');
    expect(onPrefer).toHaveBeenCalledWith('1');
    expect(screen.getByRole('rowheader', { name: '대상 고객' })).toBeInTheDocument();
    expect(screen.queryByText(/종합 점수|자동 1위/)).not.toBeInTheDocument();
  });
});
