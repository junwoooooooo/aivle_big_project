import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import MarketInterviewResult from './MarketInterviewResult.jsx';

const result = {
  source: { marketSeedSnapshotId: 'seed-3', selectionRevision: 4, bmPlanRevision: 2 },
  targeting: { criteriaText: '직장인', requestedSampleSize: 20, attemptedCount: 20, usableCount: 18, targetCount: 14, nonTargetCount: 4 },
  codedInterviewCount: 17, codingFailureCount: 1,
  comprehension: { accurate: 10, partial: 5, misunderstood: 2, unclassified: 1 },
  differentiation: { different: 8, similar: 4, unclear: 5, unclassified: 1 },
  themes: [{ axis: 'BARRIER', title: '시간 부담', description: '시간을 먼저 확인', participantIds: ['R1'],
    mentionCount: 9, targetCount: 7, nonTargetCount: 2, quote: '저녁 시간이 맞아야 해요.' }],
  participants: [{ participantId: 'R1', label: '응답자 1', profile: '41세 · 직장인 · 경기', context: '퇴근 후 운동', needs: ['시간 조율'], group: 'TARGET' }],
  interviews: [{ participantId: 'R1', questions: [
    { question: '가장 걱정되는 점은?', answer: '일정이 맞지 않을까 걱정됩니다.', uncertainty: '실제 일정 확인' },
    { question: '현재는 어떻게 하나요?', answer: '혼자 운동합니다.', uncertainty: '빈도 확인' },
    { question: '언제 사용하나요?', answer: '퇴근 뒤 사용합니다.', uncertainty: '상황 확인' },
    { question: '무엇을 바꾸고 싶나요?', answer: '시간 선택을 늘리고 싶습니다.', uncertainty: '요구 확인' },
  ] }],
};

describe('MarketInterviewResult', () => {
  it('요약 → theme → 실제 응답자 → 전체 답변 traceability를 제공한다', () => {
    render(<MarketInterviewResult result={result} />);
    expect(screen.getByRole('heading', { name: '반복 패턴을 원문 근거와 함께 확인하세요' })).toBeInTheDocument();
    expect(screen.getByText(/18명 중 근거 응답 9명/)).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: /시간 부담/ }).at(-1));
    expect(screen.getByText((_, element) => element?.classList?.contains('market-interview__filter-note')
      && element.textContent.includes('시간 부담의 원문 근거가 있는 응답자'))).toBeInTheDocument();
    fireEvent.click(screen.getByText(/나머지 답변 1개 보기/));
    expect(screen.getByText('혼자 운동합니다.')).toBeInTheDocument();
  });

  it('세부 분류와 실행 lineage를 접어서 제공하고 응답자는 한 명만 펼친다', () => {
    render(<MarketInterviewResult result={result} run={{ attempt: 2 }} />);
    expect(screen.getByRole('heading', { name: '대표 응답자와 전체 원문' })).toBeInTheDocument();
    expect(screen.getAllByText('응답자 1')).toHaveLength(2);
    fireEvent.click(screen.getByText('세부 이해도와 차별점 보기'));
    expect(screen.getByRole('heading', { name: '세부 이해도' })).toBeInTheDocument();
    expect(screen.getAllByText('코딩 제외').length).toBeGreaterThanOrEqual(2);
    fireEvent.click(screen.getByText(/실행 기록·다양성/));
    expect(screen.getByText('seed-3')).toBeInTheDocument();
    expect(screen.getByText('2회')).toBeInTheDocument();
  });

  it('명시되지 않은 구매의향을 생성하지 않는다', () => {
    const { container } = render(<MarketInterviewResult result={result} />);
    expect(container.textContent).not.toMatch(/해결되면 구매|구매할 것이다|사겠대요/);
  });

  it('theme과 respondent가 없는 완료 payload를 빈 상태로 표시한다', () => {
    render(<MarketInterviewResult result={{ targeting: { usableCount: 0 } }} />);
    expect(screen.getByRole('heading', { name: '표시할 반복 인사이트가 없습니다' })).toBeInTheDocument();
    expect(screen.getByText('조건에 맞는 응답자가 없습니다.')).toBeInTheDocument();
  });
});
