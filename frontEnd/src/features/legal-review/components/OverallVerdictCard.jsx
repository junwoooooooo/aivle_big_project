import { useEffect, useRef } from 'react';
import { Alert, Card } from '../../../shared/ui/index.js';
import {
  APPLICABILITY_LABELS, buildOverallVerdict, evidenceList, EVIDENCE_ROLE_LABELS,
  findingAnchorId, LEGAL_CATEGORY_LABELS, parseReasoning, parseStringList, RISK_LABELS,
} from '../model/legalReviewViewModel.js';
import { SECTION_LABELS } from '../../structured-plan/model/structuredPlanViewModel.js';

function categoryLabel(item) {
  return LEGAL_CATEGORY_LABELS[item.category] ?? item.title;
}

/** 조문 하나 — 쉬운 설명이 먼저, 법령 원문은 접어 둔다. */
function EvidenceItem({ item }) {
  const heading = [item.law, item.article].filter(Boolean).join(' ');
  return (
    <li className="legal-evidence">
      <p className="legal-evidence__heading">
        <span className="legal-evidence__article">
          {heading}
          {item.title && <span className="legal-evidence__title">({item.title})</span>}
        </span>
        {item.role && (
          <span className={`legal-role-chip legal-role--${item.role.toLowerCase()}`}>
            {EVIDENCE_ROLE_LABELS[item.role] ?? item.role}
          </span>
        )}
      </p>
      {item.plainSummary && (
        <p className="legal-evidence__plain">
          <span className="legal-evidence__tag">쉬운 설명</span>
          {item.plainSummary}
        </p>
      )}
      {item.whyRelevant && (
        <p className="legal-evidence__why">
          <span className="legal-evidence__tag">이 사업에 걸리는 이유</span>
          {item.whyRelevant}
        </p>
      )}
      <p className="legal-evidence__meta">
        {item.effectiveDate && <span>시행 {item.effectiveDate}</span>}
        {item.lawUrl && (
          <a href={item.lawUrl} target="_blank" rel="noreferrer noopener">
            국가법령정보센터에서 전문 보기
          </a>
        )}
      </p>
      {item.excerpt && (
        <details className="legal-excerpt">
          <summary>조문 발췌 보기 (원문 일부)</summary>
          <p>{item.excerpt}</p>
        </details>
      )}
    </li>
  );
}

/** 판정에 이른 5단 사슬. 결측 단계는 건너뛴다. */
function ReasoningChain({ chain }) {
  if (!chain) return null;
  const steps = [];
  if (chain.quotes.length > 0 || chain.sectionLabels.length > 0) {
    steps.push({
      key: 'plan',
      label: '기획서 근거',
      body: (
        <>
          {chain.quotes.map((quote) => <q key={quote}>{quote}</q>)}
          {chain.sectionLabels.length > 0 && (
            <span className="legal-muted">
              {' '}({chain.sectionLabels.map((code) => SECTION_LABELS[code] ?? code).join(', ')})
            </span>
          )}
        </>
      ),
    });
  }
  if (chain.topic) {
    steps.push({
      key: 'path',
      label: '걸리는 규제 영역',
      body: (
        <>
          <strong>{chain.topic}</strong>
          {chain.status && <span className="legal-muted"> · {chain.status}</span>}
          {chain.pathReason && <span> — {chain.pathReason}</span>}
        </>
      ),
    });
  }
  if (chain.obligations.length > 0) {
    steps.push({
      key: 'obligation',
      label: '그래서 생기는 의무',
      body: (
        <ul className="legal-chain__obligations">
          {chain.obligations.map((item) => (
            <li key={`${item.lawName}-${item.article}`}>
              <strong>{item.article}</strong> {item.summary}
            </li>
          ))}
        </ul>
      ),
    });
  }
  if (chain.consequenceText) {
    steps.push({
      key: 'consequence',
      label: '지키지 않으면',
      body: (
        <>
          {chain.consequenceText}
          {chain.sanctionArticles.length > 0 && (
            <span className="legal-muted"> (근거: {chain.sanctionArticles.join(', ')})</span>
          )}
        </>
      ),
    });
  }
  if (chain.action) {
    steps.push({
      key: 'conclusion',
      label: '결론',
      body: (
        <>
          <strong>{chain.action}</strong>
          {chain.timing && <span className="legal-timing-badge">{chain.timing}</span>}
        </>
      ),
    });
  }
  if (steps.length === 0) return null;
  return (
    <ol className="legal-chain">
      {steps.map((step) => (
        <li key={step.key}>
          <span className="legal-chain__label">{step.label}</span>
          <div className="legal-chain__body">{step.body}</div>
        </li>
      ))}
    </ol>
  );
}

function CategoryRow({ item, openAnchor }) {
  const anchorId = findingAnchorId(item.category);
  const ref = useRef(null);
  const evidence = evidenceList(item);
  const chain = parseReasoning(item.reasoningJson);
  const sourceSections = parseStringList(item.sourceSectionCodesJson);

  // 할 일 목록의 "근거 범주" 칩이 #legal-cat-… 으로 점프한다 — 접혀 있으면 아무것도 안 보인다.
  useEffect(() => {
    if (openAnchor === anchorId && ref.current) {
      ref.current.open = true;
      ref.current.scrollIntoView({ block: 'center' });
    }
  }, [openAnchor, anchorId]);

  return (
    <details className="legal-verdict-row" id={anchorId} ref={ref}>
      <summary>
        <span className="legal-verdict-row__name">{categoryLabel(item)}</span>
        <span className={`legal-risk-chip legal-risk--${item.riskLevel?.toLowerCase()}`}>
          {RISK_LABELS[item.riskLevel] ?? item.riskLevel}
        </span>
        <span className="legal-muted">
          {APPLICABILITY_LABELS[item.applicability] ?? item.applicability}
        </span>
        {item.requiresProfessionalReview && (
          <span className="legal-verdict-row__flag">전문가 확인</span>
        )}
        {item.carried && <span className="legal-muted">이전 결과 승계</span>}
      </summary>
      <div className="legal-verdict-row__body">
        <p>{item.finding}</p>
        {item.confidence && <p className="legal-muted">신뢰도 {item.confidence}</p>}
        <ReasoningChain chain={chain} />
        {!chain && (
          <>
            {item.rationale && (<><h4>판단 이유</h4><p>{item.rationale}</p></>)}
            {item.recommendedAction && (
              <><h4>권장 행동</h4><p>{item.recommendedAction}</p></>
            )}
            {sourceSections.length > 0 && (
              <p className="legal-source-sections">
                근거가 된 계획서 섹션:{' '}
                {sourceSections.map((code) => SECTION_LABELS[code] ?? code).join(', ')}
              </p>
            )}
          </>
        )}
        {evidence.length > 0 && (
          <>
            <h4>근거 조문 {evidence.length}건</h4>
            <ul className="legal-evidence-list">
              {evidence.map((entry, index) => (
                <EvidenceItem key={`${entry.law}-${entry.article}-${index}`} item={entry} />
              ))}
            </ul>
          </>
        )}
        {item.requiresProfessionalReview && (
          <Alert title="전문가 확인 권장" tone="warning" live={false}>
            실제 적용 여부와 대응 방법을 자격 있는 전문가에게 확인하세요.
          </Alert>
        )}
      </div>
    </details>
  );
}

/**
 * 10개 범주를 하나의 종합 판정으로 모은다. 각 범주는 펼쳐서 근거와 논리를 본다.
 * 범주는 하나도 빠지지 않고 위험도 그룹 안에 나열된다 — 10범주 커버리지 보증.
 */
export function OverallVerdictCard({ findings, openAnchor }) {
  const verdict = buildOverallVerdict(findings);
  return (
    <section aria-labelledby="legal-findings-title">
      <h2 id="legal-findings-title">법 범주별 근거 — 종합 판정</h2>
      <p className="legal-muted">
        10개 범주는 법령을 빠짐없이 훑었는지 보증하는 역할을 합니다. 범주를 펼치면 그 판단에
        이른 근거 조문과 논리를 확인할 수 있습니다.
      </p>
      <Card className="legal-verdict-card">
        <div className="legal-verdict-head">
          <p className="legal-verdict-head__level">
            종합 판정
            <span className={`legal-risk-chip legal-risk--${verdict.worstRiskLevel?.toLowerCase()}`}>
              {RISK_LABELS[verdict.worstRiskLevel] ?? verdict.worstRiskLevel}
            </span>
          </p>
          <p className="legal-muted" aria-label="종합 판정 요약">
            {verdict.total}개 범주 중{' '}
            {verdict.groups.map((group) => `${group.label} ${group.findings.length}`).join(' · ')}
          </p>
          <p className="legal-muted">
            전문가 확인 {verdict.professionalReviewCount}건 · 판매 전 조치 {verdict.actionCount}건
          </p>
        </div>
        {verdict.groups.map((group) => (
          <div key={group.key} className="legal-verdict-group">
            <h3 className={`legal-verdict-group__title legal-risk--${group.key.toLowerCase()}`}>
              {group.label} {group.findings.length}
            </h3>
            {group.findings.map((item) => (
              <CategoryRow key={item.category} item={item} openAnchor={openAnchor} />
            ))}
          </div>
        ))}
      </Card>
    </section>
  );
}
