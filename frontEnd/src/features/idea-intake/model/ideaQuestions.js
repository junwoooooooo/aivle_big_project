import { QUESTION_TYPE } from './ideaIntakeModel.js';

export const IDEA_FOLLOW_UP_QUESTIONS = Object.freeze([
  {
    id: 'idea-overview-clarification',
    fieldKey: 'ideaOverview',
    type: QUESTION_TYPE.FREE_TEXT,
    title: '생각하고 있는 제품이나 서비스를 조금 더 설명해 주세요.',
  },
  {
    id: 'problem-clarification',
    fieldKey: 'problem',
    type: QUESTION_TYPE.FREE_TEXT,
    title: '가장 먼저 해결하려는 문제는 무엇인가요?',
  },
  {
    id: 'target-users-clarification',
    fieldKey: 'targetUsers',
    type: QUESTION_TYPE.FREE_TEXT,
    title: '이 문제를 겪는 예상 사용자는 누구인가요?',
  },
]);
