import { useCallback, useState } from 'react';

/**
 * 요약 → 세부로 착지.
 *
 * <p>요약 격자(`BmCanvas`)와 칸별 세부(`BmCellDetails`)가 화면에서 떨어져 있어서
 * 강조 상태를 쥐고 있을 곳이 필요하다. 페이지가 쥔다.
 *
 * ⚠ 세 가지가 이 한 줄을 망가뜨린다. **셋 다 조용히 실패한다** — 예외도 경고도 없이
 * 착지만 사라지므로, 눈으로 보지 않으면 멀쩡해 보인다:
 *
 *   1. 칸이 `<button>` 이라 클릭이 포커스를 만든다. 브라우저는 포커스 받은 요소를 화면
 *      안으로 되돌리는데, 그게 우리 스크롤보다 나중에 일어나 **되감긴다.**
 *      → 포커스를 떼고 다음 프레임에 움직인다.
 *   2. 그 «다음 프레임»을 `requestAnimationFrame` 으로 잡으면 **숨은 탭에서 영영 안 온다**
 *      (`document.hidden` 일 때 rAF 콜백 0회). 그러면 착지가 통째로 사라진다.
 *      → `setTimeout(…, 0)` 은 순서는 같고 가시성에 매이지 않는다.
 *   3. **`behavior:'smooth'` 가 시작조차 안 하는 경우가 있다.** 실측(Chrome, 이 화면):
 *      진짜 마우스 클릭 뒤의 smooth 는 0px 움직이고 그대로 멈춘다. 같은 자리에서
 *      `behavior` 없이 부르면 즉시 2,456px 이동한다. `prefers-reduced-motion` 도,
 *      React 재렌더도, blur 도 아니다 — 순수 DOM 버튼으로도 재현된다.
 *      → 시작했는지 **확인하고**, 안 움직였으면 즉시 이동한다.
 *
 * @param prefix 착지 대상의 element id 접두사 (`bm-` → `bm-CHANNELS`)
 */
export default function useCellFocus(prefix = '') {
  const [active, setActive] = useState(null);

  const jump = useCallback((key) => {
    setActive(key);
    const el = document.getElementById(`${prefix}${key}`);
    if (!el) return;
    document.activeElement?.blur?.();
    setTimeout(() => {
      const before = window.scrollY;
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
      // 애니메이션이 붙었는지 한 번 본다. 안 붙었으면 부드러움을 포기한다 —
      // 착지가 조용히 사라지는 것보다 덜컥 도착하는 편이 낫다.
      setTimeout(() => {
        if (Math.abs(window.scrollY - before) < 2) el.scrollIntoView({ block: 'start' });
      }, 150);
    }, 0);
  }, [prefix]);

  return { active, jump };
}
