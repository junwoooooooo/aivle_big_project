const messages = {
  'job.idea.file.extraction.started': '첨부 문서의 텍스트와 표를 읽고 있습니다.',
  'job.idea.followup.ready': '추가 확인이 필요한 질문을 준비했습니다.',
  'job.idea.attachment.received': '첨부파일을 안전하게 저장했습니다.',
  'job.idea.attachment.parsing.started': '첨부파일에서 텍스트를 추출하고 있습니다.',
  'job.idea.attachment.parsing.failed': '첨부파일을 처리하지 못했습니다. 파일 형식을 확인해 주세요.',
  'job.idea.information.extraction.started': '입력 내용을 이해하고 있습니다.',
  'job.idea.information.extraction.completed': '사업 기회에 필요한 정보를 정리했습니다.',
  'job.idea.result.repairing': '응답 형식을 정리하고 있습니다.',
  'job.idea.brief.draft.queued': 'Opportunity Brief 초안 생성을 준비하고 있습니다.',
  'job.idea.brief.draft.started': '사업 기회에 필요한 정보를 정리하고 있습니다.',
  'job.idea.brief.draft.completed': 'Opportunity Brief 초안이 준비되었습니다.',
  'job.idea.brief.draft.failed': 'Opportunity Brief 초안을 만들지 못했습니다. 다시 시도해 주세요.',
  'job.idea.questions.completed': '추가 질문을 준비했습니다.',
  'job.boundary.lookup.started': '관련 공식 법령 근거를 확인하고 있습니다.',
  'job.boundary.queued': '규제 경계 생성을 준비하고 있습니다.',
  'job.boundary.classification.started': '사업 활동과 규제 경로를 분류하고 있습니다.',
  'job.boundary.routing.completed': '확인할 공식 법령 경로를 정리했습니다.',
  'job.boundary.evidence.fetch.started': '공식 법령 근거를 조회하고 있습니다.',
  'job.boundary.evidence.fetch.completed': '공식 법령 근거 조회를 마쳤습니다.',
  'job.boundary.screening.started': '현재 사업 구조와 관련된 근거를 선별하고 있습니다.',
  'job.boundary.rules.normalizing': '근거를 실행 가능한 사업 규칙으로 정리하고 있습니다.',
  'job.boundary.conflict.checking': '고정 조건과 규제 경계의 충돌을 확인하고 있습니다.',
  'job.boundary.needs_input': '규제 경계를 완성하려면 추가 확인이 필요합니다.',
  'job.boundary.blocked': '고정 조건과 규제 경계가 충돌합니다.',
  'job.boundary.completed': 'Concept 탐색에 사용할 규제 경계가 준비되었습니다.',
  'job.boundary.failed': '규제 경계를 생성하지 못했습니다. 다시 시도 가능 여부를 확인해 주세요.',
  'job.boundary.recovered': '중단된 규제 경계 작업을 복구했습니다.',
  'job.concept.legal-validation.started': 'Concept의 사업자 역할과 운영 구조를 확인하고 있습니다.',
  'job.concept.redesign.started': '규제 조건에 맞도록 운영 방식을 다시 설계하고 있습니다.',
  'job.concept.batch.queued': 'Concept 탐색 작업을 준비하고 있습니다.',
  'job.concept.slot.started': '서로 다른 관점의 Concept 후보 생성을 시작했습니다.',
  'job.concept.slot.generated': 'Concept 후보 구조를 생성했습니다.',
  'job.concept.slot.schema_invalid': '후보 구조를 안전한 형식으로 다시 정리하고 있습니다.',
  'job.concept.slot.retrying': '일시적인 생성 오류가 있어 해당 후보만 다시 시도하고 있습니다.',
  'job.concept.slot.repairing': '해당 후보의 구조 형식을 정리하고 있습니다.',
  'job.concept.slot.validating_origin': '확정된 아이디어 조건을 확인하고 있습니다.',
  'job.concept.slot.validating_boundary': '규제 경계와 구현 방식을 확인하고 있습니다.',
  'job.concept.slot.redesigning': '규제 조건에 맞도록 역할과 운영 흐름을 다시 설계하고 있습니다.',
  'job.concept.slot.replacing': '조건에 맞지 않는 후보를 다른 방향으로 교체하고 있습니다.',
  'job.concept.slot.eligible': '해당 Concept 후보의 검증이 완료되었습니다.',
  'job.concept.slot.rejected': '조건에 맞지 않는 내부 후보를 사용자 후보에서 제외했습니다.',
  'job.concept.batch.needs_input': 'Concept 생성을 위해 확인할 정보가 남아 있습니다.',
  'job.concept.batch.completed': '검증된 Concept 3개가 준비되었습니다.',
  'job.concept.batch.failed': 'Concept 탐색을 완료하지 못했습니다.',
  'job.concept.batch.recovered': '중단된 Concept 탐색 작업을 복구했습니다.',
  'job.legal-report.build.started': '선택한 Concept의 법률 보고서를 구성하고 있습니다.',
};

const hiddenKeys = new Set(['job.claimed', 'job.started']);

export function isUserVisibleJobEvent(event) {
  return !hiddenKeys.has(event?.messageKey);
}

export function jobEventMessage(event) {
  const template = messages[event?.messageKey] ?? '작업 상태가 업데이트되었습니다.';
  return template.replace(/\{([a-zA-Z0-9_]+)\}/g, (_, key) => {
    const value = event?.messageParams?.[key];
    return typeof value === 'string' || typeof value === 'number' ? String(value) : '';
  });
}
