import { useCallback, useEffect, useMemo, useReducer, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { useJobEvents } from '../../../shared/async-events/index.js';
import { createIdeaBriefApiAdapter, ideaCommandOptions } from '../api/ideaBriefApi.js';
import {
  createIdeaIntakeDraft,
  hydrateBriefFromIntake,
  IDEA_INTAKE_SCREEN_STATE,
  ideaIntakeDraftReducer,
  QUESTION_TYPE,
  questionsFromIdeaBrief,
  validateIdeaIntake,
} from '../model/ideaIntakeModel.js';

function hasAnswer(question, answer) {
  if (question.type === QUESTION_TYPE.MULTI_SELECT) return Array.isArray(answer) && answer.length > 0;
  return answer != null && answer !== '';
}

function screenStateFor(response) {
  return {
    DRAFT: IDEA_INTAKE_SCREEN_STATE.READY,
    DERIVING: IDEA_INTAKE_SCREEN_STATE.RUNNING,
    NEEDS_INPUT: IDEA_INTAKE_SCREEN_STATE.NEEDS_INPUT,
    READY_FOR_REVIEW: IDEA_INTAKE_SCREEN_STATE.REVIEW,
    CONFIRMED: IDEA_INTAKE_SCREEN_STATE.CONFIRMED,
    FAILED: IDEA_INTAKE_SCREEN_STATE.FAILED,
    STALE: IDEA_INTAKE_SCREEN_STATE.FAILED,
  }[response?.status] ?? IDEA_INTAKE_SCREEN_STATE.EMPTY;
}

function fieldsPayload(draft, includeEmpty = false) {
  return Object.entries(draft.fields)
    .filter(([, field]) => includeEmpty || Boolean(field.value?.trim()))
    .map(([fieldKey, field]) => ({
      fieldKey,
      value: field.value ?? '',
      decisionState: field.decisionState,
    }));
}

export default function useIdeaIntake(projectId) {
  const client = useApiClient();
  const api = useMemo(() => createIdeaBriefApiAdapter(client), [client]);
  const [draft, dispatch] = useReducer(ideaIntakeDraftReducer, undefined, createIdeaIntakeDraft);
  const [screenState, setScreenState] = useState(IDEA_INTAKE_SCREEN_STATE.LOADING);
  const [errors, setErrors] = useState({});
  const [failureMessage, setFailureMessage] = useState('');
  const [activeJobId, setActiveJobId] = useState(null);
  const [isReanalyzing, setIsReanalyzing] = useState(false);
  const [questions, setQuestions] = useState([]);
  const jobEvents = useJobEvents(activeJobId);

  const applyResponse = useCallback((response) => {
    dispatch({ type: 'LOAD_SERVER_BRIEF', response });
    setQuestions(questionsFromIdeaBrief(response));
    setActiveJobId(response.activeJobId ?? null);
    setScreenState(screenStateFor(response));
    if (response.status !== 'DERIVING') setIsReanalyzing(false);
    if (response.status === 'FAILED' || response.status === 'STALE') {
      setFailureMessage('아이디어 정리를 완료하지 못했습니다. 다시 시도해 주세요.');
    }
  }, []);

  const refresh = useCallback(async () => {
    try {
      const payload = await api.get(projectId);
      applyResponse(payload.data);
    } catch (error) {
      if (error?.status === 404) {
        setScreenState(IDEA_INTAKE_SCREEN_STATE.EMPTY);
        return;
      }
      setFailureMessage(error?.message ?? 'Idea Brief를 불러오지 못했습니다.');
      setScreenState(IDEA_INTAKE_SCREEN_STATE.FAILED);
    }
  }, [api, applyResponse, projectId]);

  useEffect(() => {
    const timer = setTimeout(refresh, 0);
    return () => clearTimeout(timer);
  }, [refresh]);
  useEffect(() => {
    if (!jobEvents.terminal || !activeJobId) return undefined;
    const timer = setTimeout(refresh, 0);
    return () => clearTimeout(timer);
  }, [activeJobId, jobEvents.terminal, refresh]);

  const updateIntake = (field, value) => {
    dispatch({ type: 'UPDATE_INTAKE', field, value });
    setErrors((current) => ({ ...current, [field]: undefined }));
    setScreenState(value.trim() || field !== 'overview' || draft.intake.overview.trim()
      ? IDEA_INTAKE_SCREEN_STATE.READY : IDEA_INTAKE_SCREEN_STATE.EMPTY);
  };

  const organizeIdea = async (event) => {
    event.preventDefault();
    const nextErrors = validateIdeaIntake(draft);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    const prepared = hydrateBriefFromIntake(draft);
    setScreenState(IDEA_INTAKE_SCREEN_STATE.RUNNING);
    try {
      const payload = await api.derive(projectId, {
        overview: prepared.intake.overview,
        fields: fieldsPayload(prepared),
        attachmentFileIds: [],
      }, ideaCommandOptions('idea-derive'));
      applyResponse(payload.data);
    } catch (error) {
      setFailureMessage(error?.message ?? '아이디어 정리를 시작하지 못했습니다.');
      setScreenState(IDEA_INTAKE_SCREEN_STATE.FAILED);
    }
  };

  const submitAnswers = async (event) => {
    event.preventDefault();
    const nextErrors = Object.fromEntries(questions
      .filter((question) => !hasAnswer(question, draft.answers[question.id]))
      .map((question) => [question.id, '질문에 답해 주세요.']));
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    try {
      const payload = await api.answerQuestions(projectId, {
        answers: questions.map((question) => ({
          questionId: question.id,
          answerJson: JSON.stringify(draft.answers[question.id]),
        })),
      }, ideaCommandOptions('idea-answers'));
      applyResponse(payload.data);
    } catch (error) {
      setFailureMessage(error?.message ?? '답변을 저장하지 못했습니다.');
      setScreenState(IDEA_INTAKE_SCREEN_STATE.FAILED);
    }
  };

  const confirmBrief = async (event) => {
    event.preventDefault();
    try {
      const patched = await api.patchFields(projectId, { fields: fieldsPayload(draft, true) }, ideaCommandOptions('idea-fields'));
      if (patched.data.status === 'DERIVING') setIsReanalyzing(true);
      applyResponse(patched.data);
      if (patched.data.status === 'DERIVING' || !patched.data.assessmentCurrent
          || !patched.data.readiness?.readyForConfirm) return;
      const payload = await api.confirm(projectId, { expectedVersion: null }, ideaCommandOptions('idea-confirm'));
      applyResponse(payload.data);
    } catch (error) {
      setFailureMessage(error?.message ?? 'Idea Brief를 확정하지 못했습니다.');
      setScreenState(IDEA_INTAKE_SCREEN_STATE.FAILED);
    }
  };

  return {
    draft, errors, failureMessage, questions, screenState, activeJobId, jobEvents, isReanalyzing,
    setFiles: (files) => dispatch({ type: 'SET_FILES', files }),
    updateIntake,
    answerQuestion: (questionId, value) => {
      dispatch({ type: 'ANSWER_QUESTION', questionId, value });
      setErrors((current) => ({ ...current, [questionId]: undefined }));
    },
    updateBriefField: (field, value) => dispatch({ type: 'UPDATE_BRIEF_FIELD', field, value }),
    updateBriefDecisionState: (field, decisionState) => dispatch({
      type: 'UPDATE_BRIEF_DECISION_STATE', field, decisionState,
    }),
    organizeIdea, submitAnswers, confirmBrief,
    retry: refresh,
  };
}
