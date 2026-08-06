import { Link, useParams } from 'react-router-dom';

import { Alert, Card } from '../../../shared/ui/index.js';
import { projectRoutes } from '../../projects/routing/projectRoutes.js';
import { scoreLabel } from '../model/validationModel.js';

const METRICS = {
  interest: '관심도',
  clarity: '메시지 이해도',
  trust: '신뢰도',
  usageIntent: '사용 의향',
  conversionIntent: '구매·신청 의향',
  sharingIntent: '공유·추천 의향',
};

export default function MarketResponseResult({ detail }) {
  const { projectId } = useParams();
  const summary = detail.summary ?? {};
  const sourceQuery = new URLSearchParams({
    marketResponseId: String(detail.prediction.id),
    ...(detail.prediction.panelInterviewId
      ? { panelInterviewId: String(detail.prediction.panelInterviewId) }
      : {}),
  }).toString();
  return (
    <div className="validation-result">
      <Alert title="검증 데이터 기반 예상 시장 반응" tone="warning">{detail.disclaimer}</Alert>
      <section className="validation-market-summary">
        <Card><span>가장 반응이 좋은 Persona</span><strong>{summary.bestPersona}</strong></Card>
        <Card><span>가장 반응이 좋은 메시지</span><strong>{summary.bestMessage}</strong></Card>
        <Card><span>추천 CTA</span><strong>{summary.recommendedCta}</strong></Card>
        <Card><span>추천 채널</span><strong>{summary.recommendedChannel}</strong></Card>
      </section>
      <div className="validation-market-results">
        {(detail.results ?? []).map((result) => (
          <Card key={`${result.personaId}-${result.messageId}`} className="validation-score-card">
            <header><h2>{result.personaName}</h2><span>메시지 {result.messageId}</span></header>
            <blockquote>{result.message}</blockquote>
            <dl>
              {Object.entries(result.scores).map(([metric, score]) => (
                <div key={metric}>
                  <dt>{METRICS[metric] ?? metric}</dt>
                  <dd>
                    <progress max="100" value={score}>{score}</progress>
                    <strong>{score} · {scoreLabel(score)}</strong>
                  </dd>
                </div>
              ))}
            </dl>
            <div className="validation-factor-grid">
              <section><h3>긍정 요인</h3><ul>{result.positiveFactors.map((factor) => <li key={factor}>{factor}</li>)}</ul></section>
              <section><h3>부정 요인</h3><ul>{result.negativeFactors.map((factor) => <li key={factor}>{factor}</li>)}</ul></section>
              <section><h3>개선 제안</h3><ul>{result.recommendedChanges.map((factor) => <li key={factor}>{factor}</li>)}</ul></section>
            </div>
          </Card>
        ))}
      </div>
      <div className="validation-result-actions">
        <Link to={`${projectRoutes.marketingNew(projectId)}?${sourceQuery}`}>이 결과로 마케팅 콘텐츠 만들기</Link>
      </div>
    </div>
  );
}
