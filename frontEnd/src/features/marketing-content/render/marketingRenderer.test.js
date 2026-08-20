import { afterEach, describe, expect, it, vi } from 'vitest';
import { downloadMarketingContent, wrapMarketingText } from './marketingRenderer.js';

describe('marketing image renderer', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('긴 한국어 문구를 다운로드 이미지 폭에 맞게 나눈다', () => {
    const context = { measureText: (value) => ({ width: value.length * 10 }) };
    expect(wrapMarketingText(context, '한글문구가길어도 잘립니다', 40))
      .toEqual(['한글문구', '가길어도', '잘립니다']);
  });

  it('이미지와 문구를 합성한 PNG 파일을 다운로드한다', async () => {
    const context = {
      beginPath: vi.fn(), closePath: vi.fn(), drawImage: vi.fn(), fill: vi.fn(), fillRect: vi.fn(),
      fillText: vi.fn(), lineTo: vi.fn(), measureText: (value) => ({ width: String(value).length * 12 }),
      moveTo: vi.fn(), quadraticCurveTo: vi.fn(), stroke: vi.fn(),
    };
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(context);
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((callback, type) => {
      expect(type).toBe('image/png');
      callback(new Blob(['png'], { type }));
    });
    vi.stubGlobal('Image', class {
      constructor() { this.naturalWidth = 1080; this.naturalHeight = 1080; }
      set src(value) { this.source = value; this.onload(); }
    });
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:download');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
    let downloadedFilename = null;
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function click() {
      downloadedFilename = this.download;
    });

    await downloadMarketingContent({
      contentType: 'SOCIAL_POST', title: '출시 소식', body: '새 제품을 소개합니다.',
      callToAction: '자세히 보기', hashtags: ['신제품', '#서비스'],
      legalReview: { requiredDisclosuresApplied: ['AI 생성 이미지'] },
    }, 'blob:generated-image', { accent: '#0f8878', align: 'LEFT', scale: '1' }, '출시 소식');

    expect(context.drawImage).toHaveBeenCalled();
    expect(context.fillText).toHaveBeenCalledWith('자세히 보기', expect.any(Number), expect.any(Number));
    expect(context.fillText).toHaveBeenCalledWith('#신제품 #서비스', expect.any(Number), expect.any(Number));
    expect(downloadedFilename).toBe('출시-소식.png');
  });
});
