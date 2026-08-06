export const IDEA_INTAKE_SCREEN_STATE = Object.freeze({
  LOADING: 'LOADING',
  EMPTY: 'EMPTY',
  READY: 'READY',
  RUNNING: 'RUNNING',
  NEEDS_INPUT: 'NEEDS_INPUT',
  REVIEW: 'REVIEW',
  FAILED: 'FAILED',
  CONFIRMED: 'CONFIRMED',
});

export const QUESTION_TYPE = Object.freeze({
  FREE_TEXT: 'FREE_TEXT',
  SINGLE_SELECT: 'SINGLE_SELECT',
  MULTI_SELECT: 'MULTI_SELECT',
  UNDECIDED: 'UNDECIDED',
});

export const FIELD_SOURCE = Object.freeze({
  USER_INPUT: 'USER_INPUT',
  FILE_EXTRACTED: 'FILE_EXTRACTED',
  AI_SUGGESTED: 'AI_SUGGESTED',
  UNDECIDED: 'UNDECIDED',
});

export const FIELD_SOURCE_LABEL = Object.freeze({
  [FIELD_SOURCE.USER_INPUT]: '사용자 입력',
  [FIELD_SOURCE.FILE_EXTRACTED]: '파일에서 추출',
  [FIELD_SOURCE.AI_SUGGESTED]: 'AI 제안',
  [FIELD_SOURCE.UNDECIDED]: '미정',
});

export const DECISION_STATE = Object.freeze({
  LOCKED: 'LOCKED',
  PREFERRED: 'PREFERRED',
  OPEN: 'OPEN',
  ASSUMPTION: 'ASSUMPTION',
});

export const DECISION_STATE_LABEL = Object.freeze({
  [DECISION_STATE.LOCKED]: '반드시 유지',
  [DECISION_STATE.PREFERRED]: '선호',
  [DECISION_STATE.OPEN]: '열어 두기',
  [DECISION_STATE.ASSUMPTION]: '가정',
});

const ALL_QUESTION_TYPES = Object.freeze(Object.values(QUESTION_TYPE));
const FACT_QUESTION_TYPES = Object.freeze([
  QUESTION_TYPE.FREE_TEXT, QUESTION_TYPE.SINGLE_SELECT, QUESTION_TYPE.UNDECIDED,
]);

export const CANONICAL_FIELD_CATALOG = Object.freeze([
  ['problem', '해결 문제', true, DECISION_STATE.PREFERRED, false, ALL_QUESTION_TYPES],
  ['targetCustomers', '대상 고객', true, DECISION_STATE.PREFERRED, false, ALL_QUESTION_TYPES],
  ['beneficiaries', '수혜자', true, DECISION_STATE.PREFERRED, false, ALL_QUESTION_TYPES],
  ['usageContext', '사용 상황', true, DECISION_STATE.PREFERRED, false, ALL_QUESTION_TYPES],
  ['expectedOutcome', '기대 결과', true, DECISION_STATE.PREFERRED, false, ALL_QUESTION_TYPES],
  ['targetRegion', '대상 지역', true, DECISION_STATE.PREFERRED, true, FACT_QUESTION_TYPES],
  ['fixedConditions', '반드시 유지', false, DECISION_STATE.LOCKED, false, ALL_QUESTION_TYPES],
  ['preferredConditions', '선호 조건', false, DECISION_STATE.PREFERRED, false, ALL_QUESTION_TYPES],
  ['openDecisions', '열어 두기', false, DECISION_STATE.OPEN, false, ALL_QUESTION_TYPES],
  ['assumptions', '가정', false, DECISION_STATE.ASSUMPTION, false, ALL_QUESTION_TYPES],
  ['prohibitedMethods', '금지 방식', false, DECISION_STATE.LOCKED, true, ALL_QUESTION_TYPES],
  ['physicalActivity', '물리 활동', true, DECISION_STATE.PREFERRED, true, FACT_QUESTION_TYPES],
  ['personalData', '개인정보', true, DECISION_STATE.PREFERRED, true, FACT_QUESTION_TYPES],
  ['payment', '결제', true, DECISION_STATE.PREFERRED, true, FACT_QUESTION_TYPES],
  ['requiredPartners', '필요 파트너·자격', true, DECISION_STATE.PREFERRED, true, ALL_QUESTION_TYPES],
].map(([key, label, requiredForConcept, defaultDecisionState, regulatorySensitive, allowedQuestionTypes]) => Object.freeze({
  key, label, requiredForConcept, defaultDecisionState, regulatorySensitive, allowedQuestionTypes,
})));

const CATALOG_BY_KEY = Object.freeze(Object.fromEntries(
  CANONICAL_FIELD_CATALOG.map((field) => [field.key, field]),
));

export const BRIEF_FIELD_GROUPS = Object.freeze([
  {
    id: 'business-idea',
    title: '사업 아이디어',
    fields: [
      ['problem', '해결 문제'],
      ['targetCustomers', '대상 고객'],
      ['beneficiaries', '수혜자'],
      ['usageContext', '사용 상황'],
      ['expectedOutcome', '기대 결과'],
      ['targetRegion', '대상 지역'],
    ],
  },
  {
    id: 'business-conditions',
    title: '사업 조건',
    fields: [
      ['fixedConditions', '고정 조건'],
      ['preferredConditions', '선호 조건'],
      ['openDecisions', '열린 결정'],
      ['assumptions', '가정'],
    ],
  },
  {
    id: 'regulatory-sensitive',
    title: '규제 민감 정보',
    fields: [
      ['prohibitedMethods', '금지 방식'],
      ['physicalActivity', '물리 활동'],
      ['personalData', '개인정보'],
      ['payment', '결제'],
      ['requiredPartners', '필요한 파트너·자격'],
    ],
  },
]);

const INTAKE_FIELDS = Object.freeze([
  'overview',
  'problem',
  'expectedUsers',
  'region',
  'desiredOutcome',
  'constraints',
  'avoidMethods',
]);

function createBriefField(value = '', source = FIELD_SOURCE.UNDECIDED, decisionState) {
  return { value, source, decisionState: decisionState ?? DECISION_STATE.OPEN };
}

export function createIdeaIntakeDraft() {
  const fields = Object.fromEntries(
    CANONICAL_FIELD_CATALOG.map((field) => [
      field.key, createBriefField('', FIELD_SOURCE.UNDECIDED, field.defaultDecisionState),
    ]),
  );
  return {
    intake: Object.fromEntries(INTAKE_FIELDS.map((fieldKey) => [fieldKey, ''])),
    referenceFiles: [],
    fields,
    catalog: CANONICAL_FIELD_CATALOG,
    answers: {},
    assessment: { userFacingSummary: '', contradictions: [], readiness: null, clarificationRound: 0, maxClarificationRounds: 2 },
  };
}

const SERVER_SOURCE = Object.freeze({
  USER_CONFIRMED: FIELD_SOURCE.USER_INPUT,
  SOURCE_EXTRACTED: FIELD_SOURCE.FILE_EXTRACTED,
  AI_PROPOSED: FIELD_SOURCE.AI_SUGGESTED,
  MISSING: FIELD_SOURCE.UNDECIDED,
});

export function draftFromIdeaBrief(response, currentDraft = createIdeaIntakeDraft()) {
  const fields = { ...currentDraft.fields };
  for (const field of response?.fields ?? []) {
    if (!fields[field.fieldKey]) continue;
    fields[field.fieldKey] = createBriefField(
      field.value ?? '',
      SERVER_SOURCE[field.provenance] ?? FIELD_SOURCE.UNDECIDED,
      field.decisionState ?? CATALOG_BY_KEY[field.fieldKey].defaultDecisionState,
    );
  }
  const answers = {};
  for (const question of response?.questions ?? []) {
    if (!question.answerJson) continue;
    try { answers[question.questionId] = JSON.parse(question.answerJson); } catch { answers[question.questionId] = question.answerJson; }
  }
  return {
    ...currentDraft,
    intake: {
      ...currentDraft.intake,
      overview: response?.overview ?? currentDraft.intake.overview,
      problem: fields.problem?.value || '',
      expectedUsers: fields.targetCustomers?.value || '',
      region: fields.targetRegion?.value || '',
      desiredOutcome: fields.expectedOutcome?.value || '',
      constraints: fields.fixedConditions?.value || '',
      avoidMethods: fields.prohibitedMethods?.value || '',
    },
    fields,
    catalog: response?.fieldCatalog?.length === CANONICAL_FIELD_CATALOG.length
      ? response.fieldCatalog : currentDraft.catalog,
    answers,
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
  return (response?.questions ?? []).map((question) => {
    const options = parseQuestionOptions(question.optionsJson);
    return {
      id: question.questionId,
      fieldKey: question.targetFieldKey,
      type: question.type,
      title: question.prompt,
      options,
    };
  });
}

function parseQuestionOptions(optionsJson) {
  try { return JSON.parse(optionsJson ?? '[]'); } catch { return []; }
}

function sourceFor(value) {
  return value?.trim() ? FIELD_SOURCE.USER_INPUT : FIELD_SOURCE.UNDECIDED;
}

export function hydrateBriefFromIntake(draft) {
  const { intake } = draft;
  const values = {
    problem: intake.problem,
    targetCustomers: intake.expectedUsers,
    beneficiaries: intake.expectedUsers,
    usageContext: '',
    expectedOutcome: intake.desiredOutcome,
    targetRegion: intake.region,
    fixedConditions: intake.constraints,
    preferredConditions: '',
    openDecisions: '',
    assumptions: '',
    prohibitedMethods: intake.avoidMethods,
    physicalActivity: '',
    personalData: '',
    payment: '',
    requiredPartners: '',
  };
  return {
    ...draft,
    fields: Object.fromEntries(
      Object.entries(values).map(([fieldKey, value]) => [
        fieldKey,
        createBriefField(value, sourceFor(value), CATALOG_BY_KEY[fieldKey].defaultDecisionState),
      ]),
    ),
  };
}

export function validateIdeaIntake(draft) {
  return draft.intake.overview.trim()
    ? {}
    : { overview: '아이디어 개요를 입력해 주세요.' };
}

function normalizeAnswerValue(value) {
  return Array.isArray(value) ? value.join(', ') : value;
}

export function applyQuestionAnswers(draft, questions) {
  const fields = { ...draft.fields };
  questions.forEach((question) => {
    const answer = draft.answers[question.id];
    if (!question.fieldKey || answer == null || answer === '') return;
    fields[question.fieldKey] = createBriefField(
      answer === '__UNDECIDED__' ? '' : normalizeAnswerValue(answer),
      answer === '__UNDECIDED__' ? FIELD_SOURCE.UNDECIDED : FIELD_SOURCE.USER_INPUT,
      CATALOG_BY_KEY[question.fieldKey]?.defaultDecisionState,
    );
  });
  return { ...draft, fields };
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
    case 'APPLY_ANSWERS':
      return applyQuestionAnswers(draft, action.questions);
    case 'UPDATE_BRIEF_FIELD':
      return {
        ...draft,
        fields: {
          ...draft.fields,
          [action.field]: createBriefField(
            action.value,
            FIELD_SOURCE.USER_INPUT,
            draft.fields[action.field].decisionState,
          ),
        },
      };
    case 'UPDATE_BRIEF_DECISION_STATE':
      return {
        ...draft,
        fields: {
          ...draft.fields,
          [action.field]: { ...draft.fields[action.field], decisionState: action.decisionState },
        },
      };
    default:
      return draft;
  }
}

export function createConfirmIdeaBriefRequest(projectId, draft) {
  return {
    projectId,
    overview: draft.intake.overview.trim(),
    fields: Object.fromEntries(
      Object.entries(draft.fields).map(([fieldKey, field]) => [fieldKey, { ...field }]),
    ),
    answers: { ...draft.answers },
    referenceFiles: draft.referenceFiles.map(({ name, size, type }) => ({ name, size, type })),
  };
}
