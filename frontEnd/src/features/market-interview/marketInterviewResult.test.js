import { describe, expect, it } from 'vitest';

import {
  ANSWER_VIEW,
  AXIS_VIEW,
  mentionText,
  normalizeMarketInterview,
  priceText,
  profileLines,
  renderBoard,
} from './marketInterviewResult.js';

function deepResult() {
  return {
    usableInterviewCount: 20,
    codedInterviewCount: 19,
    codingFailureCount: 1,
    conceptBoard: {
      conceptName: '예약 도우미', targetUsers: '서울 매장', problemScenario: '예약 누락',
      featureSet: ['예약 확인'], differentiators: '누락 방지', priceKrw: 9900,
    },
    targeting: {
      criteriaText: '서울 매장', requestedSampleSize: 20, drawnSampleSize: 20,
      targetRequested: 16, targetCount: 16, nonTargetCount: 4,
    },
    participants: Array.from({ length: 20 }, (_, index) => ({
      participantId: `R${String(index + 1).padStart(3, '0')}`,
      profile: { age: 30 + index, gender: index % 2 ? '여성' : '남성', region: '서울' },
      group: index < 16 ? 'TARGET' : 'COMPARISON',
    })),
    interviews: Array.from({ length: 20 }, (_, index) => ({
      participantId: `R${String(index + 1).padStart(3, '0')}`,
      questions: ANSWER_VIEW.map((item) => ({ question: item.label, answer: `${item.label} 실제 답변` })),
    })),
    themes: [
      { axis: 'BARRIER', title: '도입 시간', participantIds: ['R001'], mentionCount: 1,
        targetCount: 1, nonTargetCount: 0, quote: '안 산다면 실제 답변' },
      { axis: 'LIKE', title: '누락 방지', participantIds: ['R002'], mentionCount: 1,
        targetCount: 1, nonTargetCount: 0, quote: '끌리는 점 실제 답변' },
    ],
    comprehension: { accurate: 18, partial: 1, misunderstood: 0, unclassified: 1 },
    differentiation: { different: 10, similar: 5, unclear: 4, unclassified: 1 },
    codingTrace: Array.from({ length: 20 }, (_, index) => ({
      participantId: `R${String(index + 1).padStart(3, '0')}`,
      comprehension: index === 19 ? 'unclassified' : 'accurate',
      alternativeLabel: index < 2 ? '수기' : '',
    })),
    transcriptProvenance: Array.from({ length: 20 }, (_, index) => ({
      participantId: `R${String(index + 1).padStart(3, '0')}`,
    })),
    limitations: ['실제 고객 조사 결과가 아닙니다.'],
    saturation: { saturatedThemes: [] },
  };
}

describe('FULL deep-engine → MAIN presentation adapter', () => {
  const result = normalizeMarketInterview(deepResult());

  it('20 usable / 19 coded 결과를 성공 화면용 view model로 보존한다', () => {
    expect(result.answered).toBe(20);
    expect(result.transcripts).toHaveLength(20);
    expect(result.interviews.length).toBeLessThanOrEqual(5);
    expect(result.sections.map((section) => section.axis)).toEqual(AXIS_VIEW.map((item) => item.axis));
  });

  it('FULL title/participantIds와 codingTrace 대안을 결정론적으로 매핑한다', () => {
    const barrier = result.sections.find((section) => section.axis === 'BARRIER').themes[0];
    expect(barrier.label).toBe('도입 시간');
    expect(barrier.respondentIds).toEqual(['R001']);
    expect(barrier.resolvedCount).toBeNull();
    expect(result.alternatives[0]).toEqual({ label: '수기', mentionCount: 2 });
  });

  it('없는 resolved count나 백분율을 만들지 않는다', () => {
    expect(result.headline.barrier.resolved).toBeNull();
    expect(JSON.stringify(result)).not.toMatch(/percent|percentage|pct/i);
  });

  it('실제 stimulus board를 화면에 그대로 보존한다', () => {
    expect(result.board).toMatchObject(deepResult().conceptBoard);
    expect(renderBoard(result.board)).toContain('이름: 예약 도우미');
    expect(renderBoard(result.board)).toContain('가격: 9,900원');
  });

  it('주제 0개도 원문을 보존하고 분류 없음 안내를 만든다', () => {
    const raw = deepResult();
    raw.themes = [];
    const empty = normalizeMarketInterview(raw);
    expect(empty.transcripts).toHaveLength(20);
    expect(empty.sections.every((section) => section.themes.length === 0)).toBe(true);
    expect(empty.sections.every((section) => section.empty.includes('분류된 답이 없어요'))).toBe(true);
  });
});

describe('MAIN presentation helpers', () => {
  it('언급 수는 백분율이 아니라 n명 중 x명이다', () => {
    expect(mentionText(7, 20)).toBe('20명 중 7명');
    expect(mentionText(7, 20)).not.toContain('%');
  });

  it('가격과 프로필의 없는 값은 지어내지 않는다', () => {
    expect(priceText(null)).toBe('아직 정하지 않음');
    expect(profileLines({ age: null, gender: '여성', region: '서울' }).head).toBe('여성 · 서울');
  });
});
