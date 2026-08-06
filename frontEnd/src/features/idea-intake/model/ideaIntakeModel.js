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

function createBriefField(value = '', source = FIELD_SOURCE.UNDECIDED) {
  return { value, source };
}

export function createIdeaIntakeDraft() {
  const fields = Object.fromEntries(
    BRIEF_FIELD_GROUPS.flatMap((group) => group.fields).map(([fieldKey]) => [fieldKey, createBriefField()]),
  );
  return {
    intake: Object.fromEntries(INTAKE_FIELDS.map((fieldKey) => [fieldKey, ''])),
    referenceFiles: [],
    fields,
    answers: {},
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
    fields[field.fieldKey] = createBriefField(field.value ?? '', SERVER_SOURCE[field.provenance] ?? FIELD_SOURCE.UNDECIDED);
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
      overview: fields.assumptions?.value || currentDraft.intake.overview,
      problem: fields.problem?.value || '',
      expectedUsers: fields.targetCustomers?.value || '',
      region: fields.targetRegion?.value || '',
      desiredOutcome: fields.expectedOutcome?.value || '',
      constraints: fields.fixedConditions?.value || '',
      avoidMethods: fields.prohibitedMethods?.value || '',
    },
    fields,
    answers,
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
    assumptions: intake.overview,
    prohibitedMethods: intake.avoidMethods,
    physicalActivity: '',
    personalData: '',
    payment: '',
    requiredPartners: '',
  };
  return {
    ...draft,
    fields: Object.fromEntries(
      Object.entries(values).map(([fieldKey, value]) => [fieldKey, createBriefField(value, sourceFor(value))]),
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
      answer === '__UNDECIDED__' ? FIELD_SOURCE.UNDECIDED : FIELD_SOURCE.AI_SUGGESTED,
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
          [action.field]: createBriefField(action.value, FIELD_SOURCE.USER_INPUT),
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
