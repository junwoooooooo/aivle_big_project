import { useEffect, useRef } from 'react';

const SECTIONS = Object.freeze([
  ['reviewedActivities', '검토 활동'], ['requiredControls', '필수 통제'],
  ['requiredPartnersAndQualifications', '필수 파트너·자격'], ['requiredDisclosures', '필수 고지'],
  ['prohibitedVariants', '금지 변형'], ['unknownFacts', '남은 확인 사항'],
]);

export default function LegalDetailDialog({ model, onClose }) {
  const dialogRef = useRef(null);
  useEffect(() => {
    const previous = document.activeElement;
    dialogRef.current?.focus();
    const onKeyDown = (event) => {
      if (event.key === 'Escape') onClose();
      if (event.key !== 'Tab') return;
      const focusable = [...dialogRef.current.querySelectorAll('button, a[href]')];
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable.at(-1);
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => { document.removeEventListener('keydown', onKeyDown); previous?.focus?.(); };
  }, [onClose]);

  const assessment = model.legal.assessment ?? {};
  const factPattern = assessment.legalFactPattern ?? {};
  const roles = factPattern.commercialRoles ?? {};
  return <div className="legal-dialog__backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="legal-dialog" role="dialog" aria-modal="true" aria-labelledby="legal-dialog-title" tabIndex="-1" ref={dialogRef}>
      <header><div><p>{model.title}</p><h2 id="legal-dialog-title">전체 법률 근거</h2></div><button type="button" onClick={onClose} aria-label="법률 근거 닫기">닫기</button></header>
      <p className="legal-dialog__notice">공식 근거 기반 법률 구현 가능성 검토이며 법률 자문 완료를 의미하지 않습니다. 사실관계와 검토 시점에 따라 전문가 확인이 필요할 수 있습니다.</p>
      <section><h3>검토 결과</h3><strong>{model.legalStatusLabel}</strong><p>{model.legal.safeSummary}</p><p>검토 기준일: {assessment.reviewBasisDate ?? '정보 없음'}</p><p>전문가 검토 권고: {assessment.expertReviewRecommended ? '권고됨' : '별도 권고 없음'}</p></section>
      <section><h3>컨셉이 정의한 법률 관련 사업 구조</h3>
        <p>플랫폼: {factValue(factPattern.platformRole, model.candidate.platformRole, '정보 없음')}</p>
        <p>제공자: {factValue(roles.providerRole, model.candidate.providerRole, '정보 없음')}</p>
        <p>판매자: {factValue(roles.sellerRole, model.candidate.sellerRole, '정보 없음')}</p>
        <p>중개자: {factValue(roles.intermediaryRole, model.candidate.intermediaryRole, '정보 없음')}</p>
        <p>운영: {factValue(factPattern.operatingModel, model.candidate.operatingModel, '정보 없음')}</p>
      </section>
      <ListSection title="거래 흐름" values={factList(factPattern.transactionFlow, model.candidate.transactionFlow)} />
      <ListSection title="결제 흐름" values={factList(factPattern.paymentFlow, model.candidate.paymentFlow)} />
      <ListSection title="개인정보 이용" values={factList(factPattern.personalDataUsage, model.candidate.personalDataUsage)} />
      <ListSection title="광고 주장" values={factList(factPattern.advertisingClaims, model.candidate.advertisingClaims)} />
      {SECTIONS.map(([key, label]) => <ListSection key={key} title={label} values={assessment[key]} />)}
      <section><h3>공식 Evidence</h3><ul>{(model.legal.evidence ?? []).map((item) => <li key={`${item.title}-${item.officialSourceUri}`}><a href={item.officialSourceUri} target="_blank" rel="noreferrer">{item.title}</a></li>)}</ul></section>
    </section>
  </div>;
}

function factValue(governed, fallback, empty) { return governed?.value ?? fallback ?? empty; }

function factList(governed, fallback) { return Array.isArray(governed?.value) ? governed.value : fallback; }

function ListSection({ title, values }) {
  const items = Array.isArray(values) ? values : [];
  return <section><h3>{title}</h3>{items.length ? <ul>{items.map((item) => <li key={item}>{item}</li>)}</ul> : <p>해당 없음</p>}</section>;
}
