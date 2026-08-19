import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import InterviewCard from './InterviewCard.jsx';
import { InterviewFootnote } from './MarketInterviewPage.jsx';

const card = {
  key: 'accurate-R001',
  comprehension: 'accurate',
  profile: { age: 37, gender: '여성', household: null, region: '서울', income: null, job: '매장 운영' },
  answers: [
    { key: 'firstImpression', label: '첫인상', value: '예약 누락을 줄이는 도구로 보입니다.' },
    { key: 'barrier', label: '안 산다면', value: '도입 시간이 길면 어렵습니다.' },
  ],
};

describe('InterviewCard', () => {
  it('프로필과 실제 응답을 함께 보인다', () => {
    render(<InterviewCard card={card} />);
    expect(screen.getByText(/37세 · 여성 · 서울/)).toBeInTheDocument();
    expect(screen.getByText('첫인상')).toBeInTheDocument();
    expect(screen.getAllByRole('definition')).toHaveLength(2);
  });

  it('이해도 배지를 응답과 함께 보인다', () => {
    render(<InterviewCard card={{ ...card, comprehension: 'misunderstood' }} />);
    expect(screen.getByText('다른 물건으로 이해')).toBeInTheDocument();
  });
});

describe('InterviewFootnote', () => {
  it('결과가 없어도 가상 고객 정성 탐색 면책은 남는다', () => {
    render(<InterviewFootnote result={null} />);
    expect(screen.getByText(/한국미디어패널조사\(KISDI\)/)).toBeInTheDocument();
    expect(screen.getByText(/백분율로 환산하지 마/)).toBeInTheDocument();
  });

  it('서버 경계 문구를 그대로 편다', () => {
    render(<InterviewFootnote result={{ caveats: ['실제 고객 조사 결과가 아닙니다.'] }} />);
    expect(screen.getByText('이 결과를 읽는 법 1가지')).toBeInTheDocument();
    expect(screen.getByText('실제 고객 조사 결과가 아닙니다.')).toBeInTheDocument();
  });
});
