import { describe, expect, it } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { MarketResultBody } from './MarketResultBody.jsx';
import { normalizeMarketResult } from './marketResult.js';

/**
 * **AI·백엔드와 같은 골든 픽스처**로 화면을 그린다. (판 ㊸)
 *
 * <p>왜 이 파일이 필요했나 — 이 판까지 화면을 눈으로 보는 길은 `/wireframe.html` 하나였는데
 * 그 URL 은 `public/wireframe.html`(손으로 쓴 정적 목업)이 가리고 있어 <b>제품 부품을 한 번도
 * 안 그리고 있었다.</b> 눈으로 보는 것을 대신하지는 않지만, <b>「그렸는데 빠졌다」는 여기서 잡는다.</b>
 */
function result(patch = null) {
  const here = dirname(fileURLToPath(import.meta.url));
  const path = resolve(here, '../../../../ai/tests/fixtures/market_research/full.json');
  const raw = JSON.parse(readFileSync(path, 'utf-8'));
  Object.keys(raw).filter((key) => key.startsWith('_')).forEach((key) => delete raw[key]);
  if (patch) patch(raw);
  return normalizeMarketResult(raw);
}

const draw = (patch) => render(
  <MarketResultBody result={result(patch)} activeId={null} onJump={() => {}} />,
);

/** 절 하나를 편다. 본문은 «열렸을 때만» 만들어지므로 이걸 거치지 않으면 아무것도 없다. */
function 편다(container, subject) {
  const 줄 = container.querySelector(`#sec-${subject}`);
  fireEvent.click(within(줄).getByRole('button'));
  return 줄;
}

describe('MarketResultBody — 판 ㊺ 목차', () => {
  it('목차가 **목표 보고서와 같은 아홉 절**이다', () => {
    const { container } = draw();
    const ids = [...container.querySelectorAll('[id^="sec-"]')].map((el) => el.id);
    // ⚠ 순서가 곧 절 번호다. 중간에 끼우면 기존 번호가 밀린다.
    //    정본은 `marketResult.SECTION_ORDER` 이고 그것은 `TARGET_REPORT.md` 를 따른다.
    expect(ids).toEqual([
      'sec-MARKET_SIZE', 'sec-PRICE', 'sec-COMPETITOR', 'sec-CHANNEL',
      'sec-DEMAND', 'sec-UNIT_ECONOMICS', 'sec-REGULATION', 'sec-GAPS', 'sec-SYNTHESIS',
    ]);
    // ★ 「찾지 못한 것」의 **건수 나열**은 버렸다(판 ㊺) — 「못 채운 것 41건 · 가정 7건」은
    //    엔진 장부지 사업가의 물건이 아니다. 그 자리에 8절 처방(무엇/왜/**어디서**)이 선다.
    expect(ids).not.toContain('sec-NOT_FOUND');
    // 성장률·계산은 1절 «안»으로 접혔다 — 목표 보고서 1절이 규모와 성장률을 한 표에 놓는다.
    expect(ids).not.toContain('sec-GROWTH');
    expect(ids).not.toContain('sec-CALCULATION');
  });

  it('2절 판단은 **결론까지** 그린다 — 계산식만 남으면 반쪽이다', () => {
    const { container } = draw();
    // ⚠ **결론은 접힌 채로도 보여야 한다.** 옛 화면이 판단 카드를 목차 «위»에 세운 이유가
    //    「표를 다 읽고 나서야 판단을 만나면 늦다」였고, 그 이유는 목차를 바꿔도 그대로다.
    expect(screen.getByText(/값이 아닌 이유가 서지 않으면/)).toBeTruthy();
    const 절 = 편다(container, 'PRICE');
    // 펼치면 근거와 계산식이 선다. ⚠ 제목은 **절 머리가 이미 갖고 있다** — 판단 카드가
    //   절 안에서 제목을 또 세우면 「가격」이 두 번 찍힌다.
    expect(within(절).getByText(/안 씁니다/)).toBeTruthy();  // 못 쓴 갈래도 말한다
    expect(within(절).queryByText(/이 가격이 시장 어디에 서 있나/)).toBeNull();
  });

  // ★ 판 ㊻ — **8·9절이 «두 벌»이던 것을 한 벌로 줄였다.**
  //   기계가 원장에서 뽑은 처방/합성 표와 AI 가 쓴 같은 이름의 글이 한 절에 나란히 서서
  //   **같은 말을 두 번** 했다. 글이 왔으면 글만, 글이 없으면 기계 표가 그 자리를 지킨다.
  //   ⚠ 아래 두 시험이 **「글이 없을 때」를 못 박는다** — 이 폴백이 사라지면 8·9절이
  //     통째로 비고, 그것은 「조사가 못 구했다」가 아니라 「화면이 안 그렸다」다.
  it('8절 처방은 **글이 안 온 실행에서** 「어디서 구하나」까지 그린다', () => {
    const { container } = draw((raw) => { raw.report = null; });
    const card = 편다(container, 'GAPS');
    expect(within(card).getByText(/공중위생관리법/)).toBeTruthy();
  });

  it('8·9절에 글이 왔으면 **기계 표를 두 번 그리지 않는다**', () => {
    const { container } = draw();
    const card = 편다(container, 'GAPS');
    // 글은 있고, 같은 말을 하는 기계 처방 표는 없다.
    expect(card.querySelector('.mr-prose')).toBeTruthy();
    expect(within(card).queryByText(/공중위생관리법/)).toBeNull();
  });

  it('9절은 **글이 안 온 실행에서** 미는 것과 흔드는 것을 갈라 그린다', () => {
    const { container } = draw((raw) => { raw.report = null; });
    const card = 편다(container, 'SYNTHESIS');
    // 픽스처의 9절은 「흔듦」 한 줄이다 — 갈래 상자가 그 이름으로 선다.
    expect(within(card).getByRole('heading', { level: 4, name: /흔드는 것/ })).toBeTruthy();
    expect(within(card).getByText(/값만으로는 고를 이유가 없어요|경쟁 구독료/)).toBeTruthy();
    // ⚠ **빈 갈래를 지우지 않는다.** 픽스처의 9절은 「흔듦」뿐이라 「미는 것」이 비는데,
    //    그 자리를 지우면 「미는 사실이 0건이었다」와 「안 쟀다」가 같아 보인다 —
    //    성적표 수요 줄에서 고친 것과 같은 병이고, 9절은 사업가가 돈을 내는 자리다.
    expect(within(card).getByText(/한 건도 없었어요/)).toBeTruthy();
  });

  it('2·8·9절이 **안 온 실행**에서는 «못 만들었다»고 말한다 — 조용히 비우지 않는다', () => {
    // ⚠ **판 ㊺ 에서 규칙이 바뀌었다.** 옛 규칙은 「그 자리를 아예 세우지 않는다」였고
    //    이유는 「빈 카드가 «조사했는데 없었다»로 읽힌다」였다. 그 걱정은 옳다 —
    //    그런데 목차가 결과마다 아홉 줄이었다 일곱 줄이었다 하면 **그것도 거짓말이다**
    //    (같은 파일 아래 「목차가 결과마다 달라지면 그것도 거짓말이다」와 같은 규율).
    //    그래서 **줄은 세우되 「못 만들었어요 — 없다는 뜻이 아니에요」라고 말한다** —
    //    `BmResultBody` 의 강점·약점·위험을 고친 것과 **같은 처방**이다.
    const { container } = draw((raw) => {
      raw.judgment = null;
      raw.prescriptions = null;
      raw.synthesis = null;
    });
    expect(container.querySelectorAll('[id^="sec-"]')).toHaveLength(9);
    // 9절은 대체물이 없다 — 못 만들었다고 말하고 **열리지 않는다**(열리는 척하고 빈 칸을
    // 보이면 「조사가 부실한가」와 「화면이 고장인가」가 구분되지 않는다).
    expect(screen.getByText(/9절을 못 만들었어요/)).toBeTruthy();
    expect(within(container.querySelector('#sec-SYNTHESIS')).getByRole('button')).toBeDisabled();
    // ★ 8절은 **대체물이 있다** — 처방이 없어도 옛 「못 구한 것」 목록으로 물러선다.
    //   목차에서 `NOT_FOUND` 절을 걷어냈으므로, 여기서도 안 보이면 **「무엇을 못 구했나」가
    //   통째로 사라진다.** 건수 나열이 사업가의 물건은 아니지만 아무것도 없는 것보단 낫다.
    expect(screen.getByText(/못 구한 목록만 있어요/)).toBeTruthy();
    expect(within(container.querySelector('#sec-GAPS')).getByRole('button')).not.toBeDisabled();
  });

  // ⚠⚠ **판 ㊻ 에서 규칙이 뒤집혔다** (2026-08-16 사용자 지시).
  //   여기 있던 두 시험은 「덜 조사된 사유가 화면에 선다」·「요약이 죽었으면 죽었다고
  //   말한다」였고, 그 이유는 **「예산이 끊겨 빈 것」과 「정말 자료가 없는 것」이 같아
  //   보이면 안 된다**였다. 그 이유는 지금도 옳다. 그런데 사용자가 화면에서 그 상자를
  //   빼기로 했다 — 담긴 것이 「검사 미통과 3회 · fail-closed」처럼 **엔진의 내부 사정**
  //   이어서 사업가가 그걸로 정할 것이 없다는 판단이다.
  //   **그래서 시험을 지우지 않고 «뒤집어» 남긴다** — 다음에 이 상자를 되살릴 사람이
  //   「원래 없었다」고 오해하지 않도록. 되살릴 자리는 화면 맨 아래 검산 페이지다.
  it('덜 조사된 사유 상자는 **일부러 안 그린다** — 봉투에는 그대로 온다', () => {
    const before = result((raw) => {
      raw.degradations = [
        { stage: 'sections', code: 'SECTIONS_TRUNCATED', detail: '예산 상한 40건까지만 읽었다' },
      ];
    });
    // 봉투는 사유를 그대로 들고 있다 — 잃은 것은 «화면 자리»뿐이다.
    expect(before.degradations.length).toBe(1);
    draw((raw) => {
      raw.degradations = [
        { stage: 'sections', code: 'SECTIONS_TRUNCATED', detail: '예산 상한 40건까지만 읽었다' },
      ];
    });
    expect(screen.queryByText(/이 조사가 다 돌지 못했어요/)).toBeNull();
  });

  it('강조 별표가 **글자로** 남아 있지 않다', () => {
    // ⚠ 이 검사는 **골든 픽스처 위**에 있어야 한다. 실측 봉투 검사
    // (`MarketResultBody.live.test.jsx`)에도 같은 그물이 있지만 그 봉투는 git 이
    // 추적하지 않아 **다른 기계·CI 에서는 통째로 건너뛴다** — 그물이 이 기계에만
    // 걸려 있으면 게이트가 아니다.
    const { container } = draw();
    expect((container.textContent || '').match(/\*\*[^*\n]{1,40}\*\*/g) || []).toEqual([]);
  });

  // ⚠ 위와 같은 이유로 뒤집힌 시험이다. 옛 규칙은 「요약이 죽었으면 죽었다고 말한다」였고
  //   근거는 유료 스모크(2026-08-15)의 유일한 실패가 화면 0곳에 닿았다는 실측이었다.
  //   지금은 그 사유를 화면에 안 낸다 — **잃은 것을 알고 뺀 것이지 몰라서 뺀 것이 아니다.**
  it('요약이 죽은 실행에서도 **사유 상자를 안 그린다** — 판 ㊻ 결정', () => {
    draw((raw) => {
      raw.summary = null;
      raw.degradations = [
        { stage: 'summary', code: 'CHECK_FAILED', detail: '검사 미통과 3회 — 요약을 버리고 카드만 낸다' },
      ];
    });
    expect(screen.queryByText(/요약 문장이 검사를 통과하지 못해 버렸어요/)).toBeNull();
  });

  it('옛 결과의 없는 과목은 **「안 쟀다」고 말한다** — 말없이 비워 두지 않는다', () => {
    // 판 ㊸ 이전 결과는 성적표가 7과목이라 새 셋이 아예 없다.
    const { container } = draw((raw) => {
      raw.scorecard = raw.scorecard.filter(
        (row) => !['CHANNEL', 'UNIT_ECONOMICS', 'REGULATION'].includes(row.subject),
      );
    });
    // 줄은 그대로 아홉이다 — 목차가 결과마다 달라지면 그것도 거짓말이다.
    expect(container.querySelectorAll('[id^="sec-"]')).toHaveLength(9);
    expect(screen.getAllByText(/이 조사에는 없던 과목이에요/).length).toBe(3);
  });

  it('구성비 표가 반쪽이면 **그렇다고 말한다** — 반쪽 표는 빈칸보다 나쁘다', () => {
    // 합이 100%가 아닌 3행짜리 채널 표. 실측된 병이다 — 채널 절 합이 47%였고
    // 숨은 특약점 29.65%가 1위 대형마트 31.05%와 대등했다.
    const 행 = (id, subject, value) => ({
      id, kind: '관측', metric: '매출처별 판매비중', subject, period: '2025',
      value, unit: '%', grade: '확정', gradeReason: '등급표:public_filing',
      sourceUrl: 'https://kind.krx.co.kr/x', sourceKind: 'public_filing', retrievedAt: null,
      quote: null, caveats: [], formula: null, inputs: null, materialIds: [], assumptions: [],
      section: 'CHANNEL', placement: 'COMPETITOR_FIRM', issuer: '예시사',
      tableKey: 'T-1|매출처별 판매비중|2025', raw: `${value}%`,
    });
    const { container } = draw((raw) => {
      raw.evidence.push(행('t-1', '대형마트', 31.05), 행('t-2', '대리점', 10.21), 행('t-3', '편의점', 5.99));
    });
    fireEvent.click(container.querySelector('#sec-CHANNEL button'));
    expect(screen.getByText(/47\.3% 로/)).toBeTruthy();
    expect(screen.getByText(/보이지 않는 행이 있고, 그것이 1위일 수도 있다/)).toBeTruthy();
    // 발행사 꼬리표 — **두 회사의 표가 하나로 읽히는 것을 막는다.**
    expect(screen.getAllByText('경쟁사(예시사)').length).toBe(3);
  });
});
