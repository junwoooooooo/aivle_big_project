import { describe, expect, it, vi } from 'vitest';

import { drawMarketingContent, renderPreview } from './marketingRenderer.js';

function context() {
  const gradient = { addColorStop: vi.fn() };
  return {
    beginPath: vi.fn(),
    roundRect: vi.fn(),
    fill: vi.fn(),
    fillRect: vi.fn(),
    clearRect: vi.fn(),
    createLinearGradient: vi.fn(() => gradient),
    stroke: vi.fn(),
    moveTo: vi.fn(),
    lineTo: vi.fn(),
    measureText: vi.fn((text) => ({ width: text.length * 12 })),
    fillText: vi.fn(),
    save: vi.fn(),
    restore: vi.fn(),
    set fillStyle(value) { this._fillStyle = value; },
    get fillStyle() { return this._fillStyle; },
    set font(value) { this._font = value; },
    set textAlign(value) { this._textAlign = value; },
    set textBaseline(value) { this._textBaseline = value; },
    set globalAlpha(value) { this._globalAlpha = value; },
    set strokeStyle(value) { this._strokeStyle = value; },
    set lineWidth(value) { this._lineWidth = value; },
  };
}

const draft = {
  headline: '검증 결과를 담은 헤드라인',
  subheadline: '고객에게 필요한 핵심 가치를 전합니다',
  bodyCopy: '사용자가 직접 편집할 수 있는 본문입니다.',
  callToAction: '자세히 보기',
  supportingText: '#검증결과',
  accentColor: '#0f8878',
  textColor: '#ffffff',
  backgroundType: 'GRADIENT',
  backgroundValue: '#0f8878,#17363a',
  textAlignment: 'CENTER',
  headlineSize: 72,
  showCta: true,
  showPersonaTag: true,
};

describe('marketingRenderer', () => {
  it.each(['HERO_CENTER', 'SPLIT_VISUAL', 'EDITORIAL_POSTER', 'MINIMAL_CARD'])(
    'draws the %s template through the shared renderer',
    (layoutTemplate) => {
      const target = context();
      drawMarketingContent(target, 1080, 1080, { ...draft, layoutTemplate });
      expect(target.fillText).toHaveBeenCalled();
      expect(target.fillRect).toHaveBeenCalled();
    },
  );

  it('keeps the requested aspect ratio in the preview canvas', () => {
    const target = context();
    const canvas = {
      width: 0,
      height: 0,
      getContext: vi.fn(() => target),
    };
    renderPreview(canvas, {
      content: { width: 1200, height: 628 },
      current: { ...draft, layoutTemplate: 'HERO_CENTER' },
    }, 600);
    expect(canvas.width).toBe(600);
    expect(canvas.height).toBe(314);
  });
});
