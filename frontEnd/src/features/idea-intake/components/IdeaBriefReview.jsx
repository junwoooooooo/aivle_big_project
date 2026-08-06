import { Button } from '../../../shared/ui/index.js';

import { BRIEF_FIELD_GROUPS, FIELD_SOURCE_LABEL } from '../model/ideaIntakeModel.js';

export default function IdeaBriefReview({ draft, onFieldChange, onConfirm }) {
  return (
    <form className="idea-brief-review" onSubmit={onConfirm}>
      <div className="idea-review-summary"><p>Idea Brief</p><h3>{draft.intake.overview}</h3><span>각 필드를 직접 수정할 수 있습니다. 수정 내용과 질문 답변은 하나의 Draft에 저장됩니다.</span></div>
      {BRIEF_FIELD_GROUPS.map((group) => (
        <section key={group.id} className="idea-brief-group" aria-labelledby={`${group.id}-heading`}>
          <h3 id={`${group.id}-heading`}>{group.title}</h3>
          <div className="idea-brief-fields">{group.fields.map(([fieldKey, label]) => {
            const field = draft.fields[fieldKey];
            return <div className="idea-brief-field" key={fieldKey}>
              <div><label htmlFor={`brief-${fieldKey}`}>{label}</label><span className="idea-source-badge">{FIELD_SOURCE_LABEL[field.source]}</span></div>
              <textarea id={`brief-${fieldKey}`} rows="3" value={field.value} placeholder="미정" onChange={(event) => onFieldChange(fieldKey, event.target.value)} />
            </div>;
          })}</div>
        </section>
      ))}
      <div className="idea-primary-action idea-primary-action--sticky"><Button type="submit">이 내용으로 컨셉 만들기</Button></div>
    </form>
  );
}
