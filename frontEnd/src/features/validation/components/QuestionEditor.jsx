import { Button, Textarea } from '../../../shared/ui/index.js';

export default function QuestionEditor({ questions, onChange, disabled = false }) {
  const update = (index, value) => onChange(questions.map((question, current) => current === index ? value : question));
  const move = (index, direction) => {
    const target = index + direction;
    if (target < 0 || target >= questions.length) return;
    const next = [...questions];
    [next[index], next[target]] = [next[target], next[index]];
    onChange(next);
  };
  return (
    <fieldset className="validation-questions" disabled={disabled}>
      <legend>질문 구성 <span>{questions.length}/10</span></legend>
      {questions.map((question, index) => (
        <article key={`question-${index}`} className="validation-question-card">
          <Textarea
            label={`질문 ${index + 1}`}
            value={question}
            maxLength="300"
            onChange={(event) => update(index, event.target.value)}
          />
          <div>
            <span>{question.length}/300자</span>
            <Button type="button" size="small" variant="ghost" disabled={index === 0} onClick={() => move(index, -1)}>위로</Button>
            <Button type="button" size="small" variant="ghost" disabled={index === questions.length - 1} onClick={() => move(index, 1)}>아래로</Button>
            <Button type="button" size="small" variant="ghost" disabled={questions.length <= 3} onClick={() => onChange(questions.filter((_, current) => current !== index))}>삭제</Button>
          </div>
        </article>
      ))}
      <Button type="button" variant="outline" disabled={questions.length >= 10} onClick={() => onChange([...questions, ''])}>질문 추가</Button>
    </fieldset>
  );
}
