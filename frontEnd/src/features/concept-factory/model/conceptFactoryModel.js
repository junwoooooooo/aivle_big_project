export const ELIGIBLE_LEGAL_STATUSES = new Set(['IMPLEMENTABLE', 'IMPLEMENTABLE_WITH_CONTROLS']);

export const VARIATION_LABELS = Object.freeze({
  CUSTOMER_EXPERIENCE: '고객 경험',
  OPERATING_MODEL_AND_PARTNERS: '운영 모델과 파트너',
  REVENUE_AND_PRICING: '수익과 가격',
  CHANNEL_AND_SCALE: '채널과 확장',
  LOW_RISK_FAST_EXECUTION: '낮은 위험과 빠른 실행',
});

export const SLOT_STATUS_COPY = Object.freeze({
  QUEUED: '후보를 만들 준비를 하고 있습니다.',
  GENERATING: '후보를 만들고 있습니다.',
  GENERATED: '사업 구조를 정리하고 있습니다.',
  SCHEMA_INVALID: '사업 구조를 다시 정리하고 있습니다.',
  VALIDATING_ORIGIN: '사업 구조를 정리하고 있습니다.',
  VALIDATING_DISTINCTNESS: '기존 후보와 실질적으로 다른 사업 구조인지 확인하고 있습니다.',
  VALIDATING_LEGAL: '법률 근거를 확인하고 있습니다.',
  REDESIGNING: '필수 통제를 반영하고 있습니다.',
  REPLACING: '부적합 후보를 다른 방향으로 교체하고 있습니다.',
  REVIEW_RETRY_PENDING: '법률 검토 처리 중 오류가 발생했습니다. 생성된 후보는 보존되었습니다.',
  ELIGIBLE: '법률검토를 통과했습니다.',
  REJECTED: '운영 역할을 다시 설계하고 있습니다.',
  NEEDS_INPUT: '확인이 필요한 정보가 있습니다.',
  FAILED: '이 Slot 작업을 완료하지 못했습니다.',
  STALE: '최신 Idea Brief와 다른 결과입니다.',
});

export function dedupeTimeline(events = []) {
  const values = new Map();
  for (const event of events) {
    if (Number.isSafeInteger(event?.sequence) && event.sequence > 0) values.set(event.sequence, event);
  }
  return [...values.values()].sort((a, b) => a.sequence - b.sequence);
}

export function slotNumberFromEvent(event) {
  const value = Number(event?.safeMessageParams?.slot ?? event?.messageParams?.slot);
  return Number.isInteger(value) && value >= 1 && value <= 5 ? value : null;
}

export function workboardSummary(run, slots = []) {
  return {
    eligible: Number(run?.eligibleCount) || slots.filter((slot) => slot.status === 'ELIGIBLE').length,
    initialGenerated: Number(run?.initialCandidateSuccessCount) || 0,
    generatedTotal: Number(run?.generatedCandidateCount) || 0,
    generationFailed: Number(run?.candidateGenerationFailureCount) || 0,
    inspected: Number(run?.inspectedCandidateCount) || 0,
    redesigned: Number(run?.redesignCount) || 0,
    replaced: Number(run?.replacementCandidateCount) || 0,
    discarded: Number(run?.discardedCandidateCount) || 0,
    providerRetries: Number(run?.providerTransientRetryCount) || 0,
  };
}

export function evaluateRevealGate(run, slots = [], concepts = []) {
  const reasons = [];
  if (run?.status !== 'COMPLETED') reasons.push('RUN_NOT_COMPLETED');
  if (slots.length !== 5 || slots.some((slot) => slot.status !== 'ELIGIBLE')) reasons.push('SLOTS_NOT_ELIGIBLE');
  if (concepts.length !== 5) reasons.push('PUBLIC_CONCEPT_COUNT');
  if (concepts.some((concept) => concept.sourceSnapshotHash !== run?.sourceSnapshotHash)) reasons.push('SNAPSHOT_MISMATCH');
  if (concepts.some((concept) => concept.stale)) reasons.push('STALE_CONCEPT');
  if (concepts.some((concept) => !ELIGIBLE_LEGAL_STATUSES.has(concept.legalStatus))) reasons.push('LEGAL_STATUS_NOT_PUBLIC');
  const canonical = concepts.map((concept) => concept.canonicalHash).filter(Boolean);
  const major = concepts.map((concept) => concept.majorFieldHash).filter(Boolean);
  if (canonical.length !== concepts.length || new Set(canonical).size !== concepts.length
    || major.length !== concepts.length || new Set(major).size !== concepts.length) reasons.push('DUPLICATE_OR_MISSING_HASH');
  return { canReveal: reasons.length === 0, reasons };
}
