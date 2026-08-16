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
import useMarketingStrategy from '../hooks/useMarketingStrategy.js';

vi.mock('../hooks/useMarketingContent.js', () => ({
  default: vi.fn(),
}));

vi.mock('../hooks/useMarketingStrategy.js', () => ({
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

const generatedStrategy = {
  contract: 'marketing-strategy-result-v1',
  executiveSummary:
    '검증 결과를 기반으로 초기 고객에게 집중합니다.',
  targetCustomers: ['1~2인 가구'],
  positioning:
    '전기 없이 음식물 냄새를 관리하는 보관 솔루션',
  coreMessages: [
    '버리는 날까지 산뜻하게',
  ],
  channelStrategies: [
    {
      channel: 'Instagram',
      objective: '초기 인지도 확보',
      audience: '1~2인 가구',
      actions: ['사용 상황 콘텐츠 제작'],
      kpis: ['도달과 저장 반응 측정'],
      rationale: '패널 조사 결과 반영',
    },
  ],
  contentPillars: ['문제 공감', '제품 가치'],
  campaignRoadmap: [
    {
      phase: '출시 전',
      objective: '메시지 검증',
      actions: ['메시지 비교'],
      kpis: ['반응 차이 확인'],
    },
  ],
  budgetGuidelines: [
    '재무 분석의 가용 범위 준수',
  ],
  risks: [
    '패널 결과를 전체 시장으로 일반화하지 않음',
  ],
  evidenceRefs: ['MARKET:1'],
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
    ...overrides,
  };
}

function createStrategyHook(overrides = {}) {
  return {
    loading: false,
    generating: false,
    downloading: false,
    active: false,
    ready: true,
    current: true,
    error: null,
    view: {
      reportId: 'strategy-1',
      taskRunId: null,
      status: 'SUCCEEDED',
      ready: true,
      stale: false,
      sourceManifestHash:
        `sha256:${'a'.repeat(64)}`,
      sourceManifest: [
        { type: 'SELECTED_CONCEPT', id: '1' },
        { type: 'LEGAL', id: '1' },
        { type: 'MARKET', id: '1' },
        { type: 'BUSINESS_MODEL', id: '1' },
        { type: 'TECH_OPS', id: '1' },
        { type: 'FINANCE', id: '1' },
        { type: 'FINANCE_REPORT', id: '1' },
        { type: 'TWIN_SURVEY', id: '1' },
      ],
      result: generatedStrategy,
      missingSources: [],
    },
    refresh: vi.fn(),
    generate: vi.fn(),
    download: vi.fn(),
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

    useMarketingStrategy.mockReturnValue(
      createStrategyHook(),
    );
  });

  it('컨셉 확인과 마케팅 전략을 거쳐 생성 설정 단계로 이동한다', () => {
  useMarketingContent.mockReturnValue(
    createHook(),
  );

  renderPage();

  expect(
    screen.getByRole('heading', {
      name: '컨셉 정보를 확인하세요',
    }),
  ).toBeInTheDocument();

  expect(
    screen.queryByRole('heading', {
      name: '만들 콘텐츠를 설정하세요',
    }),
  ).not.toBeInTheDocument();

  // 1단계 분석 자료 → 2단계 마케팅 전략
  fireEvent.click(
    screen.getByRole('button', {
      name: '분석 자료로 마케팅 전략 만들기',
    }),
  );

  expect(
    screen.getByRole('heading', {
      name:
        '분석 결과 기반 마케팅 전략을 확인하세요',
    }),
  ).toBeInTheDocument();

  // 2단계 마케팅 전략 → 3단계 생성 설정
  fireEvent.click(
    screen.getByRole('button', {
      name: '이 전략으로 콘텐츠 만들기',
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

    // 1단계 분석 자료 → 2단계 마케팅 전략
    fireEvent.click(
      screen.getByRole('button', {
        name: '분석 자료로 마케팅 전략 만들기',
      }),
    );

    expect(
      screen.getByRole('heading', {
        name:
          '분석 결과 기반 마케팅 전략을 확인하세요',
      }),
    ).toBeInTheDocument();

    // 2단계 마케팅 전략 → 3단계 생성 설정
    fireEvent.click(
      screen.getByRole('button', {
        name: '이 전략으로 콘텐츠 만들기',
      }),
    );

    expect(
      screen.getByRole('heading', {
        name: '만들 콘텐츠를 설정하세요',
      }),
    ).toBeInTheDocument();

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

    expect(
      screen.getByText('버리는 날까지 산뜻하게'),
    ).toBeInTheDocument();


  });
});
