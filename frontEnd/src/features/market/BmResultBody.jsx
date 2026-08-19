import { useState } from 'react';
import BmCanvas, { BmCellDetails } from './BmCanvas.jsx';
import { Alert } from '../../shared/ui';
import './market.css';


/**
 * 사업 검증의 <b>둘째 걸음</b> — BM 캔버스. 첫째 걸음(시장조사)의 결과를 근거로 채운다.
 *
 * <p>읽는 순서: 판정 → 9칸 요약 → 강점·약점·위험.
 *
 * <p>⚠ <b>칸별 세부는 「누른 칸 하나」만 편다.</b> 2026-08-13 에는 통째로 뺐었다 — 근거표가
 * 9칸 × 전 항목으로 되풀이돼 화면이 길어졌기 때문이고, <b>그 이유는 지금도 유효하다.</b>
 * 그래서 「전부 펴기」로 되돌리지 않는다.
 *
 * <p>그런데 빼 두는 동안 다른 문제가 생겼다: 캔버스가 「채널 · 근거 12건」이라고 말하는데
 * <b>그 12건이 뭔지 열어볼 길이 없었다.</b> 배지와 숫자만 주고 근거를 안 보이면 그것은
 * 「믿으라」는 말이고, 이 단계가 하려는 일(근거로 반증한다)과 정면으로 어긋난다.
 * 그래서 «누른 칸 하나»만 되살렸다.
 *
 * <p>셸(제목·실행 버튼·진행 표시)은 갖지 않는다. `BusinessValidationPage` 가 갖는다.
 */
export function BmResultBody({ result }) {
  const bm = result?.bm ?? null;
  // 한 번에 하나만. 다시 누르면 닫힌다 — 닫을 길이 없으면 화면이 계속 길어진다.
  const [openCell, setOpenCell] = useState(null);
  const toggle = (key) => setOpenCell((now) => (now === key ? null : key));
  const opened = (result?.canvas ?? []).find((cell) => cell.cell === openCell) ?? null;

  return (
    <>
      {/* 판 ㊻ — **게이트 사유 상자를 뺐다**(2026-08-16 사용자 지시).
          같은 사실이 아래에서 다시 말해진다 — 근거가 0건인 칸은 캔버스에서 회색 점과
          「근거 필요」로 서고, 그 칸을 누르면 사유가 그대로 나온다.
          ⚠ **잃는 것을 적어 둔다.** 갈래(`cause`)는 이제 화면에 없다 —
            「못 찾음」(컨셉을 고쳐도 안 고쳐진다)과 「확인 못 함」(자료는 있다)의 구분이
            그것이었고, 그 구분이 없으면 **컨셉을 다듬어 수집 실패를 통과시키는 길**이
            열린다. 되살릴 자리는 이 상자가 아니라 **캔버스 칸의 「자세히」 안**이다. */}

      {/* 판 ㊻ — **판정 카드를 통째로 뺐다**(2026-08-16 사용자 지시).
          먼저 부분 검사 줄을 뺐고, 남은 배지 하나뿐인 카드도 지웠다 —
          **같은 배지가 화면 맨 위 「사업 검증」 제목 옆에 이미 서 있다**
          (`BusinessValidationPage.jsx`). 한 화면에서 같은 판정을 두 번 말할 이유가 없다.
          ⚠ 판정 자체는 사라지지 않았다. 지운 것은 **두 번째 자리**다. */}
      {!bm ? (
        <Alert tone="warning">
          BM 판정이 오지 않았어요 — 시장조사 결과는 그대로 유효해요. 다시 만들어 볼 수 있어요.
        </Alert>
      ) : null}

      {result.canvas ? (
        <BmCanvas cells={result.canvas} active={openCell} onSelect={toggle} />
      ) : null}
      {/* 누른 칸 하나. `BmCellDetails` 는 목록을 받으므로 한 장짜리 목록을 준다 —
          부품을 새로 만들지 않는다. */}
      {opened ? <BmCellDetails cells={[opened]} active={opened.cell} /> : null}

      {bm ? (
        <div className="bm-swr">
          {/* 판 ㊻ — **이모지를 뺐다**(2026-08-16 사용자 지시). 옛 주석은 「색으로만
              가르면 색을 못 보는 사람에게 같은 상자 셋이 된다」였고 그 걱정은 옳다 —
              그래서 색이 아니라 **글자**가 가른다: 제목이 「강점 · 약점 · 위험」으로
              뚜렷하게 크고, 상자마다 왼쪽 색 띠가 보조로 남는다. */}
          <SwrBox title="강점" items={bm.strengths} tone="var(--color-status-success)" />
          <SwrBox title="약점" items={bm.weaknesses} tone="var(--color-status-warning)" />
          <SwrBox title="위험" items={bm.risks} tone="var(--color-status-danger)" />
        </div>
      ) : null}
    </>
  );
}


function SwrBox({ title, items, tone }) {
  return (
    <div>
      <h4 style={{ color: tone }}>{title}</h4>
      <ul>
        {/* ⚠ **「없음」이라고 쓰지 않는다.** 판정이 「수정 필요」인 화면에서 「위험 — 없음」은
            틀린 안심이다. *없다* 와 *적지 못했다* 는 다른 말이고, 이 칸이 비는 것은
            대부분 뒤쪽이다(실측 2026-08-15: 강점·약점·위험이 전부 빈 채로 REVISION_REQUIRED). */}
        {items.length > 0
          ? items.map((line) => <li key={line}>{line}</li>)
          : <li className="bm-swr__none">이번 실행은 이 칸을 <b>적지 못했어요</b> — 없다는 뜻이 아니에요</li>}
      </ul>
    </div>
  );
}
