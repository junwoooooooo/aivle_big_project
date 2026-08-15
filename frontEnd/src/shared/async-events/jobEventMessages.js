const messages = Object.freeze({
  'job.idea.queued': '아이디어 정리 작업을 준비하고 있습니다.',
  'job.idea.started': '아이디어 정리를 시작했습니다.',
  'job.idea.extracting': '입력에서 핵심 정보를 추출하고 있습니다.',
  'job.idea.questions.preparing': '후속 질문을 준비하고 있습니다.',
  'job.idea.brief.preparing': '아이디어 정본을 준비하고 있습니다.',
  'job.idea.completed': '아이디어 정리가 완료되었습니다.',
  'job.idea.failed': '아이디어 정리를 완료하지 못했습니다.',
  'job.concept-portfolio.queued': '사업안 검토를 준비하고 있습니다.',
  'job.concept-portfolio.running': '사업 방향을 탐색하고 있습니다.',
  'job.concept-portfolio.ai-executing': '사업안을 구체화하고 관련 법률·규제 조건을 확인하고 있습니다.',
  'job.concept-portfolio.materializing': '검토 결과를 정리하고 있습니다.',
  'job.concept-portfolio.needs-input': '추가로 확인할 사업정보가 있습니다.',
  'job.concept-portfolio.completed': '검토 가능한 사업안이 준비되었습니다.',
  'job.concept-portfolio.failed': '사업안 검토를 완료하지 못했습니다.',
  'job.concept-portfolio.trace.conditions': '확정한 아이디어 조건을 확인하고 있습니다.',
  'job.concept-portfolio.trace.conditions-analyzed': '확정 조건 분석을 완료했습니다.',
  'job.concept-portfolio.trace.directions': '서로 다른 사업 방향을 탐색하고 있습니다.',
  'job.concept-portfolio.trace.drafts-generated': '사업 방향 초안을 만들었습니다.',
  'job.concept-portfolio.trace.direction-validating': '사업 방향이 확정 조건에 맞는지 확인하고 있습니다.',
  'job.concept-portfolio.trace.proposals': '사업안 후보를 구체화하고 있습니다.',
  'job.concept-portfolio.trace.proposal-generated': '사업안 후보를 구체화했습니다.',
  'job.concept-portfolio.trace.proposal-validated': '사업안 구조를 확인했습니다.',
  'job.concept-portfolio.trace.legal': '관련 법률·규제 조건을 확인하고 있습니다.',
  'job.concept-portfolio.trace.legal-started': '법률·규제 검토를 시작했습니다.',
  'job.concept-portfolio.trace.legal-reviewed': '사업안의 법률·규제 검토 결과를 반영했습니다.',
  'job.concept-portfolio.trace.recovery': '추가 사업 방향을 검토하고 있습니다.',
  'job.concept-portfolio.trace.needs-input': '추가로 확인할 실제 사업정보가 있습니다.',
  'job.concept-portfolio.trace.excluded': '검토 기준에 맞지 않는 방향을 결과에서 제외했습니다.',
  'job.concept-portfolio.trace.excluded-duplicate': '기존 사업안과 실질적으로 차이가 작아 제외했습니다.',
  'job.concept-portfolio.trace.excluded-scope': '확정한 아이디어 범위를 벗어나 이번 결과에서 제외했습니다.',
  'job.concept-portfolio.trace.excluded-legal': '현재 조건에서는 법률·규제 요구를 충족하기 어려워 제외했습니다.',
  'job.concept-portfolio.trace.ai-completed': 'AI 검토를 마치고 결과를 정리하고 있습니다.',
  'job.market.trace': '{traceDetail}',
  'job.business-model.trace': '{traceDetail}',
  'job.twin.trace': '{traceDetail}',
  'job.concept-portfolio.summary': '{reviewed}개의 사업안을 검토해 {prepared}개가 준비되었고, {needsInput}개는 실제 사업정보 확인이 필요합니다.',
  'job.concept-portfolio.continuation.queued': '추가 사업정보 반영을 준비하고 있습니다.',
  'job.concept-portfolio.continuation.running': '추가 사업정보를 반영하고 있습니다.',
  'job.concept-portfolio.continuation.ai-executing': '해당 사업안의 법률·규제 조건을 다시 확인하고 있습니다.',
  'job.concept-portfolio.continuation.materializing': '추가 검토 결과를 정리하고 있습니다.',
  'job.concept-portfolio.continuation.needs-input': '추가로 확인할 사업정보가 있습니다.',
  'job.concept-portfolio.continuation.completed': '추가 사업안 검토가 완료되었습니다.',
  'job.concept-portfolio.continuation.failed': '추가 사업정보를 반영하지 못했습니다.',
  'job.concept-portfolio.selection.queued': '사업안 선택 후 검토를 준비하고 있습니다.',
  'job.concept-portfolio.selection.running': '선택한 사업안의 검증 가정을 확인하고 있습니다.',
  'job.concept-portfolio.selection.materializing': '선택 후 검토 결과를 정리하고 있습니다.',
  'job.concept-portfolio.selection.completed': '사업안 선택 후 검토가 완료되었습니다.',
  'job.concept-portfolio.selection.failed': '사업안 선택 후 검토를 완료하지 못했습니다.',
  'job.concept.run.queued': '이전 사업안 검토를 준비하고 있습니다.',
  'job.concept.run.started': '이전 사업안 검토를 시작했습니다.',
  'job.concept.run.needs_input': '추가로 확인할 사업정보가 있습니다.',
  'job.concept.run.completed': '이전 방식의 사업안 검토가 완료되었습니다.',
  'job.concept.run.failed': '이전 방식의 사업안 검토를 완료하지 못했습니다.',
  'job.concept.slot.validating_legal': '규제 경계와 구현 방식을 확인하고 있습니다.',
  'job.marketing.queued': '마케팅 콘텐츠 작업을 준비하고 있습니다.',
  'job.marketing.started': '마케팅 콘텐츠 작업을 시작했습니다.',
  'job.marketing.source_prepared': '확정된 기획 자료를 준비했습니다.',
  'job.marketing.copy_generating': '채널에 맞는 문구를 작성하고 있습니다.',
  'job.marketing.legal_checking': '금지 표현과 필수 고지를 확인하고 있습니다.',
  'job.marketing.completed': '마케팅 콘텐츠가 준비되었습니다.',
  'job.marketing.failed': '마케팅 콘텐츠 작업을 완료하지 못했습니다.',
  'job.marketing.visual.queued': '마케팅 이미지 생성을 준비하고 있습니다.',
  'job.marketing.visual.input_validating': '입력과 Source 이미지를 확인하고 있습니다.',
  'job.marketing.visual.generating': '광고 문구와 이미지를 생성하고 있습니다.',
  'job.marketing.visual.copy_preparing': '배너 전용 문구를 준비하고 있습니다.',
  'job.marketing.visual.image_generating': 'Source 이미지로 광고 이미지를 생성하고 있습니다.',
  'job.marketing.visual.text_composing': '한글 문구의 줄바꿈·크기·대비를 조정하고 있습니다.',
  'job.marketing.visual.result_storing': '완성 이미지를 프로젝트 저장소에 보존하고 있습니다.',
  'job.marketing.visual.completed': '마케팅 이미지가 준비되었습니다.',
  'job.marketing.visual.failed': '마케팅 이미지 생성을 완료하지 못했습니다.',
  'job.market.research.queued': '시장조사 실행을 준비하고 있습니다.',
  'job.market.research.preparing': '시장조사 입력과 수집 단계를 준비하고 있습니다.',
  'job.market.research.completed': '시장조사 결과가 준비되었습니다.',
  'job.market.research.failed': '시장조사를 완료하지 못했습니다.',
  'job.business-model.queued': 'Business Model 분석을 준비하고 있습니다.',
  'job.business-model.preparing': '시장 결과와 실행 계획을 확인하고 있습니다.',
  'job.business-model.completed': 'Business Model 결과가 준비되었습니다.',
  'job.business-model.failed': 'Business Model 분석을 완료하지 못했습니다.',
  'job.twin.stimulus.preparing': 'Twin 비교안 초안을 만들고 있습니다.',
  'job.twin.stimulus.completed': 'Twin 비교안 초안이 준비되었습니다.',
  'job.twin.stimulus.failed': 'Twin 비교안 초안을 만들지 못했습니다.',
  'job.twin.survey.queued': 'Twin 조사를 준비하고 있습니다.',
  'job.twin.survey.running': '표본 응답을 생성하고 집계하고 있습니다.',
  'job.twin.survey.completed': 'Twin 조사 결과가 준비되었습니다.',
  'job.twin.survey.failed': 'Twin 조사를 완료하지 못했습니다.',
  'job.finance.estimate.queued': '재무 입력 추천 생성을 준비하고 있습니다.',
  'job.finance.estimate.alternative.queued': '다른 재무 입력 추천을 준비하고 있습니다.',
  'job.finance.estimate.generating': 'current Market·BM 근거로 재무 입력 추천을 만들고 있습니다.',
  'job.finance.estimate.completed': '재무 입력 추천이 준비되었습니다.',
  'job.finance.estimate.failed': '재무 입력 추천을 완료하지 못했습니다.',
  'job.finance.estimate.stale': '상위 입력이 바뀌어 재무 입력 추천을 적용하지 않았습니다.',
  'job.finance.analysis.queued': '재무 분석 보고서 생성을 준비하고 있습니다.',
  'job.finance.analysis.reporting': '결정론 계산 결과로 재무 분석 설명을 작성하고 있습니다.',
  'job.finance.analysis.completed': '재무 분석 보고서가 준비되었습니다.',
  'job.finance.analysis.fallback': 'AI 설명 생성에 실패해 결정론 계산 Fallback 보고서를 준비했습니다.',
  'job.tech-ops.advisory.queued': '기술·운영 상용화 자문을 준비하고 있습니다.',
  'job.tech-ops.advisory.scaling': 'current Market·BM과 확정 입력을 근거 ledger로 정리하고 있습니다.',
  'job.tech-ops.advisory.evidence': '선택적 외부 근거를 확인하고 있습니다.',
  'job.tech-ops.advisory.generating': '상용화 조언과 파일럿·운영 조건을 작성하고 있습니다.',
  'job.tech-ops.advisory.validating': '조언 영역과 근거 연결을 검증하고 있습니다.',
  'job.tech-ops.advisory.completed': '기술·운영 상용화 자문 보고서가 준비되었습니다.',
  'job.tech-ops.advisory.failed': '기술·운영 상용화 자문을 완료하지 못했습니다.',
  'job.tech-ops.advisory.stale': '상위 current 입력이 바뀌어 자문 결과를 저장하지 않았습니다.',
  'job.tech-ops.advisory.progress': '{traceDetail}',
});

export const ACTIVE_JOB_EVENT_KEYS = Object.freeze(Object.keys(messages));
const activeKeys = new Set(ACTIVE_JOB_EVENT_KEYS);
export function isUserVisibleJobEvent(event) { return activeKeys.has(event?.messageKey); }
export function jobFailureMessage(event) {
  const code = event?.messageParams?.failureCode ?? event?.technicalCode;
  const reason = event?.messageParams?.failureReason;
  if (code === 'DEADLINE_EXCEEDED' || code === 'TASK_TIMEOUT') return '처리 시간이 제한을 초과했습니다.';
  if (code === 'RATE_LIMITED') return '외부 AI 서비스 요청이 일시적으로 제한되었습니다.';
  if (code === 'RESULT_SCHEMA_INVALID' || reason === 'AI_RESULT_INVALID') return 'AI 결과를 서비스 형식으로 확인하는 과정에서 문제가 발생했습니다.';
  if (String(code ?? '').includes('LEGAL') || String(reason ?? '').includes('LEGAL')
      || String(reason ?? '').includes('MOLEG')) return '법률·규제 근거를 확인하는 외부 서비스에 연결하지 못했습니다.';
  if (code === 'DEPENDENCY_UNAVAILABLE' || reason === 'AI_SERVICE_UNAVAILABLE'
      || reason === 'TRANSIENT_EXECUTION_FAILURE') return 'AI 또는 외부 검토 서비스에 일시적으로 연결하지 못했습니다.';
  return '처리 과정에서 예상하지 못한 오류가 발생했습니다.';
}
export function jobEventMessage(event) {
  if (event?.status === 'FAILED' && event?.messageKey === 'job.concept-portfolio.failed') {
    return jobFailureMessage(event);
  }
  const template = messages[event?.messageKey] ?? '작업 상태가 업데이트되었습니다.';
  return template.replace(/\{([a-zA-Z0-9_]+)\}/g, (_, key) => {
    const value = event?.messageParams?.[key];
    return typeof value === 'string' || typeof value === 'number' ? String(value) : '';
  });
}

const INTERNAL_TRACE_TERMS = /\b(candidate|plan|fidelity|lineage|provider|hash|sha-?256)\b|v2\s*lab/i;
export function traceDetailForDisplay(event) {
  const detail = event?.messageParams?.traceDetail;
  if (typeof detail !== 'string' || !detail.trim() || INTERNAL_TRACE_TERMS.test(detail)) return '';
  return detail.trim().slice(0, 256);
}

function significant(event) {
  const action = event?.messageParams?.traceAction;
  const status = event?.status ?? event?.messageParams?.traceStatus;
  return ['REJECTED', 'ACCEPTED', 'NEEDS_INPUT', 'FAILED', 'COMPLETED'].includes(action)
    || ['NEEDS_INPUT', 'FAILED', 'COMPLETED'].includes(status)
    || (String(event?.messageParams?.traceStage ?? event?.stage).startsWith('LEGAL') && action === 'REVIEWED')
    || event?.stage === 'SUMMARY';
}

export function groupJobEvents(events) {
  return (events ?? []).reduce((groups, event) => {
    const previous = groups.at(-1);
    const same = previous && !significant(previous) && !significant(event)
      && previous.messageKey === event.messageKey
      && traceDetailForDisplay(previous) === traceDetailForDisplay(event)
      && previous.status === event.status;
    if (!same) return [...groups, { ...event, groupCount: 1, groupStartedAt: event.occurredAt }];
    groups[groups.length - 1] = { ...event, groupCount: previous.groupCount + 1,
      groupStartedAt: previous.groupStartedAt };
    return groups;
  }, []);
}
