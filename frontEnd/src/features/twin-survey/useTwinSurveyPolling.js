import { useCallback, useEffect, useRef, useState } from 'react';

import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { normalizeTwinSurvey } from './twinSurveyResult.js';

const ACTIVE = new Set(['QUEUED', 'READY', 'RUNNING']);
/**
 * 서버 예산이 12분이다. 상한을 그보다 넉넉히 두되 **무한히 기다리지는 않는다** —
 * 상한이 없으면 죽은 작업을 영원히 조회한다.
 */
const LIMIT_MS = 20 * 60 * 1000;
const INTERVAL_MS = 2000;

/** 「실행 → 폴링 → 결과」 한 벌. {@code useMarketPolling} 과 같은 모양이고 상한만 다르다. */
export default function useTwinSurveyPolling(load, start) {
  const [run, setRun] = useState(null);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [elapsed, setElapsed] = useState(0);
  const startedAt = useRef(null);

  const apply = useCallback((payload) => {
    setRun(payload?.run ?? null);
    setResult(normalizeTwinSurvey(payload?.version?.result));
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
    if (!active) { startedAt.current = null; return undefined; }
    if (startedAt.current === null) startedAt.current = Date.now();
    const timer = setInterval(async () => {
      const spent = Date.now() - startedAt.current;
      setElapsed(Math.floor(spent / 1000));
      if (spent > LIMIT_MS) {
        clearInterval(timer);
        setError('20분이 지나도 끝나지 않았다 — 실행이 멈췄을 수 있다. 새로고침하거나 다시 실행해 보라.');
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
      const message = getUserErrorMessage(failure);
      setError(String(message).includes('SAME_INPUT_ACTIVE')
        ? '같은 자극으로 이미 실행 중이다.' : message);
    } finally {
      setBusy(false);
    }
  }, [start]);

  return { run, result, error, busy, loading, active, elapsed, trigger, refresh };
}
