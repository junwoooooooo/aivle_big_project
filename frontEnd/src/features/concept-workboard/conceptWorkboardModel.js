export const BATCH_MESSAGES = Object.freeze({
  QUEUED: 'Concept 탐색 작업을 준비하고 있습니다.',
  GENERATING: '서로 다른 관점의 사업 Concept를 생성하고 있습니다.',
  VALIDATING: '아이디어 고정값과 규제 경계를 확인하고 있습니다.',
  REPLACING: '조건에 맞지 않는 후보를 다른 방향으로 다시 구성하고 있습니다.',
  COMPLETED: '검증된 Concept 3개가 준비되었습니다.',
  NEEDS_INPUT: 'Concept 생성을 위해 확인할 정보가 남아 있습니다.',
  FAILED: 'Concept 탐색을 완료하지 못했습니다.',
  STALE: '기준 아이디어 또는 규제 경계가 변경되어 다시 탐색해야 합니다.',
});

export const SLOT_MESSAGES = Object.freeze({
  QUEUED: '후보 생성을 기다리고 있습니다.',
  GENERATING: '후보를 생성하고 있습니다.',
  GENERATED: '후보 구조가 생성되었습니다.',
  SCHEMA_INVALID: '후보 구조를 정리하고 있습니다.',
  TRANSIENT_PROVIDER_FAILURE: '일시적인 생성 오류가 있어 다시 시도하고 있습니다.',
  PERMANENT_PROVIDER_FAILURE: '해당 후보 생성 경로를 완료하지 못했습니다.',
  VALIDATING_ORIGIN: '확정된 아이디어 조건을 확인하고 있습니다.',
  VALIDATING_BOUNDARY: '규제 경계와 구현 방식을 확인하고 있습니다.',
  REDESIGNING: '규제 조건에 맞도록 역할과 운영 흐름을 다시 설계하고 있습니다.',
  REPLACING: '조건에 맞지 않는 후보를 다른 방향으로 교체하고 있습니다.',
  ELIGIBLE: '검증이 완료되었습니다.',
  REJECTED: '조건에 맞지 않아 사용자 후보에서 제외했습니다.',
  NEEDS_INPUT: '후보 검증에 추가 정보가 필요합니다.',
  FAILED: '후보 생성을 완료하지 못했습니다.',
  STALE: '이 후보는 이전 기준으로 생성되었습니다.',
});

export const FOCUS_LABELS = Object.freeze({
  TARGET_AND_USER_EXPERIENCE: '고객 경험 중심',
  OPERATING_MODEL_AND_PARTNERS: '운영·파트너 중심',
  REVENUE_AND_CHANNELS: '수익·채널 중심',
});

const PUBLIC_LEGAL_STATES = new Set(['IMPLEMENTABLE', 'IMPLEMENTABLE_WITH_CONTROLS']);

export function sortSlots(slots = []) {
  return [...slots].sort((left, right) => left.slotIndex - right.slotIndex);
}

export function eventsForSlot(events = [], slotIndex) {
  return events
    .filter((event) => Number(event?.messageParams?.slotIndex) === slotIndex)
    .sort((left, right) => left.sequence - right.sequence)
    .filter((event, index, values) => index === 0 || event.sequence !== values[index - 1].sequence);
}

export function domainState(batch) {
  if (!batch) return 'NO_BATCH';
  if (batch.status === 'COMPLETED') return 'COMPLETED';
  if (batch.status === 'NEEDS_INPUT') return 'NEEDS_INPUT';
  if (batch.status === 'FAILED') return 'FAILED';
  if (batch.status === 'STALE' || batch.stale) return 'STALE';
  return 'RUNNING';
}

export function publicConceptGate(batch, slots = [], concepts = []) {
  const eligible = slots.filter((slot) => slot.eligible && slot.status === 'ELIGIBLE');
  if (!batch || batch.status !== 'COMPLETED') return gate(false, 'BATCH_NOT_COMPLETED');
  if (eligible.length !== 3) return gate(false, 'ELIGIBLE_SLOT_COUNT_MISMATCH');
  if (concepts.length !== 3) return gate(false, 'PUBLIC_CONCEPT_COUNT_MISMATCH');
  const matches = concepts.every((concept) => (
    concept.confirmedBriefVersionId === batch.confirmedBriefVersionId
    && concept.briefHash === batch.briefHash
    && concept.regulatoryBoundaryVersionId === batch.regulatoryBoundaryVersionId
    && concept.boundaryHash === batch.boundaryHash
    && !concept.stale
    && concept.duplicateStatus === 'UNIQUE'
    && PUBLIC_LEGAL_STATES.has(concept.legalState)
  ));
  return matches ? gate(true, null) : gate(false, 'PUBLIC_CONCEPT_CONTRACT_MISMATCH');
}

function gate(allowed, reason) {
  return { allowed, reason };
}
