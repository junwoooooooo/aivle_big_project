import { useCallback, useEffect, useMemo, useState } from 'react';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { createConceptPortfolioApi } from '../concept-portfolio/api/conceptPortfolioApi.js';

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

export async function waitForRefinementFinalized(
  load,
  {
    attempts = 90,
    intervalMs = 1000,
    sleepFn = sleep,
  } = {},
) {
  let next = await load();

  for (
    let attempt = 0;
    attempt < attempts && !next?.refinement?.finalized;
    attempt += 1
  ) {
    await sleepFn(intervalMs);
    next = await load();
  }

  return next;
}

/**
 * 컨셉 다듬기가 읽고 쓰는 것 — <b>다듬기 결과 · 컨셉 원문 · 법률 델타</b>.
 *
 * <p>2026-08-16 병합에서 `BusinessValidationPage` 에서 떼어 냈다. 다듬기 구획이 그 화면이
 * 아니라 <b>사업 모델 탭</b> 안에 서게 됐고, 훅을 복사하면 두 벌이 조용히 갈린다.
 */
/** 404·409·422 는 「아직 없다」다 — 오류로 세우면 화면이 통째로 빨개진다. */
const optional = (promise) => promise
  .then((payload) => payload ?? null)
  .catch((error) => ([404, 409, 422].includes(error?.status) ? null : Promise.reject(error)));

/**
 * 화면 2 가 읽는 세 가지 — <b>다듬기 결과 · 컨셉 원문 · 법률 보고서</b>.
 *
 * <p>새 계약을 만들지 않는다. 셋 다 이미 있는 조회다. 다만 셋이 모두
 * <b>`selectionId` 를 먼저 알아야</b> 해서 한 번의 대기는 피할 수 없다 — 그다음 셋은
 * 한꺼번에 간다(직렬로 늘어놓으면 폭포가 된다).
 */
export function useConceptRevision(
  client,
  marketApi,
  projectId,
  enabled,
  liveRevision = 0,
) {
  const portfolio = useMemo(() => createConceptPortfolioApi(client), [client]);
  const [state, setState] = useState({ loading: true, selectionId: null, refinement: null, concept: null, error: null });
  const [finalizing, setFinalizing] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const [deciding, setDeciding] = useState(false);

  // ⚠ **전체 법률보고서(`portfolio.report`)를 더는 읽지 않는다.** 이 화면의 법률 칸은
  //    다듬기가 «바꾼 것»에 걸리는 법만 보인다 — 그 델타는 `/concept-refinement` 응답의
  //    `deltaLegal` 이 준다. 전체 보고서를 읽으면 안 바뀐 조항까지 8건이 늘어서 부분 검사가
  //    전체 검사로 보인다(2026-08-13 반려).
  const load = useCallback(async () => {
    const selection = (await optional(portfolio.currentSelection(projectId)))?.data ?? null;
    const selectionId = selection?.selectionId ?? null;
    if (!selectionId) return { loading: false, selectionId: null, refinement: null, concept: null, error: null };
    const [refinement, seed] = await Promise.all([
      optional(marketApi.currentRefinement(selectionId)),
      optional(portfolio.marketSeed(projectId, selectionId)),
    ]);
    return {
      loading: false,
      selectionId,
      refinement,
      concept: seed?.data?.snapshot ?? null,
      error: null,
    };
  }, [portfolio, marketApi, projectId]);

  useEffect(() => {
    if (!enabled) return undefined;
    let alive = true;
    load()
      .then((next) => { if (alive) setState(next); })
      .catch((failure) => {
        if (alive) setState((value) => ({ ...value, loading: false, error: getUserErrorMessage(failure) }));
      });
    return () => { alive = false; };
  }, [enabled, load, liveRevision]);

  /**
   * <b>돌고 있을 때만 다시 읽는다.</b>
   *
   * <p>⚠ 사람이 고르는 문이 생기며 <b>이 화면이 왕복 대화가 됐다</b>. 자동 적용 시절에는
   * 결과 열람이라 한 번 읽으면 끝이었는데, 이제 사용자가 「반영하기」를 누르면 서버가
   * 법률 델타를 걸고 화면은 「법률 검토를 기다리고 있어요」가 된다 — 그 상태로 <b>영영
   * 안 바뀌었다.</b> 법률이 끝나도, 다음 라운드 제안이 와도 사용자는 모르고, 나갔다
   * 들어오라는 안내조차 없었다.
   *
   * <p>⚠ <b>돌 때만 돈다.</b> 결말이 난 화면에서 계속 두드리면 아무것도 안 바뀌는 조회로
   * 서버를 때린다. 유료 호출을 «만드는» 것이 아니라 이미 도는 것을 «보는» 것뿐이다.
   */
  // ⚠ `DECISION_NOT_APPLIED` 는 «기다리는 상태가 아니다» — 워커가 그 라운드에서 다음을
  //    자동으로 걸지 않으므로(유료 호출이라 일부러 막았다) 사용자가 누르기 전엔 안 바뀐다.
  //    거기서 폴링하면 아무것도 안 바뀌는 조회로 서버만 두드린다.
  const 도는중 = state.refinement?.outcome === 'RUNNING';
  useEffect(() => {
    if (!enabled || !도는중) return undefined;
    let alive = true;
    const timer = setInterval(() => {
      load().then((next) => { if (alive) setState(next); }).catch(() => {});
    }, 5000);
    return () => { alive = false; clearInterval(timer); };
  }, [enabled, 도는중, load]);

  /**
   * <b>시장 검증 후 최종 확정.</b> 확정하고 나면 다시 읽는다 — 서버가 법률보고서 재확정과
   * 시드 재발급을 순서대로 태우므로 화면의 값이 바뀐다.
   */
  const finalize = useCallback(async () => {
    if (!state.selectionId) return;

    setFinalizing(true);

    try {
      await marketApi.finalizeRefinedConcept(
        state.selectionId,
        `refine-finalize-${state.selectionId}`,
      );

      const next = await waitForRefinementFinalized(load);

      setState(
        next?.refinement?.finalized
          ? next
          : {
              ...next,
              error:
                '컨셉 확정 작업이 아직 진행 중입니다. 완료되면 화면이 자동으로 갱신됩니다.',
            },
      );
    } catch (failure) {
      setState((value) => ({
        ...value,
        error: getUserErrorMessage(failure),
      }));
    } finally {
      setFinalizing(false);
    }
  }, [marketApi, state.selectionId, load]);

  /**
   * <b>실패한 다듬기 라운드를 다시 건다.</b>
   *
   * <p>걸고 나면 다시 읽는다 — 새 실행이 서면 화면의 판정이 FAILED 에서 RUNNING 으로 바뀐다.
   * 서버가 거절하면(돌고 있음·이미 됨·시도 상한) 그 사유를 그대로 띄운다. <b>조용히 넘기지
   * 않는다</b> — 아무 일도 안 일어나면 사용자는 버튼이 고장 났다고 읽는다.
   */
  const retry = useCallback(async () => {
    if (!state.selectionId) return;
    setRetrying(true);
    try {
      await marketApi.retryRefinement(state.selectionId);
      setState(await load());
    } catch (failure) {
      setState((value) => ({ ...value, error: getUserErrorMessage(failure) }));
    } finally {
      setRetrying(false);
    }
  }, [marketApi, state.selectionId, load]);

  /**
   * <b>사람이 고른 것만 반영한다.</b> 이 화면이 존재하는 이유다.
   *
   * <p>빈 목록이면 「전부 넘김」이고, 그때도 서버는 그것을 <b>답으로 기록</b>한다 —
   * 「아직 안 골랐다」와 「전부 넘겼다」는 다른 사실이라 뭉개면 안 된다.
   */
  const decide = useCallback(async (round, fieldKeys) => {
    if (!state.selectionId) return;
    setDeciding(true);
    try {
      await marketApi.decideRefinement(state.selectionId, round, fieldKeys,
        `refine-decide-${state.selectionId}-${round}`);
      setState(await load());
    } catch (failure) {
      setState((value) => ({ ...value, error: getUserErrorMessage(failure) }));
    } finally {
      setDeciding(false);
    }
  }, [marketApi, state.selectionId, load]);

  return { ...state, finalizing, finalize, retrying, retry, deciding, decide };
}
