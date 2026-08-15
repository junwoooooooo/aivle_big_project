export const IDEA_INTAKE_SCREEN_STATE = Object.freeze({
  LOADING: 'LOADING',
  EMPTY: 'EMPTY',
  READY: 'READY',
  RUNNING: 'RUNNING',
  NEEDS_QUESTIONS: 'NEEDS_QUESTIONS',
  NEEDS_FIELDS: 'NEEDS_FIELDS',
  RECOVERY: 'RECOVERY',
  REVIEW: 'REVIEW',
  SAFETY_BLOCKED: 'SAFETY_BLOCKED',
  FAILED: 'FAILED',
  CONFIRMED: 'CONFIRMED',
});

export const QUESTION_TYPE = Object.freeze({
  FREE_TEXT: 'FREE_TEXT',
  SINGLE_SELECT: 'SINGLE_SELECT',
  MULTI_SELECT: 'MULTI_SELECT',
});

export const FIELD_SOURCE = Object.freeze({
  USER_INPUT: 'USER_INPUT',
  AI_DERIVED: 'AI_DERIVED',
  OPEN: 'OPEN',
});

export const FIELD_SOURCE_LABEL = Object.freeze({
  [FIELD_SOURCE.USER_INPUT]: '사용자가 입력',
  [FIELD_SOURCE.AI_DERIVED]: 'AI가 해석',
  [FIELD_SOURCE.OPEN]: '열린 조건',
});

export const DECISION_STATE = Object.freeze({
  LOCKED: 'LOCKED',
  REVIEWABLE: 'REVIEWABLE',
  OPEN: 'OPEN',
});

export const DECISION_STATE_LABEL = Object.freeze({
  [DECISION_STATE.LOCKED]: '확정됨',
  [DECISION_STATE.REVIEWABLE]: '수정 가능',
  [DECISION_STATE.OPEN]: '아직 정하지 않음',
});

const CORE_QUESTIONS = Object.freeze(Object.values(QUESTION_TYPE));
export const CANONICAL_FIELD_CATALOG = Object.freeze([
  ['ideaOverview', '아이디어 개요', true, DECISION_STATE.LOCKED, CORE_QUESTIONS],
  ['problem', '해결하려는 문제', true, DECISION_STATE.LOCKED, CORE_QUESTIONS],
  ['targetUsers', '예상 사용자', true, DECISION_STATE.LOCKED, CORE_QUESTIONS],
  ['targetRegion', '대상 지역', false, DECISION_STATE.OPEN, []],
  ['knownCompetitors', '알려진 경쟁자', false, DECISION_STATE.OPEN, []],
  ['revenueModel', '수익 모델', false, DECISION_STATE.OPEN, []],
  ['price', '가격', false, DECISION_STATE.OPEN, []],
  ['channels', '채널', false, DECISION_STATE.OPEN, []],
  ['differentiators', '차별점', false, DECISION_STATE.OPEN, []],
  ['budgetConstraint', '예산 제약', false, DECISION_STATE.OPEN, []],
  ['teamConstraint', '팀 제약', false, DECISION_STATE.OPEN, []],
  ['timelineConstraint', '일정 제약', false, DECISION_STATE.OPEN, []],
  ['otherConstraint', '기타 제약', false, DECISION_STATE.OPEN, []],
].map(([key, label, requiredForConcept, defaultDecisionState, allowedQuestionTypes]) => Object.freeze({
  key, label, requiredForConcept, defaultDecisionState, regulatorySensitive: false, allowedQuestionTypes,
})));

export const BRIEF_FIELD_GROUPS = Object.freeze([
  { id: 'required-seed', title: '사용자가 입력한 필수 Seed', fields: CANONICAL_FIELD_CATALOG.slice(0, 3).map(({ key, label }) => [key, label]) },
  { id: 'optional-seed', title: '사용자가 확정한 선택 조건', fields: CANONICAL_FIELD_CATALOG.slice(3).map(({ key, label }) => [key, label]) },
]);

const INTAKE_FIELDS = CANONICAL_FIELD_CATALOG.map(({ key }) => key);
const INTERPRETATION_FIELDS = Object.freeze([
  'interpretedProblem', 'interpretedTargetUsers', 'usageContext', 'industryCategory',
  'researchScope', 'conciseIdeaDefinition', 'targetRegionInterpretation',
  'relevantKnownCompetitorContext',
]);

function createBriefField(value = '', source = FIELD_SOURCE.OPEN, decisionState = DECISION_STATE.OPEN, provenance = 'MISSING') {
  return { value, source, decisionState, provenance };
}

export function createIdeaIntakeDraft() {
  return {
    intake: Object.fromEntries(INTAKE_FIELDS.map((fieldKey) => [fieldKey, ''])),
    referenceFiles: [],
    fields: Object.fromEntries(CANONICAL_FIELD_CATALOG.map((field) => [
      field.key, createBriefField('', FIELD_SOURCE.OPEN, field.defaultDecisionState),
    ])),
    catalog: CANONICAL_FIELD_CATALOG,
    answers: {},
    safetyReview: null,
    interpretation: Object.fromEntries(INTERPRETATION_FIELDS.map((key) => [key, ''])),
    commitmentCandidates: [],
    assessment: { userFacingSummary: '', contradictions: [], readiness: null, clarificationRound: 0, maxClarificationRounds: 2 },
  };
}

function serverSource(field) {
  if (field.provenance === 'USER_INPUT' || field.provenance === 'USER_CONFIRMED') return FIELD_SOURCE.USER_INPUT;
  if (field.provenance === 'AI_DERIVED' || field.provenance === 'SOURCE_EXTRACTED' || field.provenance === 'AI_PROPOSED') return FIELD_SOURCE.AI_DERIVED;
  return FIELD_SOURCE.OPEN;
}

export function draftFromIdeaBrief(response, currentDraft = createIdeaIntakeDraft()) {
  const fields = { ...currentDraft.fields };
  for (const field of response?.fields ?? []) {
    if (!fields[field.fieldKey]) continue;
    fields[field.fieldKey] = createBriefField(
      field.value ?? '', serverSource(field), field.decisionState ?? DECISION_STATE.OPEN, field.provenance ?? 'MISSING',
    );
  }
  const answers = {};
  for (const question of response?.questions ?? []) {
    if (!question.answerJson) continue;
    try { answers[question.questionId] = JSON.parse(question.answerJson); } catch { answers[question.questionId] = question.answerJson; }
  }
  const interpretation = { ...currentDraft.interpretation };
  for (const key of INTERPRETATION_FIELDS) interpretation[key] = response?.interpretation?.[key] ?? '';
  return {
    ...currentDraft,
    intake: Object.fromEntries(INTAKE_FIELDS.map((key) => [key,
      fields[key]?.value || (key === 'ideaOverview' ? response?.overview ?? '' : ''),
    ])),
    fields,
    catalog: response?.fieldCatalog?.length ? response.fieldCatalog : currentDraft.catalog,
    answers,
    safetyReview: response?.safetyReview ?? null,
    interpretation,
    commitmentCandidates: (response?.interpretation?.commitmentCandidates ?? []).map((candidate) => ({
      ...candidate, editedValue: candidate.value, action: 'CONFIRM',
    })),
    assessment: {
      userFacingSummary: response?.userFacingSummary ?? '',
      contradictions: response?.contradictions ?? [],
      readiness: response?.readiness ?? null,
      clarificationRound: response?.clarificationRound ?? 0,
      maxClarificationRounds: response?.maxClarificationRounds ?? 2,
    },
  };
}

export function questionsFromIdeaBrief(response) {
  return (response?.questions ?? []).map((question) => ({
    id: question.questionId,
    fieldKey: question.targetFieldKey,
    type: question.type,
    title: question.prompt,
    options: parseQuestionOptions(question.optionsJson),
  }));
}

function parseQuestionOptions(optionsJson) {
  try { return JSON.parse(optionsJson ?? '[]'); } catch { return []; }
}

export function hydrateBriefFromIntake(draft) {
  return {
    ...draft,
    fields: Object.fromEntries(CANONICAL_FIELD_CATALOG.map((definition) => {
      const value = draft.intake[definition.key]?.trim() ?? '';
      return [definition.key, createBriefField(
        value,
        value ? FIELD_SOURCE.USER_INPUT : FIELD_SOURCE.OPEN,
        value ? DECISION_STATE.LOCKED : DECISION_STATE.OPEN,
        value ? 'USER_INPUT' : 'MISSING',
      )];
    })),
  };
}

export function validateIdeaIntake(draft) {
  return Object.fromEntries([
    ['ideaOverview', '아이디어 개요를 입력해 주세요.'],
    ['problem', '해결하려는 문제를 입력해 주세요.'],
    ['targetUsers', '예상 사용자를 입력해 주세요.'],
  ].filter(([key]) => !draft.intake[key]?.trim()));
}

export const IDEA_REFERENCE_ACCEPT = '.docx,.txt,.md,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,text/markdown';
export const IDEA_REFERENCE_MAX_BYTES = 20 * 1024 * 1024;
const IDEA_REFERENCE_EXTENSIONS = new Set(['docx', 'txt', 'md']);

export function validateIdeaReferenceFiles(files) {
  const values = Array.from(files ?? []);
  if (values.length > 20) return '참고 자료는 최대 20개까지 추가할 수 있습니다.';
  for (const file of values) {
    const extension = file.name?.split('.').pop()?.toLowerCase();
    if (!IDEA_REFERENCE_EXTENSIONS.has(extension)) return 'DOCX, TXT, MD 파일만 추가할 수 있습니다.';
    if (!file.size) return '빈 파일은 추가할 수 없습니다.';
    if (file.size > IDEA_REFERENCE_MAX_BYTES) return '파일 하나의 크기는 20MB 이하여야 합니다.';
  }
  return '';
}

export function createDerivePayload(draft, attachmentFileIds = []) {
  const value = (key) => draft.intake[key]?.trim() ?? '';
  return {
    ideaOverview: value('ideaOverview'),
    problem: value('problem'),
    targetUsers: value('targetUsers'),
    optionalSeed: {
      targetRegion: value('targetRegion'),
      knownCompetitors: value('knownCompetitors'),
      revenueModel: value('revenueModel'),
      price: value('price'),
      channels: value('channels'),
      differentiators: value('differentiators'),
      constraints: {
        budgetConstraint: value('budgetConstraint'),
        teamConstraint: value('teamConstraint'),
        timelineConstraint: value('timelineConstraint'),
        otherConstraint: value('otherConstraint'),
      },
    },
    attachmentFileIds,
  };
}

export function ideaIntakeDraftReducer(draft, action) {
  switch (action.type) {
    case 'UPDATE_INTAKE':
      return { ...draft, intake: { ...draft.intake, [action.field]: action.value } };
    case 'SET_FILES':
      return { ...draft, referenceFiles: action.files };
    case 'LOAD_SERVER_BRIEF':
      return draftFromIdeaBrief(action.response, draft);
    case 'HYDRATE_BRIEF':
      return hydrateBriefFromIntake(draft);
    case 'ANSWER_QUESTION':
      return { ...draft, answers: { ...draft.answers, [action.questionId]: action.value } };
    case 'UPDATE_BRIEF_FIELD':
      return {
        ...draft,
        fields: { ...draft.fields, [action.field]: createBriefField(action.value, FIELD_SOURCE.USER_INPUT, DECISION_STATE.LOCKED) },
      };
    case 'UPDATE_INTERPRETATION':
      return { ...draft, interpretation: { ...draft.interpretation, [action.field]: action.value } };
    case 'UPDATE_COMMITMENT_VALUE':
      return { ...draft, commitmentCandidates: draft.commitmentCandidates.map((candidate) => (
        candidate.fieldKey === action.fieldKey
          ? { ...candidate, editedValue: action.value, action: 'EDIT_AND_CONFIRM' } : candidate
      )) };
    case 'SET_COMMITMENT_ACTION':
      return { ...draft, commitmentCandidates: draft.commitmentCandidates.map((candidate) => (
        candidate.fieldKey === action.fieldKey ? { ...candidate, action: action.action } : candidate
      )) };
    default:
      return draft;
  }
}

export function createConfirmIdeaBriefRequest(projectId, draft) {
  return { projectId, ...createDerivePayload(draft), interpretation: { ...draft.interpretation } };
}
