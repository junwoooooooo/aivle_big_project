import { describe, expect, it } from 'vitest';
import { ACTIVE_JOB_EVENT_KEYS, groupJobEvents, isUserVisibleJobEvent, jobEventMessage,
  traceDetailForDisplay } from './jobEventMessages.js';

describe('V2 job event message registry', () => {
  it('covers actual initial, continuation and selection action keys', () => {
    for (const key of ['job.concept-portfolio.queued', 'job.concept-portfolio.running',
      'job.concept-portfolio.ai-executing', 'job.concept-portfolio.materializing',
      'job.concept-portfolio.needs-input', 'job.concept-portfolio.completed',
      'job.concept-portfolio.failed', 'job.concept-portfolio.continuation.ai-executing',
      'job.concept-portfolio.continuation.completed', 'job.concept-portfolio.selection.running',
      'job.concept-portfolio.selection.completed']) expect(ACTIVE_JOB_EVENT_KEYS).toContain(key);
  });
  it('uses Product copy without the legacy exact-five message', () => {
    expect(jobEventMessage({ messageKey: 'job.concept-portfolio.running' })).toBe('사업 방향을 탐색하고 있습니다.');
    expect(jobEventMessage({ messageKey: 'job.concept-portfolio.completed' })).toBe('검토 가능한 사업안이 준비되었습니다.');
    expect(jobEventMessage({ messageKey: 'job.concept.run.completed' })).not.toContain('5개');
    expect(isUserVisibleJobEvent({ messageKey: 'job.claimed' })).toBe(false);
    expect(jobEventMessage({ messageKey: 'job.marketing.visual.result_storing' })).toContain('프로젝트 저장소');
  });
  it('distinguishes safe Portfolio failure reasons', () => {
    expect(jobEventMessage({ status: 'FAILED', messageKey: 'job.concept-portfolio.failed',
      messageParams: { failureCode: 'DEADLINE_EXCEEDED', retryable: true } }))
      .toBe('처리 시간이 제한을 초과했습니다.');
    expect(jobEventMessage({ status: 'FAILED', messageKey: 'job.concept-portfolio.failed',
      messageParams: { failureCode: 'RATE_LIMITED', retryable: true } }))
      .toBe('외부 AI 서비스 요청이 일시적으로 제한되었습니다.');
    expect(jobEventMessage({ status: 'FAILED', messageKey: 'job.concept-portfolio.failed',
      messageParams: { failureCode: 'RESULT_SCHEMA_INVALID', retryable: false } }))
      .toContain('서비스 형식');
  });
  it('shows safe trace detail and filters internal terminology', () => {
    expect(traceDetailForDisplay({ messageParams: { traceDetail: '거래·결제 구조를 확인했습니다.' } }))
      .toBe('거래·결제 구조를 확인했습니다.');
    expect(traceDetailForDisplay({ messageParams: { traceDetail: 'Candidate lineage hash checked' } }))
      .toBe('');
  });
  it('presents generic Market, BM and Twin trace summaries in user language', () => {
    for (const key of ['job.market.trace', 'job.business-model.trace', 'job.twin.trace']) {
      expect(ACTIVE_JOB_EVENT_KEYS).toContain(key);
      expect(jobEventMessage({ messageKey: key, messageParams: { traceDetail: '실제 단계 완료' } }))
        .toBe('실제 단계 완료');
    }
    expect(jobEventMessage({ messageKey: 'job.marketing.visual.generating' }))
      .toBe('광고 문구와 이미지를 생성하고 있습니다.');
  });
  it('Finance 추천 진행 문구에 TechOps prerequisite를 노출하지 않는다', () => {
    const message = jobEventMessage({ messageKey: 'job.finance.estimate.generating' });
    expect(message).toContain('current Market·BM');
    expect(message).not.toContain('TechOps');
    expect(message).not.toContain('기술·운영');
  });
  it('groups only consecutive generic duplicates and preserves significant events', () => {
    const generic = (id) => ({ eventId: String(id), occurredAt: `2026-08-11T00:00:0${id}Z`,
      status: 'RUNNING', messageKey: 'job.concept-portfolio.trace.proposals',
      messageParams: { traceDetail: '구조를 확인하고 있습니다.', traceAction: 'STARTED' } });
    const grouped = groupJobEvents(Array.from({ length: 8 }, (_, index) => generic(index + 1)));
    expect(grouped).toHaveLength(1);
    expect(grouped[0].groupCount).toBe(8);
    const rejected = { ...generic(9), status: 'REJECTED',
      messageParams: { traceDetail: '제외됨', traceAction: 'REJECTED' } };
    expect(groupJobEvents([generic(1), rejected, generic(2)])).toHaveLength(3);
    for (const status of ['NEEDS_INPUT', 'FAILED', 'COMPLETED']) {
      expect(groupJobEvents([{ ...generic(1), status }, { ...generic(2), status }])).toHaveLength(2);
    }
  });
});
