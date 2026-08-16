import { HYPOTHESIS_LABELS, groupLegalEvidence, hypothesisDisplay, legalStatusLabel } from '../businessProposalModel.js';
import { advertisingOnlyDisclosures, excludeLegalItems, legalAttentionGroups, legalReportSummaryCounts, uniqueLegalItems } from '../legalReportPresentation.js';

const asList = (value) => Array.isArray(value) ? value : value == null || value === '' ? [] : [value];
const text = (value) => typeof value === 'string' ? value : value?.safeSummary ?? value?.title ?? String(value);

function List({ values, empty = '확인된 내용이 없습니다.' }) {
  const items = asList(values);
  return items.length ? <ul>{items.map((item, index) => <li key={`${text(item)}-${index}`}>{text(item)}</li>)}</ul> : <p className="legal-document__empty">{empty}</p>;
}

function ReportSection({ number, title, children, className = '' }) {
  return <section className={`legal-document__section ${className}`}><h2>{number}. {title}</h2>{children}</section>;
}

function EvidenceDocument({ evidence }) {
  const groups = groupLegalEvidence(evidence);
  return groups.length ? <div className="legal-document__laws">{groups.map((group) => <article key={group.lawName}><h3>{group.lawName}</h3>{group.articles.map((article, index) => <section key={article.contentHash ?? `${article.articleReference}-${article.officialSourceUri}-${index}`}><h4>{article.articleReference ?? '관련 근거'}{article.title ? ` ${article.title}` : ''}</h4>{article.boundedProvisionSummary && <><strong>주요 내용</strong><p>{article.boundedProvisionSummary}</p></>}{article.effectiveDate && <p>시행일 {article.effectiveDate}</p>}{article.officialSourceUri && <a href={article.officialSourceUri}>법령 원문 보기</a>}</section>)}</article>)}</div> : <p className="legal-document__empty">표시할 공식 근거가 없습니다.</p>;
}

export default function LegalRegulatoryReportDocument({ project, selection, report }) {
  const body = report?.report ?? {};
  const conclusion = body.finalLegalConclusion && typeof body.finalLegalConclusion === 'object'
    ? body.finalLegalConclusion : { safeSummary: body.finalLegalConclusion };
  const legalStatus = conclusion.status ?? conclusion.legalStatus ?? conclusion.productionStatus ?? conclusion.route;
  const concept = body.selectedConcept ?? {};
  const roles = body.businessRoles ?? {};
  const advertising = body.advertisingExpressionCautions ?? {};
  const hypotheses = asList(body.finalHypotheses);
  const limitations = [conclusion.limitation, conclusion.limitations, conclusion.caveats].flatMap(asList);
  const attentionGroups = legalAttentionGroups(body);
  const summaryCounts = legalReportSummaryCounts(body);
  const advertisingDisclosures = advertisingOnlyDisclosures(body);
  const attentionItems = attentionGroups.flatMap((group) => group.values);
  const allowedClaims = excludeLegalItems(advertising.allowedClaims, attentionItems);
  const prohibitedVariants = excludeLegalItems(body.prohibitedVariants, attentionItems, allowedClaims, advertisingDisclosures);
  const deltaItems = asList(body.deltaLegalHistory).map((item) => item.legalReview?.safeSummary ?? item.safeSummary ?? (item.status ? legalStatusLabel(item.status) : '')).filter(Boolean);
  const delta = excludeLegalItems(deltaItems, attentionItems, allowedClaims, advertisingDisclosures, prohibitedVariants);

  return <article className="legal-regulatory-document">
    <header className="legal-document__cover">
      <p>VENTURE VERIFY</p><h1>법률·규제 사전 검토 보고서</h1>
      <dl><div><dt>프로젝트명</dt><dd>{project?.name ?? '자료 없음'}</dd></div><div><dt>선택 사업안</dt><dd>{selection?.conceptName ?? concept.conceptName ?? concept.conceptDefinition ?? '자료 없음'}</dd></div><div><dt>사업 분야</dt><dd>{project?.industryCategory ?? '자료 없음'}</dd></div><div><dt>검토 기준일</dt><dd>{report?.basisDate ?? body.basisDate ?? '자료 없음'}</dd></div><div><dt>보고서 ID</dt><dd>{report?.reportId ?? body.reportId ?? '자료 없음'}</dd></div></dl>
    </header>
    <ReportSection number="1" title="검토 개요"><p>선택한 사업안과 사용자가 확정한 사업 조건을 기준으로, 현재 제공된 공식 근거 범위에서 확인해야 할 법률·규제 사항을 정리했습니다.</p></ReportSection>
    <ReportSection number="2" title="종합 판단"><strong className="legal-document__verdict">{legalStatusLabel(legalStatus)}</strong><p>{conclusion.safeSummary ?? '법률·규제 검토 결과가 준비되었습니다.'}</p></ReportSection>
    <ReportSection number="3" title="주요 검토 결과 요약" className="legal-document__execution"><table><thead><tr><th scope="col">항목</th><th scope="col">건수</th></tr></thead><tbody><tr><th scope="row">필요한 조치</th><td>{summaryCounts.controls}건</td></tr><tr><th scope="row">필수 고지</th><td>{summaryCounts.disclosures}건</td></tr><tr><th scope="row">파트너·자격</th><td>{summaryCounts.partners}건</td></tr><tr><th scope="row">추가 확인</th><td>{summaryCounts.unknownFacts}건</td></tr></tbody></table></ReportSection>
    <ReportSection number="4" title="선택 사업안 개요"><dl><div><dt>한 줄 정의</dt><dd>{concept.conceptDefinition ?? concept.summary ?? '자료 없음'}</dd></div><div><dt>주요 고객</dt><dd>{asList(concept.targetUsers).join(' · ') || '자료 없음'}</dd></div><div><dt>핵심 가치</dt><dd>{text(concept.coreValue ?? '자료 없음')}</dd></div><div><dt>제공 방식</dt><dd>{text(concept.solutionMechanism ?? '자료 없음')}</dd></div></dl></ReportSection>
    <ReportSection number="5" title="확정 사업 조건"><dl>{hypotheses.map((item) => <div key={item.hypothesisType}><dt>{HYPOTHESIS_LABELS[item.hypothesisType] ?? '사업 조건'}</dt><dd>{hypothesisDisplay(item.hypothesisType, item.finalValue ?? item.proposedValue) || '자료 없음'}</dd></div>)}</dl></ReportSection>
    <ReportSection number="6" title="필요한 조치"><List values={attentionGroups[0].values} /></ReportSection>
    <ReportSection number="7" title="필수 고지사항"><List values={attentionGroups[1].values} /></ReportSection>
    <ReportSection number="8" title="파트너·자격·인허가"><List values={attentionGroups[2].values} /></ReportSection>
    <ReportSection number="9" title="사업 구조와 역할"><dl>{[['플랫폼 역할', roles.platformRole], ['판매 주체', roles.sellerRole], ['서비스 제공 주체', roles.providerRole], ['중개 주체', roles.intermediaryRole]].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value || '자료 없음'}</dd></div>)}</dl></ReportSection>
    <ReportSection number="10" title="관련 법률·규제" className="legal-document__evidence"><EvidenceDocument evidence={body.officialEvidenceReferences} /></ReportSection>
    <ReportSection number="11" title="광고·표현 주의사항"><h3>사용 가능한 표현</h3><List values={allowedClaims} />{advertisingDisclosures.length > 0 && <><h3>광고에서 함께 표시할 내용</h3><List values={advertisingDisclosures} /></>}<h3>피해야 할 표현</h3><List values={prohibitedVariants} /></ReportSection>
    <ReportSection number="12" title="확인되지 않은 사항"><List values={attentionGroups[3].values} /></ReportSection>
    <ReportSection number="13" title="거래·결제·개인정보 등 상세 검토"><h3>거래 흐름</h3><List values={uniqueLegalItems(body.transactionFlow)} /><h3>결제·수취 흐름</h3><List values={uniqueLegalItems(body.paymentFlow)} /><h3>개인정보 이용</h3><List values={uniqueLegalItems(body.personalDataUsage)} /><h3>물리 활동</h3><List values={uniqueLegalItems(body.physicalActivities)} /></ReportSection>
    <ReportSection number="14" title="변경사항 재검토 이력"><List values={delta} empty="이번 확정 과정에서 재검토가 필요한 변경은 없었습니다." /></ReportSection>
    <ReportSection number="15" title="검토 범위와 한계"><List values={limitations} empty="본 보고서는 제공된 사업안·확정 조건과 표시된 공식 근거 범위에서 작성되었으며, 법률 자문이나 법적 보장을 의미하지 않습니다." /></ReportSection>
  </article>;
}
