import { useReducer, useState } from 'react';

import { createR2AConfirmBoundary } from '../api/ideaBriefApi.js';
import {
  createConfirmIdeaBriefRequest,
  createIdeaIntakeDraft,
  IDEA_INTAKE_SCREEN_STATE,
  ideaIntakeDraftReducer,
  QUESTION_TYPE,
  validateIdeaIntake,
} from '../model/ideaIntakeModel.js';
import { IDEA_FOLLOW_UP_QUESTIONS } from '../model/ideaQuestions.js';

function hasAnswer(question, answer) {
  if (question.type === QUESTION_TYPE.MULTI_SELECT) return Array.isArray(answer) && answer.length > 0;
  return answer != null && answer !== '';
}

function questionErrors(questions, answers) {
  return Object.fromEntries(
    questions.filter((question) => !hasAnswer(question, answers[question.id])).map((question) => [question.id, question.type === QUESTION_TYPE.UNDECIDED ? '결정 여부를 선택해 주세요.' : '질문에 답해 주세요.']),
  );
}

export default function useIdeaIntake(projectId) {
  const [draft, dispatch] = useReducer(ideaIntakeDraftReducer, undefined, createIdeaIntakeDraft);
  const [screenState, setScreenState] = useState(IDEA_INTAKE_SCREEN_STATE.EMPTY);
  const [errors, setErrors] = useState({});
  const [failureMessage, setFailureMessage] = useState('');
  const [pendingConfirmRequest, setPendingConfirmRequest] = useState(null);

  const updateIntake = (field, value) => {
    dispatch({ type: 'UPDATE_INTAKE', field, value });
    setErrors((current) => ({ ...current, [field]: undefined }));
    setScreenState(value.trim() || field !== 'overview' || draft.intake.overview.trim()
      ? IDEA_INTAKE_SCREEN_STATE.READY
      : IDEA_INTAKE_SCREEN_STATE.EMPTY);
  };

  const organizeIdea = (event) => {
    event.preventDefault();
    const nextErrors = validateIdeaIntake(draft);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    setScreenState(IDEA_INTAKE_SCREEN_STATE.RUNNING);
    dispatch({ type: 'HYDRATE_BRIEF' });
    Promise.resolve().then(() => setScreenState(IDEA_INTAKE_SCREEN_STATE.NEEDS_INPUT));
  };

  const submitAnswers = (event) => {
    event.preventDefault();
    const nextErrors = questionErrors(IDEA_FOLLOW_UP_QUESTIONS, draft.answers);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    dispatch({ type: 'APPLY_ANSWERS', questions: IDEA_FOLLOW_UP_QUESTIONS });
    setScreenState(IDEA_INTAKE_SCREEN_STATE.REVIEW);
  };

  const confirmBrief = (event) => {
    event.preventDefault();
    const boundary = createR2AConfirmBoundary();
    setPendingConfirmRequest(boundary.prepare(projectId, draft, createConfirmIdeaBriefRequest));
    setScreenState(IDEA_INTAKE_SCREEN_STATE.CONFIRMED);
  };

  const retry = () => {
    setFailureMessage('');
    setScreenState(draft.intake.overview.trim() ? IDEA_INTAKE_SCREEN_STATE.READY : IDEA_INTAKE_SCREEN_STATE.EMPTY);
  };

  return {
    draft,
    errors,
    failureMessage,
    pendingConfirmRequest,
    questions: IDEA_FOLLOW_UP_QUESTIONS,
    screenState,
    setFiles: (files) => dispatch({ type: 'SET_FILES', files }),
    updateIntake,
    answerQuestion: (questionId, value) => {
      dispatch({ type: 'ANSWER_QUESTION', questionId, value });
      setErrors((current) => ({ ...current, [questionId]: undefined }));
    },
    updateBriefField: (field, value) => dispatch({ type: 'UPDATE_BRIEF_FIELD', field, value }),
    organizeIdea,
    submitAnswers,
    confirmBrief,
    retry,
    fail: (message) => {
      setFailureMessage(message);
      setScreenState(IDEA_INTAKE_SCREEN_STATE.FAILED);
    },
  };
}
