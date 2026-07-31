import { useState } from 'react';
import { Button, TextInput } from '../../../shared/ui/index.js';

/**
 * 질문 답변 폼. 답변은 기획서 본문이 아니라 확정 정보(confirmedFacts)로 저장된다 (§4-3).
 * 저장해도 재검토는 자동 실행되지 않는다.
 */
export function QuestionAnswerForm({ question, onAnswer, busy }) {
  const [open, setOpen] = useState(false);
  const [answer, setAnswer] = useState('');
  const [source, setSource] = useState('');

  if (question.status === 'ANSWERED') {
    return (
      <li className="legal-question legal-question--answered">
        <strong>{question.question}</strong>
        <p className="legal-muted">답변 완료 — 확정 정보로 저장되어 재검토에 사용됩니다.</p>
      </li>
    );
  }
  return (
    <li className="legal-question">
      <strong>{question.question}</strong>
      <p>{question.reason}</p>
      {!open && (
        <Button variant="outline" onClick={() => setOpen(true)}>답변 입력</Button>
      )}
      {open && (
        <div className="legal-answer-form">
          <TextInput
            label="확인한 사실"
            description="예: 비대상 (안전확인대상 아님)"
            value={answer}
            onChange={(event) => setAnswer(event.target.value)}
          />
          <TextInput
            label="확인 출처"
            description="예: 환경산업기술원 유선 확인"
            value={source}
            onChange={(event) => setSource(event.target.value)}
          />
          <p className="legal-muted">
            답변은 기획서 본문에 삽입되지 않고 확정 정보로만 저장됩니다.
          </p>
          <div className="legal-dialog-actions">
            <Button variant="outline" disabled={busy} onClick={() => setOpen(false)}>취소</Button>
            <Button
              disabled={busy || !answer.trim()}
              onClick={() => onAnswer(question.id, {
                answer: answer.trim(),
                factKey: question.question.slice(0, 180),
                source: source.trim() || null,
              })}
            >
              답변 저장
            </Button>
          </div>
        </div>
      )}
    </li>
  );
}
