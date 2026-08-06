import { Link, useParams } from 'react-router-dom';

import { Alert, Badge, Card } from '../../../shared/ui/index.js';
import { projectRoutes } from '../../projects/routing/projectRoutes.js';
import { groupAnswersByPersona } from '../model/validationModel.js';

const SENTIMENT = {
  POSITIVE: ['긍정', 'success'],
  NEUTRAL: ['중립', 'neutral'],
  NEGATIVE: ['부정', 'danger'],
  MIXED: ['복합', 'warning'],
};

function SummaryList({ title, items }) {
  return (
    <Card><h3>{title}</h3><ul>{(items ?? []).map((item) => <li key={item}>{item}</li>)}</ul></Card>
  );
}

export default function PanelInterviewResult({ detail }) {
  const { projectId } = useParams();
  const summary = detail.summary ?? {};
  const answersByPersona = groupAnswersByPersona(detail.answers);
  const personaQuery = (detail.personaIds ?? [])
    .filter(Boolean)
    .join(',');
  const marketQuery = new URLSearchParams({
    panelInterviewId: String(detail.interview.id),
    ...(personaQuery ? { personaIds: personaQuery } : {}),
  }).toString();
  return (
    <div className="validation-result">
      <Alert title="페르소나 기반 예상 인터뷰" tone="warning">{detail.disclaimer}</Alert>
      <section className="validation-summary-grid" aria-label="인터뷰 핵심 요약">
        <SummaryList title="공통 요구" items={summary.commonNeeds} />
        <SummaryList title="주요 우려" items={summary.concerns} />
        <SummaryList title="구매 동기" items={summary.purchaseMotivations} />
        <SummaryList title="거부 요인" items={summary.rejectionFactors} />
      </section>
      {Object.entries(answersByPersona).map(([personaName, answers]) => (
        <Card key={personaName} className="validation-persona-result">
          <h2>{personaName}</h2>
          {answers.map((answer) => {
            const [label, tone] = SENTIMENT[answer.sentiment] ?? SENTIMENT.NEUTRAL;
            return (
              <details key={`${answer.personaId}-${answer.questionOrder}`}>
                <summary><span>Q{answer.questionOrder}. {answer.question}</span><Badge tone={tone}>{label}</Badge></summary>
                <p>{answer.answer}</p>
                <ul>{answer.keyPoints.map((point) => <li key={point}>{point}</li>)}</ul>
              </details>
            );
          })}
        </Card>
      ))}
      <div className="validation-result-actions">
        <Link to={`${projectRoutes.marketResponse(projectId)}?${marketQuery}`}>이 결과로 시장 반응 예측하기</Link>
        <Link to={`${projectRoutes.marketingNew(projectId)}?panelInterviewId=${detail.interview.id}`}>이 결과로 마케팅 콘텐츠 만들기</Link>
      </div>
    </div>
  );
}
