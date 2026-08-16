import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import AssumptionLedger from './AssumptionLedger.jsx';
import { normalizeMarketResult } from './marketResult.js';

/** AI·백엔드와 **같은 골든 픽스처**를 읽는다 — 사본을 만들면 그 성질이 사라진다. */
function market() {
  const here = dirname(fileURLToPath(import.meta.url));
  const path = resolve(here, '../../../../ai/tests/fixtures/market_research/full.json');
  const raw = JSON.parse(readFileSync(path, 'utf-8'));
  Object.keys(raw).filter((key) => key.startsWith('_')).forEach((key) => delete raw[key]);
  return normalizeMarketResult(raw).market;
}

describe('가정 원장', () => {
  it('⭐ 날것의 마크다운이 화면에 남지 않는다 — 예전엔 ** 가 그대로 보였다', () => {
    const { container } = render(<AssumptionLedger market={market()} />);
    expect(container.textContent).not.toContain('**');
  });

  it('⭐ 잘린 문장이 없다 — 「원 근거 서술: … 두발 미」 가 이 화면의 출발점이었다', () => {
    const { container } = render(<AssumptionLedger market={market()} />);
    expect(container.textContent).not.toContain('원 근거 서술');
  });

  it('공식을 표의 머리로 세우고 항을 한 줄씩 편다', () => {
    render(<AssumptionLedger market={market()} />);
    expect(screen.getByText(/TAM\(연\) =/)).toBeInTheDocument();
    expect(screen.getAllByText('세그먼트비중').length).toBeGreaterThan(0);
    expect(screen.getAllByText('침투율').length).toBeGreaterThan(0);
  });

  it('항마다 관측·가정·가설 판정이 배지로 선다', () => {
    const { container } = render(<AssumptionLedger market={market()} />);
    const rows = container.querySelectorAll('.mr-ledger__t tbody tr');
    expect(rows.length).toBeGreaterThan(1);
    rows.forEach((row) => {
      const badge = row.querySelector('.ui-badge');
      expect(['관측', '가정', '가설']).toContain(badge.textContent);
      // 가정 행은 색만이 아니라 클래스로도 갈린다(색각 이상에서도 읽히게).
      expect(row.classList.contains('is-assumed')).toBe(badge.textContent !== '관측');
    });
  });

  it('⭐ 반증 조건과 관측 울타리가 화면에 있다 — 계약에만 있고 안 그리면 없는 것과 같다', () => {
    const { container } = render(<AssumptionLedger market={market()} />);
    expect(container.textContent).toContain('반증 조건');
    expect(container.textContent).toContain('관측된 울타리');
    expect(container.textContent).toContain('0.966');
  });

  it('내용 없는 각주 딱지를 세우지 않는다 — 빈 라벨은 정보가 아니라 소음이다', () => {
    const { container } = render(<AssumptionLedger market={market()} />);
    container.querySelectorAll('.mr-fact__note').forEach((note) => {
      expect(note.querySelector('span').textContent.trim()).not.toBe('');
    });
    // 울타리가 있는 항은 하나뿐이다 — 딱지도 그만큼만 있어야 한다.
    const bounds = [...container.querySelectorAll('.mr-fact__note b')]
      .filter((b) => b.textContent === '관측된 울타리');
    expect(bounds).toHaveLength(2);   // TAM·SAM 의 세그먼트비중 각 한 줄
  });

  it('항의 값은 줄여 쓰지 않는다 — 39,000원이 «3.9만원» 이 되면 단가가 안 읽힌다', () => {
    const { container } = render(<AssumptionLedger market={market()} />);
    expect(container.textContent).toContain('39,000 원');
    expect(container.textContent).not.toContain('3.9만원');
    // 결론 한 줄은 줄여 쓴다 — 자릿수가 아니라 규모를 읽는 자리다.
    expect(container.querySelector('.mr-ledger__t tfoot').textContent).toContain('억원');
  });

  it('관측 항은 건수와 «도메인 몇 곳» 을 같이 낸다 — 3건이 1곳이면 3중이 아니다', () => {
    const { container } = render(<AssumptionLedger market={market()} />);
    expect(container.textContent).toMatch(/출처 \d+곳 · \d+건/);
    expect(container.textContent).toContain('kosis.kr');
  });

  it('경계 문장을 접지 않는다 — 값과 같은 화면에 있어야 도달한 것이다', () => {
    const { container } = render(<AssumptionLedger market={market()} />);
    expect(container.querySelectorAll('.mr-caveat').length).toBeGreaterThan(0);
    expect(container.querySelector('details')).toBeNull();
  });

  it('성장률의 해석 경계는 항이 아니라 표 밖 문장으로 남는다', () => {
    const { container } = render(<AssumptionLedger market={market()} />);
    expect(container.textContent).toContain('미래 성장을 뜻하지 않는다');
  });

  it('SOM 을 안 쟀으면 «0 이 아니라 안 쟀다» 고 말한다', () => {
    const { container } = render(<AssumptionLedger market={market()} />);
    expect(container.textContent).toContain('안 쟀다');
  });

  it('요인이 없는 옛 결과는 문장 목록으로 폴백한다 — 경계가 사라지면 안 된다', () => {
    const raw = market();
    const legacy = {
      ...raw,
      tam: { ...raw.tam, factors: [], assumptions: ['세그먼트비중 0.19 는 **가정이다**'] },
      sam: null,
      growth: null,
    };
    const { container } = render(<AssumptionLedger market={legacy} />);
    expect(container.textContent).toContain('세그먼트비중 0.19 는 가정이다');
    expect(container.textContent).not.toContain('**');
  });

  it('읽을 것이 하나도 없으면 빈 상자를 세우지 않는다', () => {
    const { container } = render(
      <AssumptionLedger market={{ tam: null, sam: null, growth: null, som: 1, price: null }} />,
    );
    expect(container.firstChild).toBeNull();
  });
});

describe('가정 원장 — 합계 줄', () => {
  it('가정이 몇 개 곱해졌는지 결론 옆에 적는다', () => {
    const { container } = render(<AssumptionLedger market={market()} />);
    const foot = container.querySelector('.mr-ledger__t tfoot');
    expect(within(foot).getByText(/가정이 \d+개 곱해진 추정이다/)).toBeInTheDocument();
  });
});
