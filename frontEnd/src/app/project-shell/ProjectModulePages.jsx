import { Link, useOutletContext } from 'react-router-dom';

import { AppIcon } from '../../shared/ui/index.js';
import { getJourneyActionView, getJourneyStatusView, JOURNEY_STATUS } from '../module-status/projectJourneyModel.js';

const JOURNEY_DESCRIPTIONS = Object.freeze({
  planning: '아이디어를 정리하고 비교해 실행할 사업안을 확정합니다.',
  validation: '시장 근거와 사업 모델을 함께 검증합니다.',
  launch: '기술·운영 계획과 재무 전망으로 출시 준비 상태를 확인합니다.',
  interview: '시장 인터뷰의 정성 탐색과 트윈 패널의 정량 시뮬레이션을 진행합니다.',
  marketingStrategy: '현재 사업안을 바탕으로 검토할 마케팅 초안을 준비합니다.',
  finalReport: '앞선 결과를 한 문서로 정리해 의사결정에 활용합니다.',
});

const JOURNEY_POSITIONS = Object.freeze([
  ['8%', '50%'], ['25%', '27.78%'], ['42%', '51.94%'],
  ['58%', '26.11%'], ['75%', '50%'], ['92%', '31.94%'],
]);

function JourneyStatusBadge({ status }) {
  const view = getJourneyStatusView(status);
  return <span className="pipeline-status" data-tone={view.tone}>{view.label}</span>;
}

export function ProjectOverviewPage() {
  const { journeys } = useOutletContext();
  const currentIndex = journeys.findIndex(({ status }) => status !== JOURNEY_STATUS.COMPLETED);
  return <section className="pipeline-overview" aria-labelledby="project-overview-title">
    <div className="pipeline-page-heading"><p>6단계 사업 여정</p><h2 id="project-overview-title">프로젝트 개요</h2><span>현재 위치와 다음 할 일을 하나의 흐름에서 확인하세요.</span></div>
    <div className="journey-map"><svg className="journey-map__path" viewBox="0 0 1000 360" preserveAspectRatio="none" aria-hidden="true"><path d="M80 180 C140 180 190 100 250 100 S360 187 420 187 S520 94 580 94 S690 180 750 180 S860 115 920 115" /></svg><ol>{journeys.map((journey, index) => {
      const action = getJourneyActionView(journey.status);
      const [x, y] = JOURNEY_POSITIONS[index];
      return <li key={journey.id} style={{ '--station-x': x, '--station-y': y }} className={`${index === currentIndex ? 'is-current' : ''} is-${journey.status.toLowerCase()}`}>
        <Link className="journey-map__node-link" to={journey.href} aria-label={`${journey.shortLabel} ${action}`} title={action}>
          <span className="journey-map__station" aria-hidden="true">{journey.status === JOURNEY_STATUS.COMPLETED ? <AppIcon name="check" size={17} /> : index + 1}</span>
          <span className="journey-map__content"><span className="journey-map__step">{index + 1}단계</span><strong>{journey.shortLabel}</strong><span>{JOURNEY_DESCRIPTIONS[journey.id]}</span><JourneyStatusBadge status={journey.status} /></span>
          <span className="journey-map__action" aria-hidden="true"><AppIcon name="arrowUpRight" size={18} /></span>
        </Link>
      </li>;
    })}</ol></div>
  </section>;
}
