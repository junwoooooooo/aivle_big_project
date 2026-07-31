import { useCallback, useState } from 'react';

/**
 * 할 일 체크 상태 — 이 브라우저에만 저장된다(localStorage).
 * 사이클 id가 있으면 사이클 단위로 저장해 재검토(버전 증가)에도 진행 상태가 유지된다.
 * 사이클 정보가 없으면 기존 버전 키로 동작한다(하위호환).
 */
export function useLegalChecklist(projectId, versionNumber, cycleId) {
  const storageKey = cycleId
    ? `legal-checklist:${projectId}:cycle:${cycleId}`
    : `legal-checklist:${projectId}:v${versionNumber}`;
  const [checked, setChecked] = useState(() => {
    try {
      const stored = JSON.parse(localStorage.getItem(storageKey) ?? '[]');
      if (Array.isArray(stored) && stored.length > 0) return new Set(stored);
      // 사이클 키가 비어 있으면 직전 버전 키에서 1회 이어받는다 (기존 사용자 진행 보존)
      if (cycleId && versionNumber) {
        const legacy = JSON.parse(
          localStorage.getItem(`legal-checklist:${projectId}:v${versionNumber}`) ?? '[]',
        );
        if (Array.isArray(legacy) && legacy.length > 0) return new Set(legacy);
      }
      return new Set();
    } catch {
      return new Set();
    }
  });

  const toggle = useCallback((action) => {
    setChecked((current) => {
      const next = new Set(current);
      if (next.has(action)) next.delete(action);
      else next.add(action);
      try {
        localStorage.setItem(storageKey, JSON.stringify([...next]));
      } catch {
        // 저장 불가 환경(프라이빗 모드 등)에서는 세션 내 상태만 유지한다.
      }
      return next;
    });
  }, [storageKey]);

  const isChecked = useCallback((action) => checked.has(action), [checked]);

  return { isChecked, toggle, checkedActions: [...checked] };
}
