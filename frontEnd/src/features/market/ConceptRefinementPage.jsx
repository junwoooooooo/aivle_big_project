import { useCallback, useMemo } from 'react';
import { useNavigate, useOutletContext, useParams } from 'react-router-dom';
import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { createMarketApi } from './marketApi.js';
import { projectRoutes } from '../../app/routing/projectRoutes.js';
import { Alert, LoadingState, ProjectStageHeader, ProjectWorkspace } from '../../shared/ui';
import RefinementSummary from './RefinementSummary.jsx';
import { useConceptRevision } from './useConceptRevision.js';
import useMarketLiveState from './useMarketPolling.js';
import { evidenceSubjectIndex } from './marketResult.js';
import './market.css';

/**
 * 사업 검증의 <b>셋째 걸음</b> — 컨셉 다듬기.
 *
 * <p>읽는 순서가 곧 걸음이다: 시장 분석(무엇이 관측됐나) → 사업 모델(그 사업이 서나) →
 * <b>다듬어진 컨셉</b>(그래서 사업안을 어떻게 고칠까). 앞의 둘을 본 «뒤»에 오는 판단이라
 * 캔버스 아래 구획으로 접어 두면 다 내려간 사람만 본다.
 *
 * <p><b>이 화면은 스스로 실행을 걸지 않는다.</b> 다듬기 첫 라운드는 BM 채택이 걸어 준다
 * ({@code MarketResearchWorker.REFINEMENT_TRIGGER_SUBJECT}). 재료인 캔버스와 게이트 사유가
 * BM 실행에서만 생기기 때문이다. 그래서 여기 「실행」 버튼이 없는 것이 정상이다 —
 * 사용자가 하는 일은 <b>제안을 고르는 것</b>이다.
 *
 * <p>⚠ 근거 원문은 <b>시장조사 결과</b>에 있다. 그것을 다시 부르지 않고
 * {@code useMarketLiveState} 가 이미 읽어 둔 BM 봉투에서 꺼낸다 — 두 벌로 읽으면
 * 「어느 판을 근거로 골랐나」가 조용히 갈린다.
 */
export default function ConceptRefinementPage() {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const client = useApiClient();
  const { liveRevision = 0 } = useOutletContext() ?? {};
  const api = useMemo(() => createMarketApi(client, projectId), [client, projectId]);

  // 근거 원문만 쓰려고 BM 결과를 읽는다. 실행은 걸지 않으므로 `start` 는 아무것도 안 한다.
  const load = useCallback(() => api.currentBusinessModel(), [api]);
  const noop = useCallback(async () => undefined, []);
  const { result, loading } = useMarketLiveState(load, noop, liveRevision);
  const revision = useConceptRevision(client, api, projectId, true);
  const evidenceSubjects = useMemo(
    () => (result ? evidenceSubjectIndex(result) : new Map()), [result]);

  if (loading || revision.loading) return <LoadingState label="다듬어진 컨셉을 불러오는 중" />;

  return (
    <ProjectWorkspace as="section" mode="analyze" className="market-page">
      <ProjectStageHeader step={3} eyebrow="컨셉 다듬기"
        title="조사 결과를 사업안에 어떻게 반영할지 고르세요"
        description="고른 것만 컨셉에 들어갑니다. 넘긴 제안도 기록으로 남습니다." />

      {/* ⚠ 다듬기가 아직 안 걸린 것과 «고칠 것이 없는 것»은 다른 사건이다.
          BM 을 아직 안 돌렸으면 그 사실을 말한다 — 빈 화면으로 두면 고장으로 읽힌다. */}
      {!revision.selectionId ? (
        <Alert tone="info">
          확정된 사업안이 아직 없어요. 사업안을 고르고 확정한 뒤에 다듬기가 시작돼요.
        </Alert>
      ) : null}

      <RefinementSummary
        result={revision.refinement}
        concept={revision.concept}
        evidenceSubjects={evidenceSubjects}
        evidenceById={result?.evidenceById ?? null}
        /* 근거 원문은 시장 분석 탭에 있다 — 같은 앵커 접두사(`sec-`)로 건너간다. */
        onJumpSubject={(anchor) => navigate(`${projectRoutes.market(projectId)}#${anchor}`)}
        onBack={() => navigate(projectRoutes.businessModel(projectId))}
        onNext={() => navigate(projectRoutes.techOps(projectId))}
        onFinalize={revision.selectionId ? revision.finalize : null}
        finalizing={revision.finalizing}
        onRetry={revision.selectionId ? revision.retry : null}
        retrying={revision.retrying}
        onDecide={revision.selectionId ? revision.decide : null}
        deciding={revision.deciding}
        error={revision.error}
      />
    </ProjectWorkspace>
  );
}
