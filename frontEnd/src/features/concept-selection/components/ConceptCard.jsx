export default function ConceptCard({ model, compared, preferred, compareDisabled, onToggleCompare, onPrefer, onDetails }) {
  return <article className="selection-card" data-preferred={preferred || undefined}>
    <header><div><span>컨셉 {model.slotNumber}</span><h2>{model.title}</h2></div><span className="selection-card__legal">{model.legalStatusLabel}</span></header>
    <p className="selection-card__summary">{model.summary}</p><small>시장 값은 선택 전 AI 사전 가설입니다.</small>
    <div className="selection-card__tags" aria-label="비교 태그">{model.tags.map((tag) => <span key={tag}>{tag}</span>)}</div>
    <dl>
      <Item label="핵심 차별점" value={model.differentiator} />
      <Item label="대상 고객" value={model.targetCustomer} />
      <Item label="운영 모델" value={model.operatingModel} />
      <Item label="수익 구조" value={model.revenueModel} />
      <Item label="필수 조건" value={model.requiredControls} />
      <Item label="핵심 위험" value={model.risks} />
    </dl>
    <div className="selection-card__actions">
      <label><input type="checkbox" checked={compared} disabled={!compared && compareDisabled} onChange={() => onToggleCompare(model.conceptId)} /> 비교 대상</label>
      <label><input type="radio" name="preferred-concept" checked={preferred} disabled={!compared} onChange={() => onPrefer(model.conceptId)} /> 선택 후보로 표시</label>
      <button type="button" onClick={() => onDetails(model)}>법률 근거 상세 보기</button>
    </div>
  </article>;
}

function Item({ label, value }) {
  return <div><dt>{label}</dt><dd>{value}</dd></div>;
}
