import { useCallback, useEffect, useRef, useState } from 'react';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { normalizeMarketResult } from './marketResult.js';

const ACTIVE = new Set(['QUEUED', 'READY', 'RUNNING']);
/** 시장조사는 90~266초 걸린다. 여유를 두되 **무한히 기다리지는 않는다.** */
const LIMIT_MS = 10 * 60 * 1000;

/**
 * 「실행 → SSE revision → canonical 결과」 한 벌.
 *
 * <p>interval은 네트워크 polling이 아니라 <b>경과 시간 표시</b>와
 * <b>10분 안내 상한</b>에만 사용한다.
 */
export default function useMarketLiveState(load, start, refreshKey = 0) {
  const [run, setRun] = useState(null);
  const [result, setResult] = useState(null);
  const [source, setSource] = useState(null);
  const [stale, setStale] = useState(false);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [elapsed, setElapsed] = useState(0);
  const startedAt = useRef(null);

  const apply = useCallback((payload) => {
    setRun(payload?.run ?? null);
    setResult(normalizeMarketResult(payload?.version?.result));
    setSource(payload?.source ?? null);
    setStale(Boolean(payload?.stale));
  }, []);

  const refresh = useCallback(async () => {
    try {
      apply(await load());
      setError(null);
    } catch (failure) {
      setError(getUserErrorMessage(failure));
    }
  }, [load, apply, refreshKey]);

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
  }, [load, apply, refreshKey]);

  const active = ACTIVE.has(run?.taskState);

  useEffect(() => {
    if (!active) { startedAt.current = null; return undefined; }
    if (startedAt.current === null) startedAt.current = Date.now();
    // ⚠ effect 본문에서 동기 setState 를 하지 않는다(연쇄 렌더). 0 으로 되돌리는 것은
    //    정리 단계에서 한다 — 다음 실행이 시작될 때 어차피 다시 센다.
    const timer = setInterval(() => {
      const spent = Date.now() - startedAt.current;
      setElapsed(Math.floor(spent / 1000));
      if (spent > LIMIT_MS) {
        clearInterval(timer);
        setError('10분이 지나도 끝나지 않았다 — 실행이 멈췄을 수 있다. 새로고침하거나 다시 실행해 보라.');
        return;
      }
    }, 1000);
    return () => { clearInterval(timer); setElapsed(0); };
  }, [active]);

  const trigger = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      setRun(await start());
      startedAt.current = Date.now();
    } catch (failure) {
      // 실행 중 재실행은 409 가 온다 — 에러가 아니라 「이미 돌고 있다」다.
      const message = getUserErrorMessage(failure);
      setError(String(message).includes('SAME_INPUT_ACTIVE')
        ? '같은 입력으로 이미 실행 중이다.' : message);
    } finally {
      setBusy(false);
    }
  }, [start]);

  return { run, result, source, stale, error, busy, loading, active, elapsed, trigger, refresh };
}
