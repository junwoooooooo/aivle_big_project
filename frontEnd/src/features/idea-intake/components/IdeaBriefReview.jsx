import { Button } from '../../../shared/ui/index.js';

import {
  BRIEF_FIELD_GROUPS,
  DECISION_STATE_LABEL,
} from '../model/ideaIntakeModel.js';

const GROUP_LABELS = Object.freeze({
  'business-idea': '사업 아이디어',
  'business-conditions': '사업 조건',
  'regulatory-sensitive': '규제 민감 정보',
});
const SOURCE_LABELS = Object.freeze({
  USER_INPUT: '사용자 입력',
  FILE_EXTRACTED: '파일에서 추출',
  AI_SUGGESTED: 'AI 제안',
  UNDECIDED: '미정',
});

export default function IdeaBriefReview({
  draft, onFieldChange, onDecisionStateChange, onConfirm,
}) {
  const { userFacingSummary, contradictions, readiness } = draft.assessment;
  const catalogByKey = Object.fromEntries(draft.catalog.map((field) => [field.key, field]));
  return (
    <form className="idea-brief-review" onSubmit={onConfirm}>
      <div className="idea-review-summary">
        <p>Idea Brief</p>
        <h3>{draft.intake.overview}</h3>
        <span>{userFacingSummary || '필드와 결정 상태를 검토하고 필요한 내용을 직접 수정하세요.'}</span>
      </div>
      {readiness && <section className="idea-readiness" aria-label="컨셉 생성 준비 상태">
        <h3>컨셉 생성 준비 상태 · {readiness.score}점</h3>
        <p>필수 필드 {readiness.completedRequiredFieldCount}/{readiness.totalRequiredFieldCount}</p>
        {readiness.missingFieldKeys?.length > 0
          && <p>미정 필드: {readiness.missingFieldKeys.join(', ')}</p>}
      </section>}
      {contradictions.length > 0 && <section className="idea-contradictions" role="alert">
        <h3>확인이 필요한 충돌</h3>
        <ul>{contradictions.map((value) => <li key={`${value.fieldKeys.join('-')}:${value.summary}`}>
          {value.summary} ({value.fieldKeys.join(', ')})
        </li>)}</ul>
      </section>}
      {BRIEF_FIELD_GROUPS.map((group) => (
        <section key={group.id} className="idea-brief-group" aria-labelledby={`${group.id}-heading`}>
          <h3 id={`${group.id}-heading`}>{GROUP_LABELS[group.id] ?? group.title}</h3>
          <div className="idea-brief-fields">{group.fields.map(([fieldKey]) => {
            const field = draft.fields[fieldKey];
            const label = catalogByKey[fieldKey].label;
            return <div className="idea-brief-field" key={fieldKey}>
              <div>
                <label htmlFor={`brief-${fieldKey}`}>{label}</label>
                <span className="idea-source-badge">{SOURCE_LABELS[field.source]}</span>
              </div>
              <label htmlFor={`brief-state-${fieldKey}`} className="visually-hidden">{label} 결정 상태</label>
              <select id={`brief-state-${fieldKey}`} value={field.decisionState}
                onChange={(event) => onDecisionStateChange(fieldKey, event.target.value)}>
                {Object.entries(DECISION_STATE_LABEL).map(([value, stateLabel]) => (
                  <option value={value} key={value}>{stateLabel}</option>
                ))}
              </select>
              <textarea id={`brief-${fieldKey}`} rows="3" value={field.value} placeholder="미정"
                onChange={(event) => onFieldChange(fieldKey, event.target.value)} />
            </div>;
          })}</div>
        </section>
      ))}
      <div className="idea-primary-action idea-primary-action--sticky"><Button type="submit">
        {readiness?.readyForConfirm ? '이 내용으로 컨셉 만들기' : '저장하고 준비 상태 확인'}
      </Button></div>
    </form>
  );
}
