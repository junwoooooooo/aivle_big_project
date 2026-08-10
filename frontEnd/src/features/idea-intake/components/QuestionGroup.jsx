import { Button } from '../../../shared/ui/index.js';

import ErrorSummary from './ErrorSummary.jsx';
import QuestionCard from './QuestionCard.jsx';

export default function QuestionGroup({ questions, answers, errors, onAnswer, onSubmit }) {
  const visibleQuestions = questions.slice(0, 4);
  return (
    <form className="idea-question-flow" onSubmit={onSubmit} noValidate>
      <div className="idea-section-heading"><p>AI 후속 질문</p><h3>조금만 더 구체화해 볼게요</h3><span>{visibleQuestions.length}개 질문을 한 번에 확인하고 답할 수 있습니다.</span></div>
      <ErrorSummary errors={errors} title="답변이 필요한 질문이 있습니다." />
      <div className="idea-question-grid">{visibleQuestions.map((question) => (
        <QuestionCard key={question.id} question={question} answer={answers[question.id]} onAnswer={(value) => onAnswer(question.id, value)} />
      ))}</div>
      <div className="idea-primary-action idea-primary-action--sticky"><Button type="submit">답변을 Brief에 반영하기</Button></div>
    </form>
  );
}
