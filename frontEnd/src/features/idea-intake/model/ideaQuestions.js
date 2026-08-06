import { QUESTION_TYPE } from './ideaIntakeModel.js';

export const IDEA_FOLLOW_UP_QUESTIONS = Object.freeze([
  {
    id: 'primary-beneficiary',
    fieldKey: 'beneficiaries',
    type: QUESTION_TYPE.FREE_TEXT,
    title: '이 아이디어의 직접적인 수혜자는 누구인가요?',
    description: '구매자와 실제 사용자가 다르다면 구분해서 적어 주세요.',
  },
  {
    id: 'physical-activity',
    fieldKey: 'physicalActivity',
    type: QUESTION_TYPE.SINGLE_SELECT,
    title: '서비스에 오프라인 또는 물리적 활동이 포함되나요?',
    options: ['포함되지 않음', '일부 포함', '핵심적으로 포함'],
  },
  {
    id: 'sensitive-data',
    fieldKey: 'personalData',
    type: QUESTION_TYPE.MULTI_SELECT,
    title: '다룰 가능성이 있는 민감 정보를 선택해 주세요.',
    description: '해당하는 항목을 모두 선택할 수 있습니다.',
    options: ['개인 식별 정보', '건강 정보', '위치 정보', '결제 정보'],
  },
  {
    id: 'partner-qualification',
    fieldKey: 'requiredPartners',
    type: QUESTION_TYPE.UNDECIDED,
    title: '필요한 파트너나 자격 요건이 정해졌나요?',
    description: '아직 판단하기 어렵다면 미정으로 남겨 둘 수 있습니다.',
  },
]);
