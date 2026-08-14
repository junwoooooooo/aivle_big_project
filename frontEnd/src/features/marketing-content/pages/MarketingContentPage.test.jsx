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
        name: '이 컨셉으로 콘텐츠 만들기 →',
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
        name: '이 컨셉으로 콘텐츠 만들기 →',
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
        name: '콘텐츠 생성',
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
});