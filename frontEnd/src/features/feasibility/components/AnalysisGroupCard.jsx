import { Card } from '../../../shared/ui/index.js';
import {
  CONFIDENCE_LABELS, DIMENSION_LABELS, EVIDENCE_TYPE_LABELS, VERDICT_LABELS, parseJsonList,
} from '../model/feasibilityViewModel.js';

function EvidenceList({ value }) {
  const evidence = parseJsonList(value);
  if (evidence.length === 0) return <p className="feasibility-muted">표시할 근거가 없습니다.</p>;
  return (
    <ul className="feasibility-evidence">
      {evidence.map((item, index) => (
        <li key={`${item.type}-${item.reference}-${index}`}>
          <strong>{EVIDENCE_TYPE_LABELS[item.type] ?? item.type}</strong>
          <span>{item.description}</span>
          {item.reference && <small>{item.reference}</small>}
        </li>
      ))}
    </ul>
  );
}

/** 묶음에 속한 차원 하나 — 접어 두고 필요할 때 펼친다. */
function DimensionRow({ item }) {
  const actions = parseJsonList(item.recommendedActionsJson);
  const risks = parseJsonList(item.risksJson);
  return (
    <details className="feasibility-dimension-row">
      <summary>
        <span className="feasibility-dimension-row__name">
          {DIMENSION_LABELS[item.code] ?? item.code}
        </span>
        <span className="feasibility-dimension-row__score">
          {item.score ?? '정보 부족'}
        </span>
        <span className="feasibility-muted">
          신뢰도 {CONFIDENCE_LABELS[item.confidence] ?? item.confidence}
        </span>
      </summary>
      <div className="feasibility-dimension-row__body">
        <p>{item.finding}</p>
        <h5>판단 이유</h5><p>{item.rationale}</p>
        <h5>근거 구분</h5><EvidenceList value={item.evidenceJson} />
        {risks.length > 0 && (
          <>
            <h5>위험</h5>
            <ul>{risks.map((risk) => <li key={risk}>{risk}</li>)}</ul>
          </>
        )}
        {actions.length > 0 && (
          <>
            <h5>권장 행동</h5>
            <ul>{actions.map((action) => <li key={action}>{action}</li>)}</ul>
          </>
        )}
      </div>
    </details>
  );
}

/**
 * 시장·비즈니스 모델·기술 운영 묶음 하나를 큰 카드로 보여준다.
 * 묶음 결론이 먼저 오고, 그 판단을 구성한 차원은 접어 둔다.
 */
export function AnalysisGroupCard({ group }) {
  return (
    <Card
      className="feasibility-group"
      aria-labelledby={`feasibility-group-${group.analysisType}`}
    >
      <div className="feasibility-group__head">
        <div>
          <h3 id={`feasibility-group-${group.analysisType}`}>{group.label}</h3>
          <p className="feasibility-muted">{group.description}</p>
        </div>
        <div className="feasibility-group__score">
          {/* 점수 미상이면 숫자 자리는 비운다 — 판정 칩이 이미 '정보 부족'을 말한다 */}
          <span className="feasibility-group__number" aria-label="묶음 점수">
            {group.score ?? '—'}
          </span>
          {group.verdict && (
            <span className={`feasibility-verdict feasibility-verdict--${group.verdict.toLowerCase()}`}>
              {VERDICT_LABELS[group.verdict] ?? group.verdict}
            </span>
          )}
        </div>
      </div>

      {group.headline && <p className="feasibility-group__headline">{group.headline}</p>}
      {group.summary && <p>{group.summary}</p>}

      {(group.strengths.length > 0 || group.risks.length > 0) && (
        <div className="feasibility-group__grid">
          {group.strengths.length > 0 && (
            <div>
              <h4>확인된 강점</h4>
              <ul>{group.strengths.map((item) => <li key={item}>{item}</li>)}</ul>
            </div>
          )}
          {group.risks.length > 0 && (
            <div>
              <h4>주요 위험</h4>
              <ul>{group.risks.map((item) => <li key={item}>{item}</li>)}</ul>
            </div>
          )}
        </div>
      )}

      {group.nextFocus && (
        <p className="feasibility-group__focus">
          <span className="feasibility-group__focus-tag">먼저 할 일</span>
          {group.nextFocus}
        </p>
      )}

      <div className="feasibility-group__dimensions">
        <h4>이 판단을 구성한 항목 {group.dimensions.length}개</h4>
        {group.dimensions.map((item) => <DimensionRow key={item.code} item={item} />)}
      </div>
    </Card>
  );
}
