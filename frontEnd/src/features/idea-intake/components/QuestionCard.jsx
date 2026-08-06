import { QUESTION_TYPE } from '../model/ideaIntakeModel.js';

function toggleMultiValue(current, option) {
  const values = Array.isArray(current) ? current : [];
  return values.includes(option) ? values.filter((value) => value !== option) : [...values, option];
}

export default function QuestionCard({ question, answer, onAnswer }) {
  const descriptionId = question.description ? `${question.id}-description` : undefined;
  return (
    <fieldset id={question.id} className="idea-question-card" aria-describedby={descriptionId}>
      <legend>{question.title}</legend>
      {question.description && <p id={descriptionId}>{question.description}</p>}

      {question.type === QUESTION_TYPE.FREE_TEXT && (
        <textarea
          id={`${question.id}-answer`}
          aria-label={`${question.title} 답변`}
          rows="4"
          value={answer ?? ''}
          onChange={(event) => onAnswer(event.target.value)}
        />
      )}

      {question.type === QUESTION_TYPE.SINGLE_SELECT && <div className="idea-question-options">{question.options.map((option) => (
        <label key={option}><input type="radio" name={question.id} value={option} checked={answer === option} onChange={() => onAnswer(option)} /><span>{option}</span></label>
      ))}</div>}

      {question.type === QUESTION_TYPE.MULTI_SELECT && <div className="idea-question-options">{question.options.map((option) => (
        <label key={option}><input type="checkbox" name={`${question.id}-${option}`} value={option} checked={Array.isArray(answer) && answer.includes(option)} onChange={() => onAnswer(toggleMultiValue(answer, option))} /><span>{option}</span></label>
      ))}</div>}

      {question.type === QUESTION_TYPE.UNDECIDED && (
        <button
          type="button"
          className="idea-undecided-button"
          aria-pressed={answer === '__UNDECIDED__'}
          onClick={() => onAnswer(answer === '__UNDECIDED__' ? '' : '__UNDECIDED__')}
        >
          아직 결정하지 않음
        </button>
      )}
    </fieldset>
  );
}
