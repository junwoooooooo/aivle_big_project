import { describe, expect, it, vi } from 'vitest';
import { render } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import BmCanvas, { BmCellDetails } from './BmCanvas.jsx';
import { normalizeMarketResult } from './marketResult.js';

/** AI·백엔드와 **같은 골든 픽스처**를 읽는다. */
function canvas(patch = null) {
  const here = dirname(fileURLToPath(import.meta.url));
  const path = resolve(here, '../../../../ai/tests/fixtures/market_research/bm.json');
  const raw = JSON.parse(readFileSync(path, 'utf-8'));
  Object.keys(raw).filter((key) => key.startsWith('_')).forEach((key) => delete raw[key]);
  if (patch) patch(raw);
  return normalizeMarketResult(raw).canvas;
}

/** 사용자가 실제로 본 상태 — 계획 칸이 서술 없이 비어 있다. */
function withEmptyPlanCell() {
  return canvas((raw) => {
    const cell = raw.canvas.cells.find((c) => c.canvasCell === 'CUSTOMER_RELATIONSHIPS');
    cell.content = [];
    cell.sourceLabels = [];
    cell.reason = '입력에 고객 관계 정보가 포함되지 않음.';
  });
}

describe('BM 캔버스 — 빈 칸', () => {
  it('⭐ 빈 칸이 같은 사실을 두 번 말하지 않는다', () => {
    const cells = withEmptyPlanCell();
    const { container } = render(<BmCellDetails cells={cells} active={null} />);
    const detail = container.querySelector('#bm-CUSTOMER_RELATIONSHIPS');

    // 모델이 쓴 사유 한 줄만 남는다 — 고정 문구를 덧붙이지 않는다.
    expect(detail.textContent).toContain('입력에 고객 관계 정보가 포함되지 않음');
    expect(detail.textContent).not.toContain('컨셉 서술에 이 칸 내용이 없어요');
  });

  it('사유가 안 오면 그때만 고정 문구로 메운다 — 빈 자리로 두지 않는다', () => {
    const cells = canvas((raw) => {
      const cell = raw.canvas.cells.find((c) => c.canvasCell === 'KEY_RESOURCES');
      cell.content = [];
      cell.sourceLabels = [];
      cell.reason = ' ';
    });
    const { container } = render(<BmCellDetails cells={cells} active={null} />);
    const detail = container.querySelector('#bm-KEY_RESOURCES');
    expect(detail.textContent).toContain('컨셉 서술에 이 칸 내용이 없어요');
  });

  it('⭐ 빈 요약 칸은 «왜 비었는지»를 그 자리에 세운다', () => {
    // ⚠ 2026-08-15 정정: 이 시험은 「요약 칸은 상태 줄 하나로 말한다」로 빈 칸에
    //   `.bm-cell__lead` 가 **없어야** 한다고 못박고 있었다. 그 결정이 다른 병을 만들었다 —
    //   서버가 사용자가 쓴 계획 문장을 지웠을 때(실측: 성공 3회 중 2회) 화면에는
    //   「서술 없음」만 남고 **왜 사라졌는지가 어디에도 없었다.** 사유를 쓰는 함수는
    //   있었는데 호출부가 0곳인 `BmCellDetails` 안에만 있었다.
    const { container } = render(<BmCanvas cells={withEmptyPlanCell()} />);
    const tile = [...container.querySelectorAll('.bm-cell')]
      .find((node) => node.textContent.includes('고객 관계'));
    expect(tile.querySelector('.bm-cell__foot').textContent).toContain('서술 없음');
    expect(tile.querySelector('.bm-cell__why').textContent)
      .toContain('입력에 고객 관계 정보가');
  });

  it('자리채움 사유는 사유로 안 보인다 — 서버가 넣는 「사유 미기재」까지', () => {
    // 실측 봉투(`p43-smoke-01-validation.json`)에 `"reason": "사유 미기재"` 가 그대로 있다.
    // 예전에는 정규화가 넣는 문구 하나만 걸러서 이것이 **사유인 척** 화면에 섰다.
    const cells = canvas((raw) => {
      const cell = raw.canvas.cells.find((c) => c.canvasCell === 'CUSTOMER_RELATIONSHIPS');
      cell.content = [];
      cell.reason = '사유 미기재';
    });
    const { container } = render(<BmCanvas cells={cells} />);
    const tile = [...container.querySelectorAll('.bm-cell')]
      .find((node) => node.textContent.includes('고객 관계'));
    expect(tile.textContent).not.toContain('사유 미기재');
    expect(tile.querySelector('.bm-cell__why').textContent).toContain('컨셉 서술에');
  });

  it('⭐ 칸을 누르면 그 칸의 근거표가 열린다 — 「근거 12건」을 열 길이 있어야 한다', () => {
    const onSelect = vi.fn();
    const { container } = render(
      <BmCanvas cells={canvas()} active={null} onSelect={onSelect} />);
    // ⚠ 본문에 「채널」이 들어간 다른 칸(가치 제안)이 있다 — **제목으로** 고른다.
    const tile = [...container.querySelectorAll('.bm-cell')]
      .find((node) => node.querySelector('h4')?.textContent === '채널');
    expect(tile.tagName).toBe('BUTTON');
    expect(tile.textContent).toContain('자세히');
    tile.click();
    expect(onSelect).toHaveBeenCalledWith('CHANNELS');
  });

  it('onSelect 가 없으면 누를 수 없다 — 착지할 자리가 없는데 누르게 두지 않는다', () => {
    const { container } = render(<BmCanvas cells={canvas()} />);
    expect(container.querySelector('button.bm-cell')).toBeNull();
  });
});

describe('BM 캔버스 — PLAN 과 UNVERIFIED 는 다른 사건이다', () => {
  it('⭐ 계획 칸이라고 서버 상태를 덮어쓰지 않는다', () => {
    // 파트너는 프롬프트가 «비면 UNVERIFIED» 로 정해 둔 칸이다.
    // 넷을 전부 '계획'으로 뭉개면 「찾아봤는데 없다」가 「말한 적 없다」로 바뀐다.
    const cells = canvas();
    const partners = cells.find((cell) => cell.cell === 'KEY_PARTNERS');
    expect(partners.status).toBe('UNVERIFIED');

    const { container } = render(<BmCellDetails cells={cells} active={null} />);
    const detail = container.querySelector('#bm-KEY_PARTNERS');
    // 「미확인」은 2026-08-13 에 「근거 필요」가 됐다(목업 어휘). 뜻은 그대로다.
    expect(detail.textContent).toContain('근거 필요');
    expect(detail.textContent).not.toContain('서술 없음');
  });

  it('진짜 PLAN 칸은 여전히 «서술됨/서술 없음» 으로 읽힌다', () => {
    const { container } = render(<BmCellDetails cells={canvas()} active={null} />);
    expect(container.querySelector('#bm-KEY_ACTIVITIES').textContent).toContain('서술됨');
  });
});

describe('BM 캔버스 — 3×3 배치', () => {
  it('⭐ 9칸이 한 격자에 목업 순서로 선다 — 밴드 제목은 없다', () => {
    const { container } = render(<BmCanvas cells={canvas()} onJump={() => {}} />);
    const grid = container.querySelector('.bm-canvas');
    const names = [...grid.querySelectorAll('.bm-cell h4')].map((node) => node.textContent);

    expect(names).toEqual([
      '핵심 파트너', '핵심 활동', '가치 제안',
      '고객 관계', '고객 세그먼트', '채널',
      '핵심 자원', '비용 구조', '수익원',
    ]);
    // 밴드 제목(「고객과 가치」 등)은 목업에 없다 — 되살아나면 여기서 잡힌다.
    expect(container.querySelector('.bm-band')).toBeNull();
    expect(container.textContent).not.toContain('고객과 가치');
  });
});

describe('BM 캔버스 — 마크다운', () => {
  it('칸 내용의 별표가 화면에 남지 않는다', () => {
    const cells = canvas((raw) => {
      const cell = raw.canvas.cells.find((c) => c.canvasCell === 'KEY_ACTIVITIES');
      cell.content = ['**예약 보증금**을 자동 청구한다'];
    });
    const { container } = render(<BmCellDetails cells={cells} active={null} />);
    const detail = container.querySelector('#bm-KEY_ACTIVITIES');
    expect(detail.textContent).not.toContain('**');
    expect(detail.querySelector('strong').textContent).toBe('예약 보증금');
  });
});
