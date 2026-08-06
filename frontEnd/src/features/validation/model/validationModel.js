export const DEFAULT_QUESTIONS = {
  PROBLEM_DISCOVERY: [
    '현재 이 문제를 얼마나 자주 경험한다고 보나요?',
    '가장 불편하게 느낄 상황은 무엇인가요?',
    '현재는 어떤 방식으로 문제를 해결할 가능성이 높나요?',
  ],
  VALUE_PROPOSITION: [
    '이 서비스 설명에서 가장 관심을 끄는 부분은 무엇인가요?',
    '서비스가 필요하지 않다고 느낄 이유는 무엇인가요?',
    '사용을 결정할 때 가장 중요하게 보는 조건은 무엇인가요?',
  ],
  PURCHASE_MOTIVATION: [
    '구매나 신청을 고려하게 만드는 요소는 무엇인가요?',
    '가격이나 비용 측면에서 어떤 우려가 있나요?',
    '결정을 미루거나 포기하게 만드는 이유는 무엇인가요?',
  ],
  MESSAGE_REACTION: [
    '이 메시지에서 가장 먼저 이해되는 가치는 무엇인가요?',
    '신뢰하기 어렵거나 과장됐다고 느낄 표현은 무엇인가요?',
    '다음 행동을 결정하려면 어떤 정보가 더 필요한가요?',
  ],
  CUSTOM: ['', '', ''],
};

export function scoreLabel(score) {
  if (score >= 85) return '매우 높음';
  if (score >= 70) return '높음';
  if (score >= 50) return '보통';
  return '낮음';
}

export function groupAnswersByPersona(answers = []) {
  return answers.reduce((groups, answer) => {
    const current = groups[answer.personaName] ?? [];
    return { ...groups, [answer.personaName]: [...current, answer] };
  }, {});
}
