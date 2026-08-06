import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { ConceptWorkboard } from './ConceptWorkboard.jsx';

const brief = { version: 3, hash: 'sha256:brief', fields: [{ fieldKey: 'problem', value: '폐기물 문제' }] };
const boundary = { version: { versionNumber: 2, regulatoryBoundaryHash: 'sha256:boundary', rules: [{ ruleId: 'r1', title: '허가 파트너 이용' }] } };
const batch = { batchId: 7, jobId: 'job-7', status: 'GENERATING', confirmedBriefVersionId: 10,
  briefHash: 'sha256:brief', regulatoryBoundaryVersionId: 20, boundaryHash: 'sha256:boundary', stale: false, retryable: false };
const slot = (slotIndex, status = 'GENERATING') => ({ slotId: slotIndex + 1, slotIndex,
  variationFocus: ['TARGET_AND_USER_EXPERIENCE', 'OPERATING_MODEL_AND_PARTNERS', 'REVENUE_AND_CHANNELS'][slotIndex],
  status, currentPhase: 'INITIAL', attemptCount: 1, legalState: status === 'ELIGIBLE' ? 'IMPLEMENTABLE' : null,
  eligible: status === 'ELIGIBLE', updatedAt: '2026-08-05T09:00:00Z' });
const concept = (id, controlled = false) => ({ conceptId: id, conceptName: `Concept ${id}`, oneLineSummary: `Summary ${id}`,
  targetSegment: { segment: `Customer ${id}` }, problemScenario: '문제 상황', valueProposition: '가치', solutionMechanism: '해결 방식',
  actorRoles: [{ actor: '플랫폼', role: '중개' }], platformRole: '중개', transactionFlow: [], dataFlow: [], physicalActivities: [],
  partnerRequirements: ['허가 파트너'], featureSet: ['예약'], channelHypothesis: ['웹'], pricingHypothesis: { model: '구독' },
  operatingModel: { operator: '플랫폼' }, revenueModelHypothesis: { model: '수수료' }, risks: ['도입 위험'],
  legalState: controlled ? 'IMPLEMENTABLE_WITH_CONTROLS' : 'IMPLEMENTABLE', requiredControls: controlled ? ['접근 통제'] : [],
  requiredPartnersOrLicenses: ['허가 파트너'], requiredDisclosures: controlled ? ['처리 목적 고지'] : [], prohibitedVariants: ['직접 수거'],
  unresolvedAssumptions: [], assessmentVersion: 1, validatedSnapshotHash: `sha256:${id}`,
  confirmedBriefVersionId: 10, briefHash: 'sha256:brief', regulatoryBoundaryVersionId: 20,
  boundaryHash: 'sha256:boundary', stale: false, duplicateStatus: 'UNIQUE' });

function workboard(overrides = {}) {
  return { batch, slots: [slot(2), slot(0), slot(1)], concepts: [], network: 'STREAMING', error: '',
    job: { events: [], transport: 'SSE' }, load: vi.fn(), retry: vi.fn(), ...overrides };
}

describe('ConceptWorkboard', () => {
  it('sorts three slots, shows focus and safe state, and hides every draft detail while running', () => {
    render(<ConceptWorkboard workboard={workboard({ concepts: [concept(1)] })} brief={brief} boundary={boundary} messages={[]} onReturnToBrief={vi.fn()} />);
    const slots = screen.getAllByRole('article');
    expect(slots.map((item) => item.getAttribute('aria-label'))).toEqual([
      expect.stringContaining('Slot 1'), expect.stringContaining('Slot 2'), expect.stringContaining('Slot 3'),
    ]);
    expect(screen.queryByText('Concept 1', { selector: 'h3' })).not.toBeInTheDocument();
    expect(screen.getByText('서로 다른 관점의 사업 Concept를 생성하고 있습니다.')).toBeInTheDocument();
    expect(screen.queryByText(/technicalCode|providerBody|stack trace/i)).not.toBeInTheDocument();
  });

  it('expands a slot timeline with aria-expanded and slot-filtered durable events', () => {
    const events = [{ jobId: 'job-7', sequence: 2, messageKey: 'job.concept.slot.generated', status: 'RUNNING', occurredAt: '2026-08-05T09:00:00Z', messageParams: { slotIndex: 0 } }];
    render(<ConceptWorkboard workboard={workboard({ job: { events, transport: 'SSE' } })} brief={brief} boundary={boundary} onReturnToBrief={vi.fn()} />);
    const button = screen.getAllByRole('button', { name: /Slot Timeline 펼치기/ })[0];
    expect(button).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(button);
    expect(button).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getAllByText('Concept 후보 구조를 생성했습니다.')).toHaveLength(2);
  });

  it('reveals all three cards together only after the completed gate passes', () => {
    const completed = { ...batch, status: 'COMPLETED' };
    render(<ConceptWorkboard workboard={workboard({ batch: completed, slots: [slot(0, 'ELIGIBLE'), slot(1, 'ELIGIBLE'), slot(2, 'ELIGIBLE')], concepts: [concept(1), concept(2, true), concept(3)] })} brief={brief} boundary={boundary} onReturnToBrief={vi.fn()} />);
    expect(screen.getByRole('region', { name: '검증 완료 Concept 3개' })).toBeInTheDocument();
    expect(screen.getAllByText(/Concept [123]/, { selector: 'h3' })).toHaveLength(3);
    expect(screen.getByText('필수 통제 적용 시 구현 가능')).toBeInTheDocument();
    fireEvent.click(screen.getAllByText('구현 구조와 조건 자세히 보기')[1]);
    expect(screen.getByText('접근 통제')).toBeInTheDocument();
    expect(screen.getAllByText('허가 파트너').length).toBeGreaterThan(0);
    expect(screen.getAllByText('직접 수거').length).toBeGreaterThan(0);
  });

  it('shows a safe gate error when completed API returns two concepts', () => {
    render(<ConceptWorkboard workboard={workboard({ batch: { ...batch, status: 'COMPLETED' }, slots: [slot(0, 'ELIGIBLE'), slot(1, 'ELIGIBLE'), slot(2, 'ELIGIBLE')], concepts: [concept(1), concept(2)] })} brief={brief} boundary={boundary} onReturnToBrief={vi.fn()} />);
    expect(screen.getByRole('alert')).toHaveTextContent('상세 후보는 안전하게 숨겼습니다');
    expect(screen.queryByRole('region', { name: '검증 완료 Concept 3개' })).not.toBeInTheDocument();
  });

  it.each([
    ['NEEDS_INPUT', '추가 확인이 필요합니다', 'Brief 수정으로 돌아가기'],
    ['FAILED', 'Concept 탐색을 완료하지 못했습니다', 'Brief와 Boundary 확인'],
    ['STALE', '이전 기준의 Concept입니다', '현재 Brief 확인'],
  ])('renders safe %s recovery action', (status, title, action) => {
    render(<ConceptWorkboard workboard={workboard({ batch: { ...batch, status, stale: status === 'STALE' } })} brief={brief} boundary={boundary} onReturnToBrief={vi.fn()} />);
    expect(screen.getByText(title)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: action })).toBeInTheDocument();
  });

  it('shows an explicit retry action only when the failed batch is retryable', () => {
    const retry = vi.fn();
    render(<ConceptWorkboard workboard={workboard({ batch: { ...batch, status: 'FAILED', retryable: true }, retry })} brief={brief} boundary={boundary} onReturnToBrief={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: '다시 실행' }));
    expect(retry).toHaveBeenCalledOnce();
  });
});
