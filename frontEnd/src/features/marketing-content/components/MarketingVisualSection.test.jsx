import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import MarketingVisualSection from './MarketingVisualSection.jsx';

const create = vi.fn(); const retry = vi.fn(); const cancel = vi.fn(); const download = vi.fn();
let visualState;
vi.mock('../hooks/useMarketingVisual.js', () => ({ default: () => visualState }));

const props = {
  projectId: '41',
  detail: { content: { contentId: 'content-1', title: '상품 배너', contentType: 'BANNER', channel: 'SNS' } },
  revision: { revisionId: 'revision-1', revisionNumber: 3, revisionType: 'USER_EDITED' },
  source: { conceptName: '한글 상품', targetSegment: '직장인', valueProposition: '편리한 가치', keyFeatures: ['빠름'],
    allowedClaims: ['편리함'], prohibitedClaims: ['100% 보장'], requiredDisclosures: ['개인차 있음'], requiredControls: ['과장 금지'] },
  draft: { title: '기존 콘텐츠 제목', body: '기존 콘텐츠 본문', imageBrief: '상품 중심' },
};

function base(overrides = {}) {
  return { run: null, error: null, previewUrl: null, busy: false, events: { events: [] },
    create, retry, cancel, download, ...overrides };
}

describe('MarketingVisualSection', () => {
  beforeEach(() => { vi.clearAllMocks(); visualState = base(); globalThis.URL.createObjectURL = vi.fn(() => 'blob:preview'); globalThis.URL.revokeObjectURL = vi.fn(); });

  it('renders the empty state when no Marketing Content or revision is selected', () => {
    expect(() => render(<MarketingVisualSection projectId="41" detail={null} revision={null} source={null} draft={null} />))
      .not.toThrow();
    expect(screen.getByText('배너 결과가 아직 없습니다.')).toBeInTheDocument();
    expect(screen.getByText('먼저 마케팅 콘텐츠와 수정 이력을 선택해 주세요.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '광고 배너 만들기' })).toBeDisabled();
  });

  it('preserves the AIdev visual form, source summary, seven tones and source image actions', () => {
    render(<MarketingVisualSection {...props} />);
    expect(screen.getByText('AI 광고 배너 생성')).toBeInTheDocument();
    expect(screen.getByText('기획 자료 1개')).toBeInTheDocument();
    expect(screen.getByText(/직장인/)).toBeInTheDocument();
    expect(screen.getAllByRole('option')).toHaveLength(10);
    expect(screen.getByDisplayValue('기존 콘텐츠 제목')).toBeInTheDocument();
    const file = new File(['image'], '상품.png', { type: 'image/png' });
    fireEvent.change(screen.getByLabelText('상품 이미지 업로드'), { target: { files: [file] } });
    expect(screen.getByText('상품.png')).toBeInTheDocument();
    fireEvent.click(screen.getByText('제거'));
    expect(screen.queryByText('상품.png')).not.toBeInTheDocument();
  });

  it('shows real processing events, cancel, safe failure and retry', () => {
    visualState = base({ busy: true, run: { state: 'RUNNING', taskRunId: 'task-1' }, events: { events: [
      { sequence: 1, messageKey: 'job.marketing.visual.input_validating', status: 'RUNNING' },
      { sequence: 2, messageKey: 'job.marketing.visual.generating', status: 'RUNNING' },
    ] } });
    const rendered = render(<MarketingVisualSection {...props} />);
    expect(screen.getByText('생성 취소')).toBeInTheDocument(); fireEvent.click(screen.getByText('생성 취소')); expect(cancel).toHaveBeenCalled();
    visualState = base({ run: { state: 'FAILED', errorCode: 'IMAGE_GENERATION_FAILED', retryable: true } }); rendered.rerender(<MarketingVisualSection {...props} />);
    expect(screen.getByText(/Provider에 연결하지 못했습니다/)).toBeInTheDocument();
    fireEvent.click(screen.getByText('다시 시도')); expect(retry).toHaveBeenCalled();
  });

  it('renders canonical artifact preview, associated copy, lineage, disclosures and download', () => {
    visualState = base({ previewUrl: 'blob:generated', run: { state: 'SUCCEEDED', result: {
      marketingRevisionId: 'revision-1', callToAction: '지금 확인하기',
      generatedCopy: { badge: '행사', headline: '생성 헤드라인', subheadline: '생성 보조문구' },
      visual: { promotionName: '여름 행사', mood: '고급스러운', bannerFormat: '가로형 배너' }, banner: { model: 'gpt-image-2', size: '1536x1024' },
      artifact: { artifactId: 'artifact-2', filename: 'banner.jpg' },
      legalReview: { requiredDisclosuresApplied: ['개인차 있음'], requiredControlsApplied: ['과장 금지'] },
    } } });
    render(<MarketingVisualSection {...props} />);
    expect(screen.getByAltText('생성된 광고 배너')).toHaveAttribute('src', 'blob:generated');
    expect(screen.getByText('생성 헤드라인')).toBeInTheDocument();
    expect(screen.getByText('지금 확인하기')).toBeInTheDocument();
    expect(screen.getByText('여름 행사')).toBeInTheDocument();
    expect(screen.queryByText(/revision-1/)).not.toBeInTheDocument();
    expect(screen.getByText('사용한 자료')).toBeInTheDocument();
    expect(screen.getByText('개인차 있음')).toBeInTheDocument();
    fireEvent.click(screen.getByText('광고 배너 저장 / 다운로드')); expect(download).toHaveBeenCalled();
  });
});
