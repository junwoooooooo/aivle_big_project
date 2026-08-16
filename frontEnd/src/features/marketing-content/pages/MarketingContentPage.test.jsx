import {
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import {
  MemoryRouter,
  Route,
  Routes,
} from 'react-router-dom';
import {
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest';
import MarketingContentPage from './MarketingContentPage.jsx';
import useMarketingContent from '../hooks/useMarketingContent.js';

vi.mock('../hooks/useMarketingContent.js', () => ({
  default: vi.fn(),
}));

const generatedResult = {
  contract: 'marketing-content-result-v1',
  contentType: 'SOCIAL_POST',
  title: '버리는 날까지 산뜻하게',
  body: '1~2인 가구를 위한 무전원 음식물 보관함',
  callToAction: '제품 자세히 보기',
  hashtags: ['프레시락미니', '무전원'],
  imageBrief: '밝고 친근한 제품 광고 이미지',
  legalReview: {
    compliant: true,
    warnings: [],
    requiredDisclosuresApplied: [],
  },
  artifactRefs: [],
};

const completedDetail = {
  content: {
    contentId: 'content-1',
    contentType: 'SOCIAL_POST',
    title: '프레시락 미니 콘텐츠',
    status: 'COMPLETED',
    activeJobId: null,
    marketingSourceSnapshotId: 'source-1',
    currentRevisionNumber: 1,
    finalizedAt: null,
  },
  sourceSnapshot: {
    conceptName: '프레시락 미니',
    targetSegment: '1~2인 가구',
    valueProposition: '전기 없이 음식물 냄새 관리',
    prohibitedClaims: [],
    requiredDisclosures: [],
  },
  revisions: [
    {
      revisionId: 'revision-1',
      revisionNumber: 1,
      revisionType: 'GENERATED',
      result: generatedResult,
    },
  ],
  artifactRefs: [],
};

function createHook(overrides = {}) {
  return {
    loading: false,
    list: [],
    source: {
      snapshotId: 'source-1',
      snapshot: {
        conceptName: '프레시락 미니',
        targetSegment: '1~2인 가구',
        valueProposition:
          '전기 없이 음식물 냄새를 관리합니다.',
        prohibitedClaims: [],
        requiredDisclosures: [],
      },
    },
    selected: null,
    error: null,
    saving: false,
    uploading: false,
    active: false,
    status: 'IDLE',
    jobEvents: {
      events: [],
    },
    refresh: vi.fn(),
    open: vi.fn(),
    uploadReference: vi.fn(),
    create: vi.fn(),
    save: vi.fn(),
    finalize: vi.fn(),
    regenerate: vi.fn(),
    retry: vi.fn(),
    ...overrides,
  };
}

function renderPage() {
  return render(
    <MemoryRouter
      initialEntries={[
        '/app/projects/1/marketing-content',
      ]}
    >
      <Routes>
        <Route
          path="/app/projects/:projectId/marketing-content"
          element={<MarketingContentPage />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe('MarketingContentPage 단계형 화면', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('공식 명칭과 게시 전 AI 초안 안내를 눈에 보이게 제공한다', () => {
    useMarketingContent.mockReturnValue(createHook());
    renderPage();
    expect(screen.getByText('마케팅 실행')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '현재 확정된 컨셉으로 마케팅 초안을 만드세요' })).toBeInTheDocument();
    expect(screen.getByText(/AI가 현재 확정된 컨셉을 바탕으로 만든 초안입니다/)).toBeInTheDocument();
  });

  it('컨셉 확인 후 생성 설정 단계로 이동한다', () => {
    useMarketingContent.mockReturnValue(createHook());

    renderPage();

    expect(
      screen.getByRole('heading', {
        name: '컨셉 정보를 확인하세요',
      }),
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('heading', {
        name: '어떤 콘텐츠를 만들까요?',
      }),
    ).not.toBeInTheDocument();

    fireEvent.click(
      screen.getByRole('button', {
        name: '이 사업안으로 마케팅 초안 만들기',
      }),
    );

    expect(
      screen.getByRole('heading', {
        name: '만들 콘텐츠를 설정하세요',
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('heading', {
        name: '어떤 콘텐츠를 만들까요?',
      }),
    ).toBeInTheDocument();
  });

  it('저장된 콘텐츠를 선택하면 결과 단계로 이동한다', async () => {
    const open = vi.fn().mockResolvedValue(
      completedDetail,
    );

    useMarketingContent.mockReturnValue(
      createHook({
        list: [completedDetail.content],
        selected: completedDetail,
        open,
      }),
    );

    renderPage();

    fireEvent.click(
      screen.getByRole('button', {
        name: /프레시락 미니 콘텐츠/,
      }),
    );

    await waitFor(() => {
      expect(open).toHaveBeenCalledWith('content-1');
    });

    expect(
      await screen.findByRole('heading', {
        name: '생성 결과를 확인하세요',
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByText('버리는 날까지 산뜻하게'),
    ).toBeInTheDocument();
  });

  it('생성 완료 상태가 반영되면 결과 단계로 자동 이동한다', async () => {
    const create = vi.fn().mockResolvedValue({
      content: {
        contentId: 'content-1',
        status: 'RUNNING',
      },
    });

    let hookValue = createHook({
      create,
    });

    useMarketingContent.mockImplementation(
      () => hookValue,
    );

    const page = renderPage();

    fireEvent.click(
      screen.getByRole('button', {
        name: '이 사업안으로 마케팅 초안 만들기',
      }),
    );

    fireEvent.change(
      screen.getByLabelText('채널'),
      {
        target: {
          value: 'Instagram',
        },
      },
    );

    fireEvent.change(
      screen.getByLabelText('목적'),
      {
        target: {
          value: '출시 인지도 확보',
        },
      },
    );

    fireEvent.click(
      screen.getByRole('button', {
        name: '마케팅 초안 만들기',
      }),
    );

    await waitFor(() => {
      expect(create).toHaveBeenCalledTimes(1);
    });

    hookValue = createHook({
      create,
      list: [completedDetail.content],
      selected: completedDetail,
      status: 'COMPLETED',
    });

    page.rerender(
      <MemoryRouter
        initialEntries={[
          '/app/projects/1/marketing-content',
        ]}
      >
        <Routes>
          <Route
            path="/app/projects/:projectId/marketing-content"
            element={<MarketingContentPage />}
          />
        </Routes>
      </MemoryRouter>,
    );

    expect(
      await screen.findByRole('heading', {
        name: '생성 결과를 확인하세요',
      }),
    ).toBeInTheDocument();
  });

  it('실패한 실행은 같은 source 재시도 CTA만 제공한다', async () => {
    const retry = vi.fn();
    const failed = { ...completedDetail, content: { ...completedDetail.content,
      contentId: 'failed-1', status: 'FAILED', retryable: true }, revisions: [] };
    useMarketingContent.mockReturnValue(createHook({ list: [failed.content], selected: failed,
      open: vi.fn().mockResolvedValue(failed), retry }));
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /프레시락 미니 콘텐츠/ }));
    fireEvent.click(await screen.findByRole('button', { name: '다시 시도' }));
    expect(retry).toHaveBeenCalledTimes(1);
    expect(screen.queryByText('TaskRun')).not.toBeInTheDocument();
    expect(screen.queryByText('MODULE_INPUT_STALE')).not.toBeInTheDocument();
  });

  it('이전 컨셉의 결과를 보존해 열람하고 현재 컨셉 신규 생성 CTA를 제공한다', async () => {
    const regenerate = vi.fn();
    const stale = { ...completedDetail, content: { ...completedDetail.content,
      contentId: 'stale-1', status: 'STALE' } };
    useMarketingContent.mockReturnValue(createHook({ list: [stale.content], selected: stale,
      open: vi.fn().mockResolvedValue(stale), regenerate }));
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /프레시락 미니 콘텐츠/ }));
    expect(await screen.findByText(/이전 컨셉을 기준으로 만든 결과입니다/)).toBeInTheDocument();
    expect(screen.getByText('버리는 날까지 산뜻하게')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '현재 컨셉으로 새 초안 만들기' }));
    expect(regenerate).toHaveBeenCalledTimes(1);
  });

  it('성공 결과에서 검토와 다른 초안 만들기를 명시적으로 선택하며 자동 이동하지 않는다', async () => {
    const regenerate = vi.fn();
    useMarketingContent.mockReturnValue(createHook({ list: [completedDetail.content], selected: completedDetail,
      open: vi.fn().mockResolvedValue(completedDetail), regenerate }));
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /프레시락 미니 콘텐츠/ }));
    expect(await screen.findByRole('heading', { name: '생성 결과를 확인하세요' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '다른 초안 만들기' }));
    expect(regenerate).toHaveBeenCalledTimes(1);
    expect(window.location.pathname).not.toContain('marketing-test');
  });
});
