import { Link } from 'react-router-dom';

import { projectRoutes } from '../../../app/routing/projectRoutes.js';
import { Button } from '../../../shared/ui/index.js';

const INTERPRETATION_FIELDS = Object.freeze([
  ['interpretedProblem', 'AI가 이해한 문제'],
  ['interpretedTargetUsers', 'AI가 이해한 예상 사용자'],
  ['usageContext', 'AI가 파악한 사용 상황'],
  ['industryCategory', 'AI가 분류한 사업 분야'],
  ['researchScope', 'AI가 정리한 사업안 탐색 범위'],
  ['conciseIdeaDefinition', 'AI가 정리한 한 줄 정의'],
  ['targetRegionInterpretation', 'AI가 이해한 대상 지역'],
  ['relevantKnownCompetitorContext', 'AI가 파악한 경쟁 상황'],
]);

function eligibilityCopy(review) {
  const decision = review?.decision;
  if (decision === 'ALLOW_WITH_RESTRICTIONS') return { icon: '△', title: '일부 내용을 조정하면 진행할 수 있습니다.' };
  if (decision === 'BLOCK_OR_REFRAME') return { icon: '', title: '현재 형태로는 다음 단계로 진행하기 어렵습니다.' };
  return { icon: '✓', title: '다음 단계로 진행할 수 있습니다.' };
}

export default function IdeaBriefReview({
  draft,
  projectId,
  confirmed = false,
  confirming = false,
  hasDownstream = false,
  onInterpretationChange,
  onCommitmentValueChange,
  onCommitmentAction,
  onConfirm,
  onEdit,
}) {
  const catalog = draft.catalog ?? Object.keys(draft.fields ?? {}).map((key) => ({ key, label: key }));
  const commitmentCandidates = draft.commitmentCandidates ?? [];
  const enteredSeed = catalog.filter(({ key }) => draft.fields[key]?.value?.trim()
    && draft.fields[key]?.provenance !== 'USER_CONFIRMED');
  const confirmedCommitments = catalog.filter(({ key }) => draft.fields[key]?.value?.trim()
    && draft.fields[key]?.provenance === 'USER_CONFIRMED');
  const eligibility = eligibilityCopy(draft.safetyReview);
  const beginEdit = () => {
    const accepted = !hasDownstream || window.confirm('아이디어를 수정하면 기존 사업안과 분석 결과는 이전 내용으로 만든 결과가 됩니다. 수정 후 사업안을 다시 생성하는 것을 권장합니다.');
    if (accepted) onEdit?.();
  };

  return <form className="idea-brief-review" data-review-state={confirmed ? 'confirmed' : confirming ? 'confirming' : 'editable'} onSubmit={onConfirm}>
    {confirmed ? <section className="idea-review-complete" role="status">
      <span aria-hidden="true">✓</span><div><h3>아이디어 정리가 완료되었습니다.</h3><p>확정한 내용은 다음 사업안 생성에 사용됩니다.</p></div>
    </section> : <div className="idea-review-summary">
      <p>확인 및 수정</p>
      <h3>입력한 내용과 AI가 정리한 내용을 함께 확인해 주세요.</h3>
      <span>{draft.assessment?.userFacingSummary || '필요한 부분을 수정한 뒤 아이디어를 확정할 수 있습니다.'}</span>
    </div>}

    {draft.safetyReview && <section className="idea-eligibility-strip" data-decision={draft.safetyReview.decision}>
      <span aria-hidden="true">{eligibility.icon}</span><div><h3>아이디어 진행 가능 여부</h3><strong>{eligibility.title}</strong><p>{draft.safetyReview.userFacingReason}</p>
      {draft.safetyReview.restrictions?.length > 0 && <ul>{draft.safetyReview.restrictions.map((value) => <li key={value}>{value}</li>)}</ul>}</div>
    </section>}

    <div className="idea-review-workspace">
      <section className="idea-brief-group idea-review-workspace__user" aria-labelledby="user-input-heading">
        <div className="idea-review-section-heading"><div><p>원본</p><h3 id="user-input-heading">내가 입력한 내용</h3></div><span className="idea-source-badge">사용자 입력</span></div>
        <p className="idea-locked-notice">처음 입력한 내용은 별도로 그대로 보존됩니다.</p>
        {confirmed ? <details className="idea-confirmed-disclosure"><summary>확정 내용 보기</summary><UserValues values={[...enteredSeed, ...confirmedCommitments]} draft={draft} /></details>
          : <UserValues values={enteredSeed} draft={draft} />}
        {!confirmed && confirmedCommitments.length > 0 && <><h4>이미 확정한 조건</h4><UserValues values={confirmedCommitments} draft={draft} /></>}
        {draft.referenceFiles?.length > 0 && <div className="idea-review-attachments"><strong>참고 자료</strong><ul>{draft.referenceFiles.map((file) => <li key={`${file.name}-${file.size}`}>{file.name}</li>)}</ul></div>}
      </section>

      <section className="idea-brief-group idea-review-workspace__ai" aria-labelledby="interpretation-heading">
        <div className="idea-review-section-heading"><div><p>AI 정리</p><h3 id="interpretation-heading">AI가 정리한 내용</h3></div></div>
        <p className="idea-interpretation-help">AI가 입력 내용을 바탕으로 정리한 결과입니다. 필요한 부분은 직접 수정할 수 있으며, 수정한 내용은 이후 사업안 생성에 반영됩니다.</p>
        <div className="idea-brief-fields">{INTERPRETATION_FIELDS.map(([key, label]) => (
          <div className="idea-brief-field" key={key}>
            <label htmlFor={`interpretation-${key}`}>{label}</label>
            {confirmed ? <p className="idea-brief-field__readonly" id={`interpretation-${key}`}>{draft.interpretation[key] || '정리된 내용 없음'}</p>
              : <textarea id={`interpretation-${key}`} rows="3" value={draft.interpretation[key] ?? ''}
                onChange={(event) => onInterpretationChange(key, event.target.value)} />}
          </div>
        ))}</div>
      </section>
    </div>

    {!confirmed && commitmentCandidates.length > 0 && <section className="idea-brief-group" aria-labelledby="commitment-heading">
      <h3 id="commitment-heading">AI가 원문에서 찾은 결정사항</h3>
      <p className="idea-interpretation-help">입력 내용에서 이미 정한 것으로 보이는 항목이 있습니다. 맞는지 확인해 주세요.</p>
      <div className="idea-commitment-list">{commitmentCandidates.map((candidate) => {
        const label = catalog.find(({ key }) => key === candidate.fieldKey)?.label ?? candidate.fieldKey;
        return <article className="idea-commitment-card" key={candidate.fieldKey}>
          <div><strong>{label}</strong><span className="idea-source-badge">확인 필요</span></div>
          <textarea aria-label={`${label} 결정 후보`} value={candidate.editedValue}
            disabled={candidate.action === 'RETURN_TO_OPEN'}
            onChange={(event) => onCommitmentValueChange(candidate.fieldKey, event.target.value)} />
          <small>입력에서 찾은 근거: {candidate.evidenceQuote}</small>
          <div className="idea-commitment-actions" role="group" aria-label={`${label} 결정`}>
            <button type="button" aria-pressed={candidate.action === 'CONFIRM'} onClick={() => onCommitmentAction(candidate.fieldKey, 'CONFIRM')}>맞아요</button>
            <button type="button" aria-pressed={candidate.action === 'EDIT_AND_CONFIRM'} onClick={() => onCommitmentAction(candidate.fieldKey, 'EDIT_AND_CONFIRM')}>수정</button>
            <button type="button" aria-pressed={candidate.action === 'RETURN_TO_OPEN'} onClick={() => onCommitmentAction(candidate.fieldKey, 'RETURN_TO_OPEN')}>아직 정하지 않음</button>
          </div>
        </article>;
      })}</div>
    </section>}

    {confirmed ? <div className="idea-review-actions"><Button type="button" variant="outline" onClick={beginEdit}>아이디어 수정</Button><Link className="ui-button ui-button--primary" to={projectRoutes.concepts(projectId)}>사업안 생성 및 검토로 이동 →</Link></div>
      : <div className="idea-primary-action"><Button type="submit" loading={confirming} disabled={confirming}>{confirming ? '아이디어를 확정하고 있습니다' : '입력 내용으로 아이디어 확정하기'}</Button></div>}
  </form>;
}

function UserValues({ values, draft }) {
  if (values.length === 0) return <p className="idea-locked-notice">표시할 입력 내용이 없습니다.</p>;
  return <dl className="idea-seed-summary">{values.map(({ key, label }) => <div key={key}><dt>{label}</dt><dd>{draft.fields[key].value}</dd></div>)}</dl>;
}
