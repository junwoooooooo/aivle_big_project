import { describe, expect, it } from 'vitest';
import { marketInterviewDashboard } from './marketInterviewDashboard.js';

const payload = {
  targeting: { usableCount: 18 },
  themes: [
    { axis: 'BARRIER', title: '설정 부담', participantIds: ['R1', 'R2'], mentionCount: 2,
      targetCount: 1, nonTargetCount: 1, quote: '설정 시간이 부담됩니다.' },
    { axis: 'BARRIER', title: '가격 부담', participantIds: ['R3', 'R4'], mentionCount: 2,
      targetCount: 2, nonTargetCount: 0, quote: '가격을 먼저 봅니다.' },
    { axis: 'SUGGESTION', title: '설정 지원', participantIds: ['R1'], mentionCount: 1,
      targetCount: 1, nonTargetCount: 0, quote: '초기 설정을 도와주세요.' },
  ],
  codingTrace: [
    { participantId: 'R1', group: 'TARGET', alternativeLabel: '직접 전화' },
    { participantId: 'R2', group: 'COMPARISON', alternativeLabel: '직접 전화' },
  ],
  participants: [{ participantId: 'R1', label: '응답자 1', group: 'TARGET' }],
  interviews: [{ participantId: 'R1', questions: [{ question: '현재 대안은?', answer: '직접 전화합니다.' }] }],
};

describe('marketInterviewDashboard', () => {
  it('원래 count·quote·participant 연결을 보존하고 동점은 기존 순서로 결정한다', () => {
    const view = marketInterviewDashboard(payload);
    expect(view.headlines[0]).toMatchObject({ label: '안 사는 이유', title: '설정 부담', count: 2, total: 18 });
    expect(view.sections[0].themes[0]).toMatchObject({ quote: '설정 시간이 부담됩니다.',
      participantIds: ['R1', 'R2'], targetCount: 1, nonTargetCount: 1 });
    expect(view.participants[0].interview.questions[0].answer).toBe('직접 전화합니다.');
  });

  it('coding trace에 명시된 대안만 안정적으로 집계한다', () => {
    const view = marketInterviewDashboard(payload);
    expect(view.headlines.find((item) => item.label === '지금 쓰는 것')).toMatchObject({ title: '직접 전화', count: 2 });
  });

  it('partial payload를 빈 상태로 안전하게 변환하며 구매의향 문구를 만들지 않는다', () => {
    const view = marketInterviewDashboard({ targeting: { usableCount: 0 }, themes: null, participants: null });
    expect(view.headlines).toEqual([]);
    expect(JSON.stringify(view)).not.toMatch(/해결되면 구매|구매할 것이다|사겠대요/);
  });
});
