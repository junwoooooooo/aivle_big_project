import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { useJobEvents } from '../../../shared/async-events/index.js';
import { createIdeaBriefApiAdapter, ideaCommandOptions } from '../api/ideaBriefApi.js';
import {
  createIdeaIntakeDraft,
  createDerivePayload,
  IDEA_INTAKE_SCREEN_STATE,
  ideaIntakeDraftReducer,
  QUESTION_TYPE,
  questionsFromIdeaBrief,
  validateIdeaIntake,
} from '../model/ideaIntakeModel.js';

export const IDEA_FAILURE_KIND = Object.freeze({
  DERIVATION_FAILURE: 'DERIVATION_FAILURE',
  INTERACTION_FAILURE: 'INTERACTION_FAILURE',
  STATE_RECONCILIATION_REQUIRED: 'STATE_RECONCILIATION_REQUIRED',
});

function hasAnswer(question, answer) {
  if (question.type === QUESTION_TYPE.MULTI_SELECT) return Array.isArray(answer) && answer.length > 0;
  return answer != null && answer !== '';
}

function screenStateFor(response) {
  if (response?.recoveryRequired) return IDEA_INTAKE_SCREEN_STATE.RECOVERY;
  if (response?.status === 'NEEDS_INPUT') {
    if ((response.questions ?? []).some((question) => !question.answered)) {
      return IDEA_INTAKE_SCREEN_STATE.NEEDS_QUESTIONS;
    }
    if ((response.readiness?.missingFieldKeys ?? []).length > 0) {
      return IDEA_INTAKE_SCREEN_STATE.NEEDS_FIELDS;
    }
    return IDEA_INTAKE_SCREEN_STATE.REVIEW;
  }
  return {
    DRAFT: IDEA_INTAKE_SCREEN_STATE.READY,
    DERIVING: IDEA_INTAKE_SCREEN_STATE.RUNNING,
    READY_FOR_REVIEW: IDEA_INTAKE_SCREEN_STATE.REVIEW,
    SAFETY_BLOCKED: IDEA_INTAKE_SCREEN_STATE.SAFETY_BLOCKED,
    CONFIRMED: IDEA_INTAKE_SCREEN_STATE.CONFIRMED,
    FAILED: IDEA_INTAKE_SCREEN_STATE.FAILED,
    STALE: IDEA_INTAKE_SCREEN_STATE.FAILED,
  }[response?.status] ?? IDEA_INTAKE_SCREEN_STATE.EMPTY;
}

export default function useIdeaIntake(projectId) {
  const client = useApiClient();
  const api = useMemo(() => createIdeaBriefApiAdapter(client), [client]);
  const [draft, dispatch] = useReducer(ideaIntakeDraftReducer, undefined, createIdeaIntakeDraft);
  const [screenState, setScreenState] = useState(IDEA_INTAKE_SCREEN_STATE.LOADING);
  const [errors, setErrors] = useState({});
  const [failureMessage, setFailureMessage] = useState('');
  const [failureKind, setFailureKind] = useState(IDEA_FAILURE_KIND.INTERACTION_FAILURE);
  const [activeJobId, setActiveJobId] = useState(null);
  const [isReanalyzing, setIsReanalyzing] = useState(false);
  const [questions, setQuestions] = useState([]);
  const terminalJobId = useRef(null);
  const jobEvents = useJobEvents(activeJobId);

  const applyResponse = useCallback((response) => {
    dispatch({ type: 'LOAD_SERVER_BRIEF', response });
    const unansweredIds = new Set((response.questions ?? [])
      .filter((question) => !question.answered).map((question) => question.questionId));
    setQuestions(questionsFromIdeaBrief(response).filter((question) => unansweredIds.has(question.id)));
    setActiveJobId(response.activeJobId ?? null);
    const terminalJobStillAttached = response.status === 'DERIVING'
      && response.activeJobId != null && response.activeJobId === terminalJobId.current;
    if (response.recoveryRequired || terminalJobStillAttached) {
      setFailureKind(IDEA_FAILURE_KIND.STATE_RECONCILIATION_REQUIRED);
      setFailureMessage('이전 분석 작업은 종료되었습니다. 현재 입력으로 다시 분석해 주세요.');
      setScreenState(IDEA_INTAKE_SCREEN_STATE.RECOVERY);
      setIsReanalyzing(false);
      return;
    }
    if (response.activeJobId !== terminalJobId.current) terminalJobId.current = null;
    setScreenState(screenStateFor(response));
    if (response.status !== 'DERIVING') setIsReanalyzing(false);
    if (response.status === 'FAILED' || response.status === 'STALE') {
      setFailureKind(IDEA_FAILURE_KIND.DERIVATION_FAILURE);
      setFailureMessage('AI 분석을 완료하지 못했습니다. 다시 분석할 수 있습니다.');
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
      setFailureKind(IDEA_FAILURE_KIND.INTERACTION_FAILURE);
      setScreenState(IDEA_INTAKE_SCREEN_STATE.FAILED);
    }
  }, [api, applyResponse, projectId]);

  useEffect(() => {
    const timer = setTimeout(refresh, 0);
    return () => clearTimeout(timer);
  }, [refresh]);
  useEffect(() => {
    if (!jobEvents.terminal || !activeJobId) return undefined;
    terminalJobId.current = activeJobId;
    const timer = setTimeout(refresh, 0);
    return () => clearTimeout(timer);
  }, [activeJobId, jobEvents.terminal, refresh]);

  const updateIntake = (field, value) => {
    dispatch({ type: 'UPDATE_INTAKE', field, value });
    setErrors((current) => ({ ...current, [field]: undefined }));
    setScreenState(value.trim() || field !== 'ideaOverview' || draft.intake.ideaOverview.trim()
      ? IDEA_INTAKE_SCREEN_STATE.READY : IDEA_INTAKE_SCREEN_STATE.EMPTY);
  };

  const organizeIdea = async (event) => {
    event.preventDefault();
    const nextErrors = validateIdeaIntake(draft);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    setScreenState(IDEA_INTAKE_SCREEN_STATE.RUNNING);
    try {
      const payload = await api.derive(
        projectId, createDerivePayload(draft), ideaCommandOptions('market-seed-interpret'),
      );
      applyResponse(payload.data);
    } catch (error) {
      setFailureMessage(error?.message ?? '아이디어 정리를 시작하지 못했습니다.');
      setFailureKind(IDEA_FAILURE_KIND.INTERACTION_FAILURE);
      setScreenState(IDEA_INTAKE_SCREEN_STATE.FAILED);
    }
  };

  const submitAnswers = async (event) => {
    event.preventDefault();
    if (questions.length === 0) {
      setScreenState(IDEA_INTAKE_SCREEN_STATE.REVIEW);
      return;
    }
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
      setFailureKind(IDEA_FAILURE_KIND.INTERACTION_FAILURE);
      setScreenState(IDEA_INTAKE_SCREEN_STATE.FAILED);
    }
  };

  const submitMissingFields = async (event) => {
    event.preventDefault();
    const missingFieldKeys = draft.assessment.readiness?.missingFieldKeys ?? [];
    const nextErrors = Object.fromEntries(missingFieldKeys
      .filter((fieldKey) => !draft.fields[fieldKey]?.value?.trim())
      .map((fieldKey) => [fieldKey, '필수 정보를 입력해 주세요.']));
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    try {
      const payload = await api.patchFields(projectId, {
        fields: missingFieldKeys.map((fieldKey) => ({
          fieldKey,
          value: draft.fields[fieldKey].value.trim(),
          decisionState: draft.fields[fieldKey].decisionState,
        })),
      }, ideaCommandOptions('idea-missing-fields'));
      if (payload.data.status === 'DERIVING') setIsReanalyzing(true);
      applyResponse(payload.data);
    } catch (error) {
      setFailureMessage(error?.message ?? '누락 정보를 반영하지 못했습니다.');
      setFailureKind(IDEA_FAILURE_KIND.INTERACTION_FAILURE);
      setScreenState(IDEA_INTAKE_SCREEN_STATE.FAILED);
    }
  };

  const reanalyze = async () => {
    const previousJobId = activeJobId;
    setScreenState(IDEA_INTAKE_SCREEN_STATE.RUNNING);
    setIsReanalyzing(true);
    try {
      const payload = await api.reanalyze(projectId, ideaCommandOptions('idea-reanalyze'));
      if (previousJobId && payload.data.activeJobId === previousJobId) {
        setFailureKind(IDEA_FAILURE_KIND.STATE_RECONCILIATION_REQUIRED);
        setFailureMessage('이전 분석 작업은 종료되었습니다. 현재 입력으로 다시 분석해 주세요.');
        setIsReanalyzing(false);
        setScreenState(IDEA_INTAKE_SCREEN_STATE.RECOVERY);
        return;
      }
      applyResponse(payload.data);
    } catch (error) {
      setFailureMessage(error?.message ?? 'Idea Brief를 다시 분석하지 못했습니다.');
      setFailureKind(IDEA_FAILURE_KIND.INTERACTION_FAILURE);
      setScreenState(IDEA_INTAKE_SCREEN_STATE.FAILED);
    }
  };

  const confirmBrief = async (event) => {
    event.preventDefault();
    try {
      if (draft.commitmentCandidates.length > 0) {
        const reviewed = await api.reviewCommitments(projectId, {
          commitments: draft.commitmentCandidates.map((candidate) => ({
            fieldKey: candidate.fieldKey,
            action: candidate.action,
            value: candidate.action === 'EDIT_AND_CONFIRM' ? candidate.editedValue : null,
          })),
        }, ideaCommandOptions('idea-commitments'));
        applyResponse(reviewed.data);
        if (reviewed.data.status === 'DERIVING') return;
      }
      const patched = await api.patchInterpretation(
        projectId, draft.interpretation, ideaCommandOptions('idea-interpretation'),
      );
      applyResponse(patched.data);
      if (!patched.data.readiness?.readyForConfirm) return;
      const payload = await api.confirmInterpretation(
        projectId, { expectedVersion: null }, ideaCommandOptions('idea-confirm-interpretation'),
      );
      applyResponse(payload.data);
    } catch (error) {
      setFailureMessage(error?.message ?? 'Idea Brief를 확정하지 못했습니다.');
      setFailureKind(IDEA_FAILURE_KIND.INTERACTION_FAILURE);
      setScreenState(IDEA_INTAKE_SCREEN_STATE.FAILED);
    }
  };

  return {
    draft, errors, failureMessage, failureKind, questions, screenState, activeJobId, jobEvents, isReanalyzing,
    setFiles: (files) => dispatch({ type: 'SET_FILES', files }),
    updateIntake,
    answerQuestion: (questionId, value) => {
      dispatch({ type: 'ANSWER_QUESTION', questionId, value });
      setErrors((current) => ({ ...current, [questionId]: undefined }));
    },
    updateBriefField: (field, value) => dispatch({ type: 'UPDATE_BRIEF_FIELD', field, value }),
    updateInterpretation: (field, value) => dispatch({ type: 'UPDATE_INTERPRETATION', field, value }),
    updateCommitmentValue: (fieldKey, value) => dispatch({ type: 'UPDATE_COMMITMENT_VALUE', fieldKey, value }),
    setCommitmentAction: (fieldKey, action) => dispatch({ type: 'SET_COMMITMENT_ACTION', fieldKey, action }),
    organizeIdea, submitAnswers, submitMissingFields, confirmBrief,
    refresh, reanalyze,
    restart: () => setScreenState(IDEA_INTAKE_SCREEN_STATE.READY),
  };
}
