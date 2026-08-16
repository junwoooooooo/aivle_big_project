import { describe, expect, it } from 'vitest';
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
    expect(detail.textContent).not.toContain('컨셉 서술에 이 칸 내용이 없다');
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
    expect(detail.textContent).toContain('컨셉 서술에 이 칸 내용이 없다');
  });

  it('요약 칸은 상태 줄 하나로 말한다 — 카드에 같은 말을 두 번 넣지 않는다', () => {
    const { container } = render(<BmCanvas cells={withEmptyPlanCell()} onJump={() => {}} />);
    const tile = [...container.querySelectorAll('.bm-cell')]
      .find((node) => node.textContent.includes('고객 관계'));
    expect(tile.querySelector('.bm-cell__lead')).toBeNull();
    expect(tile.querySelector('.bm-cell__foot').textContent).toContain('서술 없음');
    expect(tile.textContent).not.toContain('입력에 고객 관계 정보가');
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
    expect(detail.textContent).toContain('미확인');
    expect(detail.textContent).not.toContain('서술 없음');
  });

  it('진짜 PLAN 칸은 여전히 «서술됨/서술 없음» 으로 읽힌다', () => {
    const { container } = render(<BmCellDetails cells={canvas()} active={null} />);
    expect(container.querySelector('#bm-KEY_ACTIVITIES').textContent).toContain('서술됨');
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
