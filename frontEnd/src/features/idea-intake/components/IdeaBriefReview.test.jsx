import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import IdeaBriefReview from './IdeaBriefReview.jsx';
import { createIdeaIntakeDraft, draftFromIdeaBrief } from '../model/ideaIntakeModel.js';

describe('AI 해석 확인', () => {
  it('사용자 입력과 AI 해석을 다른 출처로 표시하고 해석 수정을 허용한다', () => {
    const draft = draftFromIdeaBrief({
      fields: [
        { fieldKey: 'ideaOverview', value: '개요', decisionState: 'LOCKED', provenance: 'USER_INPUT' },
        { fieldKey: 'problem', value: '폐기 문제', decisionState: 'LOCKED', provenance: 'USER_INPUT' },
        { fieldKey: 'targetUsers', value: '지역 식당', decisionState: 'LOCKED', provenance: 'USER_INPUT' },
      ],
      safetyReview: { decision: 'ALLOW', restrictions: [], userFacingReason: '지원 가능한 아이디어입니다.' },
      interpretation: {
        interpretedProblem: '음식물 폐기 문제', interpretedTargetUsers: '지역 식당',
        usageContext: '영업 종료 후', industryCategory: '폐기물 관리', researchScope: '수거 서비스',
        conciseIdeaDefinition: '폐기를 줄이는 서비스', targetRegionInterpretation: '',
        relevantKnownCompetitorContext: '',
      },
      userFacingSummary: '입력하신 아이디어를 이렇게 이해했습니다.',
    }, createIdeaIntakeDraft());
    const onChange = vi.fn();
    render(<IdeaBriefReview draft={draft} onInterpretationChange={onChange} onConfirm={vi.fn()} />);
    expect(screen.getByRole('heading', { name: '내가 입력한 내용' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'AI가 정리한 내용' })).toBeInTheDocument();
    expect(screen.getByText('아이디어 진행 가능 여부')).toBeInTheDocument();
    expect(screen.getByText('다음 단계로 진행할 수 있습니다.')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('AI가 이해한 문제'), { target: { value: '수정한 문제' } });
    expect(onChange).toHaveBeenCalledWith('interpretedProblem', '수정한 문제');
  });

  it('원문 결정 후보를 direct 입력과 구분하고 확인·수정·open 액션을 제공한다', () => {
    const draft = draftFromIdeaBrief({
      fields: [
        { fieldKey: 'ideaOverview', value: '월 9,900원 구독 서비스', decisionState: 'LOCKED', provenance: 'USER_INPUT' },
        { fieldKey: 'problem', value: '문제', decisionState: 'LOCKED', provenance: 'USER_INPUT' },
        { fieldKey: 'targetUsers', value: '사용자', decisionState: 'LOCKED', provenance: 'USER_INPUT' },
      ],
      interpretation: {
        interpretedProblem: '문제', interpretedTargetUsers: '사용자', usageContext: '맥락',
        industryCategory: '업종', researchScope: '범위', conciseIdeaDefinition: '정의',
        targetRegionInterpretation: '', relevantKnownCompetitorContext: '',
        commitmentCandidates: [{
          fieldKey: 'price', value: '월 9,900원', evidenceQuote: '월 9,900원 구독',
          source: 'AI_DERIVED', origin: 'USER_TEXT', authority: 'REVIEWABLE',
        }],
      },
    }, createIdeaIntakeDraft());
    const onValue = vi.fn();
    const onAction = vi.fn();
    render(<IdeaBriefReview draft={draft} onInterpretationChange={vi.fn()}
      onCommitmentValueChange={onValue} onCommitmentAction={onAction} onConfirm={vi.fn()} />);
    expect(screen.getByText('AI가 원문에서 찾은 결정사항')).toBeInTheDocument();
    expect(screen.getByText('확인 필요')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('가격 결정 후보'), { target: { value: '월 10,900원' } });
    expect(onValue).toHaveBeenCalledWith('price', '월 10,900원');
    fireEvent.click(screen.getByRole('button', { name: '아직 정하지 않음' }));
    expect(onAction).toHaveBeenCalledWith('price', 'RETURN_TO_OPEN');
  });

  it('확정 뒤에도 같은 review workspace를 유지하고 읽기 전용과 실제 다음 행동을 표시한다', () => {
    const draft = draftFromIdeaBrief({
      fields: [{ fieldKey: 'ideaOverview', value: '예약 서비스', decisionState: 'LOCKED', provenance: 'USER_INPUT' }],
      safetyReview: { decision: 'ALLOW', restrictions: [], userFacingReason: '진행할 수 있습니다.' },
      interpretation: { interpretedProblem: '예약 누락', interpretedTargetUsers: '상점', usageContext: '', industryCategory: '', researchScope: '', conciseIdeaDefinition: '', targetRegionInterpretation: '', relevantKnownCompetitorContext: '' },
    }, createIdeaIntakeDraft());
    render(<MemoryRouter><IdeaBriefReview draft={draft} projectId="41" confirmed onEdit={vi.fn()} /></MemoryRouter>);
    expect(screen.getByText('아이디어 정리가 완료되었습니다.')).toBeInTheDocument();
    expect(document.querySelector('.idea-review-workspace')).toBeInTheDocument();
    expect(screen.queryByLabelText('AI가 이해한 문제')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: '사업안 생성 및 검토로 이동 →' })).toHaveAttribute('href', '/app/projects/41/concepts');
  });
});
