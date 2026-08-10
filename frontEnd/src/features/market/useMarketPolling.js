import { useCallback, useEffect, useRef, useState } from 'react';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { normalizeMarketResult } from './marketResult.js';

const ACTIVE = new Set(['QUEUED', 'READY', 'RUNNING']);
/** 시장조사는 90~266초 걸린다. 여유를 두되 **무한히 기다리지는 않는다.** */
const LIMIT_MS = 10 * 60 * 1000;
const INTERVAL_MS = 2000;

/**
 * 「실행 → 폴링 → 결과」 한 벌.
 *
 * <p>legal 화면의 2초 폴링 패턴을 따르되 두 가지를 더한다:
 * <b>경과 시간 표시</b>와 <b>10분 상한</b>. 4분 넘게 아무 표시가 없으면 사용자는
 * 멈춘 줄 알고, 상한이 없으면 죽은 작업을 영원히 조회한다.
 */
export default function useMarketPolling(load, start) {
  const [run, setRun] = useState(null);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [elapsed, setElapsed] = useState(0);
  const startedAt = useRef(null);

  const apply = useCallback((payload) => {
    setRun(payload?.run ?? null);
    setResult(normalizeMarketResult(payload?.version?.result));
  }, []);

  const refresh = useCallback(async () => {
    try {
      apply(await load());
      setError(null);
    } catch (failure) {
      setError(getUserErrorMessage(failure));
    }
  }, [load, apply]);

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const payload = await load();
        if (alive) apply(payload);
      } catch (failure) {
        if (alive) setError(getUserErrorMessage(failure));
      } finally {
        if (alive) setLoading(false);
      }
    })();
    return () => { alive = false; };
  }, [load, apply]);

  const active = ACTIVE.has(run?.taskState);

  useEffect(() => {
    if (run?.state !== 'FAILED') return;
    // Show an actionable, safe failure record in DevTools without logging input text or secrets.
    console.error('[market-research] execution failed', {
      taskRunId: run.taskRunId,
      code: run.errorCode,
      reason: run.errorReason ?? null,
      retryable: run.retryable,
    });
  }, [run?.state, run?.taskRunId, run?.errorCode, run?.errorReason, run?.retryable]);

  useEffect(() => {
    if (!active) { startedAt.current = null; return undefined; }
    if (startedAt.current === null) startedAt.current = Date.now();
    // ⚠ effect 본문에서 동기 setState 를 하지 않는다(연쇄 렌더). 0 으로 되돌리는 것은
    //    정리 단계에서 한다 — 다음 실행이 시작될 때 어차피 다시 센다.
    const timer = setInterval(async () => {
      const spent = Date.now() - startedAt.current;
      setElapsed(Math.floor(spent / 1000));
      if (spent > LIMIT_MS) {
        clearInterval(timer);
        setError('10분이 지나도 끝나지 않았다 — 실행이 멈췄을 수 있다. 새로고침하거나 다시 실행해 보라.');
        return;
      }
      await refresh();
    }, INTERVAL_MS);
    return () => { clearInterval(timer); setElapsed(0); };
  }, [active, refresh]);

  const trigger = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      setRun(await start());
      startedAt.current = Date.now();
    } catch (failure) {
      console.error('[market-research] start request failed', {
        code: failure?.code,
        status: failure?.status,
        fieldErrors: failure?.fieldErrors,
      });
      // 실행 중 재실행은 409 가 온다 — 에러가 아니라 「이미 돌고 있다」다.
      const message = getUserErrorMessage(failure);
      setError(String(message).includes('SAME_INPUT_ACTIVE')
        ? '같은 입력으로 이미 실행 중이다.' : message);
    } finally {
      setBusy(false);
    }
  }, [start]);

  return { run, result, error, busy, loading, active, elapsed, trigger, refresh };
}
