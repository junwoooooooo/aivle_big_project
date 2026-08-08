import { Button } from '../../../shared/ui/index.js';

const INTERPRETATION_FIELDS = Object.freeze([
  ['interpretedProblem', 'AI가 이해한 문제'],
  ['interpretedTargetUsers', 'AI가 이해한 예상 사용자'],
  ['usageContext', '사용 맥락'],
  ['industryCategory', '업종 분류'],
  ['researchScope', '컨셉 탐색 범위'],
  ['conciseIdeaDefinition', '한 줄 아이디어 정의'],
  ['targetRegionInterpretation', '지역 해석'],
  ['relevantKnownCompetitorContext', '경쟁자 맥락'],
]);

export default function IdeaBriefReview({ draft, onInterpretationChange, onConfirm }) {
  const enteredSeed = draft.catalog.filter(({ key }) => draft.fields[key]?.value?.trim());
  return (
    <form className="idea-brief-review" onSubmit={onConfirm}>
      <div className="idea-review-summary">
        <p>AI 해석 · 확인 필요</p>
        <h3>입력하신 아이디어를 이렇게 이해했습니다.</h3>
        <span>{draft.assessment.userFacingSummary || '해석을 확인하고 필요한 부분만 수정해 주세요.'}</span>
      </div>

      {draft.safetyReview && <section className="idea-safety-summary" data-decision={draft.safetyReview.decision}>
        <h3>안전 확인 완료</h3>
        <p>{draft.safetyReview.userFacingReason}</p>
        {draft.safetyReview.restrictions?.length > 0 && <ul>{draft.safetyReview.restrictions.map((value) => <li key={value}>{value}</li>)}</ul>}
      </section>}

      <section className="idea-brief-group" aria-labelledby="locked-seed-heading">
        <h3 id="locked-seed-heading">사용자가 입력한 조건</h3>
        <p className="idea-locked-notice">아래 값은 사용자 입력으로 보존되며 AI 해석이 덮어쓰지 않습니다.</p>
        <dl className="idea-seed-summary">{enteredSeed.map(({ key, label }) => <div key={key}>
          <dt>{label}<span className="idea-source-badge">사용자가 입력</span></dt>
          <dd>{draft.fields[key].value}</dd>
        </div>)}</dl>
      </section>

      <section className="idea-brief-group" aria-labelledby="interpretation-heading">
        <h3 id="interpretation-heading">AI가 해석한 내용</h3>
        <p className="idea-interpretation-help">AI가 해석 · 수정 가능. 수정해도 원래 사용자 입력의 출처는 바뀌지 않습니다.</p>
        <div className="idea-brief-fields">{INTERPRETATION_FIELDS.map(([key, label]) => (
          <div className="idea-brief-field" key={key}>
            <div><label htmlFor={`interpretation-${key}`}>{label}</label><span className="idea-source-badge">AI가 해석</span></div>
            <textarea id={`interpretation-${key}`} rows="3" value={draft.interpretation[key] ?? ''}
              onChange={(event) => onInterpretationChange(key, event.target.value)} />
          </div>
        ))}</div>
      </section>

      <div className="idea-primary-action idea-primary-action--sticky"><Button type="submit">이대로 진행</Button></div>
    </form>
  );
}
