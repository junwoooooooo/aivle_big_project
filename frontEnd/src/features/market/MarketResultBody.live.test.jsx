import { describe, expect, it } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { MarketResultBody } from './MarketResultBody.jsx';
import { normalizeMarketResult } from './marketResult.js';

/**
 * **유료 실행이 실제로 낸 봉투**를 화면 부품에 넣는다. (판 ㊸ 보완)
 *
 * <p>골든 픽스처(`MarketResultBody.test.jsx`)는 손으로 썼다. 그것이 정답이지만, 대가로
 * <b>손이 상상하지 못한 조합은 영원히 안 들어온다</b> — 값이 `null` 인 승격 카드,
 * 인용이 없는 행, `summary` 가 통째로 죽은 실행 같은 것들이다. 자바 쪽에도 같은 이유로
 * `MarketResearchLiveEnvelopeTests` 를 뒀다.
 *
 * <p><b>봉투 파일이 없으면 건너뛴다.</b> 유료 실행은 아무 때나 돌 수 없다.
 */
const HERE = dirname(fileURLToPath(import.meta.url));
const 봉투 = resolve(
  HERE, '../../../../ai/app/research/research2/runs-generated/p43-smoke-01-validation.json',
);

const 있다 = existsSync(봉투);
const 실측 = () => normalizeMarketResult(JSON.parse(readFileSync(봉투, 'utf-8')));

describe.skipIf(!있다)('MarketResultBody — 실측 봉투', () => {
  const draw = () => render(
    <MarketResultBody result={실측()} activeId={null} onJump={() => {}} />,
  );

  it('아홉 절이 다 서고 터지지 않는다', () => {
    const { container } = draw();
    // 판 ㊺ — 목차가 목표 보고서와 같은 아홉 절이다(정본 `marketResult.SECTION_ORDER`).
    expect(container.querySelectorAll('[id^="sec-"]')).toHaveLength(9);
  });

  it('2·8·9절이 실측 값으로 **목차 안에서** 그려진다', () => {
    const { container } = draw();
    // ⚠ 판 ㊺ 부터 이 셋은 목차 «밖» 카드가 아니라 **2·8·9절 그 자체**다.
    //    밖에 두면 목차가 아홉인데 카드가 셋 더 서서 「이게 몇 절인가」를 셀 수 없다.
    const 편다 = (subject) => {
      const 줄 = container.querySelector(`#sec-${subject}`);
      fireEvent.click(within(줄).getByRole('button'));
      return 줄;
    };
    // 실측 봉투는 비교 갈래가 여럿이라 여러 줄이 잡힌다 — 한 줄이라고 단정하지 않는다.
    expect(within(편다('PRICE')).getAllByText(/안 씁니다|계산:/).length).toBeGreaterThan(0);
    // ★ 셋째 열(「어디서」)이 이 표의 값어치다 — 「못 구했다」로 끝나면 사업가는 거기서 멈춘다.
    const 처방 = 편다('GAPS');
    expect(within(처방).getByRole('columnheader', { name: /어디서 구하나/ })).toBeTruthy();
    expect(within(처방).getAllByRole('row').length).toBeGreaterThan(1);  // 머리줄 + 최소 한 줄
    // ⚠ **두 갈래가 «둘 다» 서야 한다.** 빈 갈래를 지우면 「흔드는 사실이 0건이었다」와
    //    「흔듦을 아예 안 쟀다」가 화면에서 같아 보인다.
    const 합성 = 편다('SYNTHESIS');
    expect(within(합성).getByRole('heading', { level: 4, name: /미는 것/ })).toBeTruthy();
    expect(within(합성).getByRole('heading', { level: 4, name: /흔드는 것/ })).toBeTruthy();
  });

  it('★ 수요 줄이 **두 수를 모순 없이** 말한다', () => {
    // 실측 결함이었다: 「근거 0건」 배지 옆에 「근거 13건 ▾」 단추가 섰다.
    draw();
    expect(screen.getByText(/정황 근거/)).toBeTruthy();
    // 파이썬 `None` 이 한국어 문장에 박히던 자리.
    expect(screen.queryByText(/최고 등급 None/)).toBeNull();
  });

  // ⚠ 판 ㊻ 에서 뒤집혔다(사용자 지시). 옛 규칙과 그 이유는
  //   `MarketResultBody.test.jsx` 의 같은 자리 주석에 그대로 적어 뒀다.
  it('★ 요약이 죽어도 **사유 상자를 안 그린다** — 봉투에는 그대로 온다', () => {
    draw();
    expect(실측().summary).toBeFalsy();
    // 잃은 것은 화면 자리뿐이다 — 사유는 봉투가 계속 들고 온다.
    expect((실측().degradations ?? []).length).toBeGreaterThan(0);
    expect(screen.queryByText(/요약 문장이 검사를 통과하지 못해 버렸어요/)).toBeNull();
  });

  it('★ 경계 문장이 **화면에 닿는다** — 봉투에만 있고 화면에 없으면 지운 것과 같다', () => {
    // `CLAUDE.md` §5-8: 경계 표시는 절대 제거하지 않는다. 이 봉투는 88장 중 48장이
    // 경계를 들고 있다(문장 61개). 접이식 안에 있어 **펼쳐야** 보이므로, 펼친 뒤 센다.
    const { container } = draw();
    // ⚠ **아코디언이다** — 한 번에 한 절만 열린다. 전부 눌러 놓고 한 번에 세면 마지막
    //   절(「찾지 못한 것」)만 열린 채로 0 이 나온다. 절마다 열어서 센다.
    let 본 = 0;
    container.querySelectorAll('[id^="sec-"]').forEach((sec) => {
      const button = sec.querySelector('[aria-expanded]');
      if (!button) return;
      if (button.getAttribute('aria-expanded') === 'false') fireEvent.click(button);
      본 += sec.querySelectorAll('.mr-caveat').length;
      if (button.getAttribute('aria-expanded') === 'true') fireEvent.click(button);
    });
    const 봉투경계 = 실측().evidence.reduce((n, e) => n + (e.caveats?.length || 0), 0);
    expect(봉투경계).toBeGreaterThan(0);
    expect(본).toBeGreaterThan(0);
  });

  it('★ 강조 별표가 **글자로** 남아 있지 않다', () => {
    // 실측(2026-08-15): 8절 처방 표의 「왜」 칸만 Emphasis 를 안 거쳐
    // 「**어디를 볼지 적는다**」가 별표째 찍혔다. 문구를 그대로 비교하는 검사는
    // 이 부류를 **구조적으로 못 잡는다** — 기대 문자열에도 별표를 적기 때문이다.
    // 그래서 화면 전체를 훑는다.
    const { container } = draw();
    const 남은 = (container.textContent || '').match(/\*\*[^*\n]{1,40}\*\*/g) || [];
    expect(남은).toEqual([]);
  });

  it('★ 자릿수가 깨진 수가 화면에 없다', () => {
    // 「8조 9,854」 + 「억원」 을 갈라 읽어 8.0e20 원(80,000경)이 나갔던 자리.
    const 값 = 실측().evidence.map((e) => e.value).filter((v) => typeof v === 'number');
    expect(값.length).toBeGreaterThan(0);
    // 이 컨셉에서 1경(1e16) 을 넘는 원화 값은 없다 — 넘으면 배율을 두 번 곱한 것이다.
    expect(Math.max(...값)).toBeLessThan(1e16);
  });
});
