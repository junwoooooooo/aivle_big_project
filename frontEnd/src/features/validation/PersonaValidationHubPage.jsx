import { Link, useParams } from 'react-router-dom';

import { Badge, Card, PageHeader } from '../../shared/ui/index.js';
import useMarketingContents from '../marketing/hooks/useMarketingContents.js';
import { projectRoutes } from '../projects/routing/projectRoutes.js';
import useValidationData from './hooks/useValidationData.js';
import './validation.css';

export default function PersonaValidationHubPage() {
  const { projectId } = useParams();
  const marketing = useMarketingContents(projectId);
  const interviews = useValidationData(projectId, 'interview', null);
  const markets = useValidationData(projectId, 'market', null);
  const marketingLatest = marketing.items[0];
  const stateFor = (resource, unit) => {
    if (resource.loading) return '상태 확인 중';
    if (resource.error) return '상태 확인 실패';
    const draft = resource.items.find((item) => item.status === 'DRAFT');
    if (draft) return `초안 작성 중 · ${draft[unit]}개`;
    const completed = resource.items.find((item) => item.status === 'COMPLETED');
    if (completed) return `최근 완료 · ${completed[unit]}개`;
    if (resource.items.some((item) => item.status === 'FAILED')) return '최근 실패';
    return '아직 시작하지 않음';
  };
  const preferred = (resource) => resource.items.find((item) => item.status === 'DRAFT')
    ?? resource.items.find((item) => item.status === 'COMPLETED')
    ?? resource.items[0];
  const interviewLatest = preferred(interviews);
  const marketLatest = preferred(markets);
  const cards = [
    {
      title: '패널 인터뷰',
      description: 'Persona별 예상 질문과 답변을 통해 고객의 요구·우려·구매 동기를 확인합니다.',
      status: stateFor(interviews, 'questionCount'),
      tone: interviewLatest?.status === 'COMPLETED' ? 'success' : 'neutral',
      to: interviewLatest?.status === 'COMPLETED'
        ? projectRoutes.interviewDetail(projectId, interviewLatest.id)
        : projectRoutes.interview(projectId),
      action: interviewLatest?.status === 'COMPLETED' ? '결과 보기' : '진행하기',
      updatedAt: interviewLatest?.completedAt ?? interviewLatest?.updatedAt,
      latestTitle: interviewLatest?.title,
      count: interviews.items.length,
    },
    {
      title: '시장 반응 예측',
      description: 'Persona와 검증 결과를 바탕으로 관심도와 메시지 반응을 비교합니다.',
      status: stateFor(markets, 'messageCount'),
      tone: marketLatest?.status === 'COMPLETED' ? 'success' : 'neutral',
      to: marketLatest?.status === 'COMPLETED'
        ? projectRoutes.marketResponseDetail(projectId, marketLatest.id)
        : projectRoutes.marketResponse(projectId),
      action: marketLatest?.status === 'COMPLETED' ? '결과 보기' : '진행하기',
      updatedAt: marketLatest?.completedAt ?? marketLatest?.updatedAt,
      latestTitle: marketLatest?.title,
      count: markets.items.length,
    },
    {
      title: '마케팅 콘텐츠 제작',
      description: '검증 결과를 활용해 광고 카피와 배너·포스터 시안을 제작합니다.',
      status: marketing.loading
        ? '상태 확인 중'
        : marketing.items.length > 0 ? `${marketing.items.length}개 시안` : '아직 제작하지 않음',
      tone: marketing.items.length > 0 ? 'success' : 'info',
      to: projectRoutes.marketing(projectId),
      action: marketing.items.length > 0 ? '결과 보기' : '진행하기',
      updatedAt: marketingLatest?.updatedAt,
      latestTitle: marketingLatest?.title,
      count: marketing.items.length,
    },
  ];
  return (
    <div className="validation-page">
      <PageHeader
        eyebrow="Persona Validation"
        title="페르소나 검증"
        description="고객 관점의 검증부터 반응 예측, 마케팅 시안 제작까지 기능별로 진행합니다."
      />
      <div className="validation-card-grid">
        {cards.map((card) => (
          <Card key={card.title} className="validation-feature-card">
            <Badge tone={card.tone}>{card.status}</Badge>
            <h2>{card.title}</h2>
            <p>{card.description}</p>
            <p>{card.latestTitle ? `최근 결과: ${card.latestTitle}` : '최근 결과 없음'} · 전체 {card.count}건</p>
            <p className="validation-feature-card__time">
              {card.updatedAt ? `최근 저장 ${new Date(card.updatedAt).toLocaleString('ko-KR')}` : '최근 실행 기록 없음'}
            </p>
            <Link className="ui-button ui-button--primary ui-button--medium" to={card.to}>{card.action}</Link>
          </Card>
        ))}
      </div>
      <Card className="validation-persona-link">
        <div><h2>Persona 추천과 선택</h2><p>기존 분석 결과와 군집 Persona 선택 기능은 그대로 유지됩니다.</p></div>
        <Link to={projectRoutes.personas(projectId)}>Persona 결과 보기</Link>
      </Card>
    </div>
  );
}
