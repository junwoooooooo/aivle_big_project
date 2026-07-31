import { Alert } from '../../../shared/ui/index.js';
import {
  APPLICABILITY_LABELS, collectActions, evidenceList, extractPlanQuotes,
  LEGAL_CATEGORY_LABELS, lawDigest, RISK_LABELS,
} from '../model/legalReviewViewModel.js';
import { SECTION_LABELS } from '../../structured-plan/model/structuredPlanViewModel.js';
import { usePlanSnapshot } from '../hooks/usePlanSnapshot.js';

const FACT_SECTIONS = ['BUSINESS_OVERVIEW', 'PRODUCT_SERVICE', 'BUSINESS_MODEL'];
const EXCERPT_LENGTH = 300;
const KOREAN_ITEM = ['가', '나', '다'];

function excerpt(text) {
  if (!text) return '';
  const flat = String(text).replace(/\s+/g, ' ').trim();
  return flat.length > EXCERPT_LENGTH ? `${flat.slice(0, EXCERPT_LENGTH)}…` : flat;
}

function evidenceScope(findings) {
  const laws = new Set();
  const articles = new Set();
  (findings ?? []).forEach((finding) => {
    evidenceList(finding).forEach(({ law, article }) => {
      if (law) laws.add(law);
      if (article) articles.add(`${law} ${article}`);
    });
  });
  return { laws: laws.size, articles: articles.size };
}

export function LegalFormalReport({ review, projectId, projectTitle }) {
  const plan = usePlanSnapshot(projectId, review.structuredPlanId);
  const scope = evidenceScope(review.findings);
  const actions = collectActions(review.findings);
  const rows = [...actions.now, ...actions.conditional];
  const insufficient = review.findings.filter(
    (finding) => finding.applicability === 'INSUFFICIENT_INFORMATION',
  );
  const digest = lawDigest(review.findings);
  const completedAt = review.completedAt
    ? new Date(review.completedAt).toLocaleDateString('ko-KR')
    : '—';
  const factSections = FACT_SECTIONS
    .map((code) => (plan?.sections ?? []).find((section) => section.sectionCode === code))
    .filter((section) => section?.content);

  return (
    <article className="legal-formal">
      <h2 className="legal-formal__title">법 률 검 토 보 고 서</h2>
      <p className="legal-formal__subtitle">— 사업 개시 전 규제 사전점검 —</p>
      <table className="legal-formal__head">
        <tbody>
          <tr><th scope="row">문서번호</th><td>사전검토 제{projectId}-{review.versionNumber}호</td></tr>
          <tr><th scope="row">작성일자</th><td>{completedAt}</td></tr>
          <tr>
            <th scope="row">작&nbsp;&nbsp;&nbsp;&nbsp;성</th>
            <td>
              {review.provider === 'mock' ? 'Mock Legal AI (모의 데이터)' : 'AI 법률 사전검토 시스템'}
              {review.modelName && ` — ${review.modelName}`}
            </td>
          </tr>
          <tr>
            <th scope="row">건&nbsp;&nbsp;&nbsp;&nbsp;명</th>
            <td>{projectTitle ?? `프로젝트 #${projectId}`} 사업 개시 관련 규제 사전검토의 건</td>
          </tr>
        </tbody>
      </table>

      <section aria-labelledby="legal-formal-1">
        <h3 id="legal-formal-1">Ⅰ. 검토의 목적 및 범위</h3>
        <ol className="legal-formal__clauses">
          <li>
            본 보고서는 확정된 사업계획(계획 #{review.structuredPlanId}, 문서 버전
            #{review.sourceDocumentVersionId})을 사실관계의 전제로 하여, 사업 개시 전
            확인이 필요한 규제 사항을 10개 범주에 걸쳐 사전점검한 것입니다.
          </li>
          <li>
            본 검토의 근거 조문은 법제처 국가법령정보센터 등재 현행 법령을 기준으로
            하였으며, 본 보고서에 인용된 법령은 {scope.laws}건, 조문은 {scope.articles}건입니다.
          </li>
        </ol>
      </section>

      <section aria-labelledby="legal-formal-2">
        <h3 id="legal-formal-2">Ⅱ. 사실관계의 요지 (검토의 전제)</h3>
        {factSections.length > 0 ? (
          <ol className="legal-formal__clauses">
            {factSections.map((section) => (
              <li key={section.sectionCode}>
                <strong>{SECTION_LABELS[section.sectionCode] ?? section.sectionCode}</strong>
                {' — '}{excerpt(section.content)}
              </li>
            ))}
          </ol>
        ) : (
          <p>확정된 사업계획 #{review.structuredPlanId}의 기재 내용을 검토의 전제로 하였습니다.</p>
        )}
      </section>

      <section aria-labelledby="legal-formal-3">
        <h3 id="legal-formal-3">Ⅲ. 검토 결과의 요지</h3>
        <p>{review.summary}</p>
        {rows.length > 0 && (
          <>
            <p className="legal-formal__table-title">【이행사항 일람】</p>
            <table className="legal-formal__table">
              <thead>
                <tr>
                  <th scope="col">연번</th><th scope="col">이행사항</th>
                  <th scope="col">이행시기</th><th scope="col">중요도</th>
                  <th scope="col">관련 범주</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((item, index) => (
                  <tr key={item.action}>
                    <td>{index + 1}</td>
                    <td>{item.action}</td>
                    <td>{item.timing ?? '시점 미정'}</td>
                    <td>{RISK_LABELS[item.maxRiskLevel] ?? '확인 필요'}</td>
                    <td>{item.categories.map((code) => LEGAL_CATEGORY_LABELS[code] ?? code).join(', ')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </section>

      <section aria-labelledby="legal-formal-4">
        <h3 id="legal-formal-4">Ⅳ. 항목별 검토</h3>
        {review.findings.map((finding, index) => {
          const { body, quotes } = extractPlanQuotes(finding.rationale);
          const evidence = evidenceList(finding);
          return (
            <div key={finding.category} className="legal-formal__item">
              <h4>
                {index + 1}. {LEGAL_CATEGORY_LABELS[finding.category] ?? finding.title}
                <span className={`legal-risk-chip legal-risk--${finding.riskLevel?.toLowerCase()}`}>
                  {RISK_LABELS[finding.riskLevel] ?? finding.riskLevel}
                </span>
                <span className="legal-muted">
                  {APPLICABILITY_LABELS[finding.applicability] ?? finding.applicability}
                </span>
              </h4>
              {evidence.length > 0 && (
                <>
                  <h5>{KOREAN_ITEM[0]}. 관련 규정</h5>
                  <ul>
                    {evidence.map((entry, i) => (
                      <li key={`${i}-${entry.law}-${entry.article}`}>
                        {[entry.law, entry.article].filter(Boolean).join(' ')}
                        {entry.title && `(${entry.title})`}
                        {entry.plainSummary && (
                          <span className="legal-muted"> — {entry.plainSummary}</span>
                        )}
                      </li>
                    ))}
                  </ul>
                </>
              )}
              <h5>{KOREAN_ITEM[1]}. 검토 의견</h5>
              <p>{finding.finding}</p>
              {quotes.map((quote, i) => (
                <blockquote key={`${i}-${quote}`} className="legal-quote">“{quote}”</blockquote>
              ))}
              {body && <p className="legal-muted">{body}</p>}
              <h5>{KOREAN_ITEM[2]}. 권고사항</h5>
              <p>{finding.recommendedAction}</p>
              {finding.requiresProfessionalReview && (
                <p className="legal-muted">※ 자격 있는 전문가의 확인이 권장되는 항목입니다.</p>
              )}
            </div>
          );
        })}
      </section>

      {(review.questions.length > 0 || insufficient.length > 0) && (
        <section aria-labelledby="legal-formal-5">
          <h3 id="legal-formal-5">Ⅴ. 추가 확인이 필요한 사항</h3>
          <p>
            아래 사항은 사업계획서상 판단 근거가 확인되지 아니하여 결론을 유보한 것으로서,
            해당 사실관계가 확정되는 대로 재검토를 요합니다.
          </p>
          <ol className="legal-formal__clauses">
            {insufficient.map((finding) => (
              <li key={finding.category}>
                ({LEGAL_CATEGORY_LABELS[finding.category] ?? finding.category}) {finding.finding}
              </li>
            ))}
            {review.questions.map((item) => (
              <li key={`question-${item.id}`}>{item.question} — {item.reason}</li>
            ))}
          </ol>
        </section>
      )}

      <section aria-labelledby="legal-formal-6">
        <h3 id="legal-formal-6">Ⅵ. 결론</h3>
        <p>
          본 검토의 종합 위험도는 「{RISK_LABELS[review.overallRiskLevel] ?? '확인 필요'}」이며,
          판매 개시 전까지 제Ⅲ항 일람표 기재 이행사항 {actions.now.length}건의 이행이
          요구됩니다{actions.conditional.length > 0
            && `. 그 외 ${actions.conditional.length}건은 관련 계획이 실행되는 시점에 의무가 발생합니다`}.
        </p>
      </section>

      {digest.length > 0 && (
        <section aria-labelledby="legal-formal-appendix">
          <h3 id="legal-formal-appendix">[별첨] 검토 대상 법령 및 조문 일람</h3>
          <table className="legal-formal__table">
            <thead>
              <tr><th scope="col">범주</th><th scope="col">법령</th><th scope="col">주요 조문</th></tr>
            </thead>
            <tbody>
              {digest.flatMap((item) => item.laws.map((entry, index) => (
                <tr key={`${item.category}-${entry.law}`}>
                  {index === 0 && (
                    <td rowSpan={item.laws.length}>
                      {LEGAL_CATEGORY_LABELS[item.category] ?? item.category}
                    </td>
                  )}
                  <td>{entry.law}</td>
                  <td>{entry.articles.join(', ')}</td>
                </tr>
              )))}
            </tbody>
          </table>
        </section>
      )}

      <Alert title="책임의 한계" tone="warning" live={false}>
        본 보고서는 인공지능 기반 사전점검 자료로서 변호사의 법률자문에 해당하지 아니하며,
        적법성에 대한 확정적 판단이나 결과를 보장하지 아니합니다.
        {' '}{review.disclaimer}
      </Alert>
    </article>
  );
}
