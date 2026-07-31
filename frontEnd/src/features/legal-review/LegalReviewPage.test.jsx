import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LegalReviewPage from './LegalReviewPage.jsx';
import { useLegalReview } from './hooks/useLegalReview.js';
import { LEGAL_CATEGORY_LABELS } from './model/legalReviewViewModel.js';

vi.mock('./hooks/useLegalReview.js', () => ({ useLegalReview: vi.fn() }));
vi.mock('./hooks/usePlanSnapshot.js', () => ({ usePlanSnapshot: () => null }));
vi.mock('./hooks/useReviewCycle.js', () => ({
  useReviewCycle: () => ({ cycle: null, versions: [], publication: null, refresh: vi.fn() }),
}));
vi.mock('../../shared/api/ApiClientProvider.jsx', () => ({
  ApiClientProvider: ({ children }) => children,
  useApiClient: () => ({ get: vi.fn(), post: vi.fn() }),
}));
vi.mock('../projects/ProjectContext.jsx', () => ({
  useProjectContext: () => ({
    project: { stage: 'LEGAL_REVIEW', stageLabel: '법률 검토', title: '테스트 사업' },
  }),
}));

const CATEGORIES = [
  'BUSINESS_REGISTRATION', 'LICENSE_AND_PERMIT', 'PRIVACY_AND_DATA', 'TERMS_AND_CONTRACT',
  'INTELLECTUAL_PROPERTY', 'CONSUMER_PROTECTION', 'ADVERTISING_AND_MARKETING',
  'LABOR_AND_EMPLOYMENT', 'INDUSTRY_SPECIFIC', 'TAX_AND_FINANCIAL',
];
// 픽스처에서 마지막 범주만 reasoning 없이 두어 구 리뷰(하위호환) 경로를 검증한다
const LEGACY_CATEGORY_LABEL = '세무·재무 규제';

// 같은 할 일이 여러 범주에 반복 등장하는 실제 파이프라인 출력 형태를 재현한다:
// 통신판매업 신고가 사업자등록·소비자보호 양쪽에, 등록료는 조건부, 노무는 폴백 문장.
function actionFor(category, index) {
  if (index === 0 || index === 5) return '통신판매업 신고 (판매 개시 전)';
  if (index === 4) return '등록료 납부 준비 (계획 실행 시)';
  if (index === 7) return '관할 기관 또는 자격 있는 전문가에게 적용 여부와 대응 방법을 확인하세요.';
  return `${category} 절차 이행 (판매 개시 전)`;
}

function reviewFixture() {
  return {
    status: 'NEEDS_REVIEW',
    overallRiskLevel: 'HIGH',
    summary: '10개 범주 중 7개에서 높은 위험이 확인되었습니다.',
    disclaimer: '검토 범위 밖의 법령이 존재할 수 있습니다.',
    provider: 'legal-pipeline',
    modelName: 'claude-sonnet-5(claude-cli)+law.go.kr',
    versionNumber: 1,
    completedAt: '2026-07-29T14:56:35',
    structuredPlanId: 1,
    sourceDocumentVersionId: 1,
    findings: CATEGORIES.map((category, index) => ({
      id: index + 1,
      category,
      displayOrder: index + 1,
      applicability: index === 7 ? 'INSUFFICIENT_INFORMATION' : 'APPLICABLE',
      riskLevel: index === 0 ? 'MEDIUM' : index === 7 ? 'UNKNOWN' : 'HIGH',
      finding: `${category} 판단`,
      rationale: index === 6
        ? '규제 경로: advertising_claims(해당)\n계획 인용: 악취 30% 이상 개선을 핵심 카피로'
        : `${category} 이유`,
      recommendedAction: actionFor(category, index),
      evidenceJson: JSON.stringify([{
        lawName: '개인정보 보호법',
        article: '제30조',
        title: '개인정보 처리방침의 수립 및 공개',
        role: 'REQUIREMENT',
        plainSummary: '고객 정보를 다루려면 처리방침을 만들어 공개해야 합니다.',
        whyRelevant: '구매 이력을 저장해 추천에 쓰므로 개인정보 처리자에 해당합니다.',
        excerpt: '제30조(개인정보 처리방침의 수립 및 공개) ① 개인정보처리자는 …',
        effectiveDate: '2025-10-02',
        lawUrl: 'https://www.law.go.kr/법령/개인정보보호법',
      }]),
      // 마지막 범주만 구 형식(문자열 근거 + reasoning 없음) — 하위호환 경로를 함께 검증한다
      reasoningJson: index === CATEGORIES.length - 1 ? null : JSON.stringify({
        planBasis: { sectionLabels: ['BUSINESS_OVERVIEW'], quotes: ['구매 이력을 저장한다'] },
        regulatoryPath: { topic: '개인정보 처리', status: '해당', reason: '고객 데이터를 수집합니다' },
        obligations: [{ article: '제30조', lawName: '개인정보 보호법', summary: '처리방침 수립·공개' }],
        consequence: { sanctionArticles: ['제75조'], text: '공개하지 않으면 과태료 대상이 될 수 있습니다' },
        conclusion: { action: '개인정보 처리방침 수립', timing: '판매 개시 전' },
      }),
      sourceSectionCodesJson: '["BUSINESS_OVERVIEW"]',
      requiresProfessionalReview: index === 7,
      confidence: 'MEDIUM',
    })),
    questions: [{ id: 1, question: '만 14세 미만 이용자가 있나요?', reason: '아동 개인정보 조항 적용 판단' }],
  };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/projects/1/legal-review']}>
      <Routes><Route path="/projects/:projectId/legal-review" element={<LegalReviewPage />} /></Routes>
    </MemoryRouter>,
  );
}

describe('LegalReviewPage action-first layout', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('pivots duplicated recommendations into a deduplicated to-do checklist', () => {
    useLegalReview.mockReturnValue({ status: 'result', review: reviewFixture(), start: vi.fn(), retry: vi.fn() });
    renderPage();

    // 헤더 메타 + 위험 분포
    expect(screen.getByText('법률·규제 사전검토 보고서')).toBeInTheDocument();
    expect(screen.getByText('legal-pipeline')).toBeInTheDocument();
    const chips = screen.getByLabelText('위험도 분포');
    expect(within(chips).getByText('높음 8')).toBeInTheDocument();

    // 판매 전 할 일: 유니크 액션 7개 (신고 세트는 두 범주가 공유 → 1개로 병합)
    const todo = screen.getByRole('region', { name: /판매 전 할 일/ });
    expect(within(todo).getAllByRole('checkbox')).toHaveLength(7);
    expect(within(todo).getAllByText('통신판매업 신고')).toHaveLength(1);
    const merged = within(todo).getByText('통신판매업 신고').closest('li');
    expect(within(merged).getByText('사업자 등록')).toBeInTheDocument();
    expect(within(merged).getByText('소비자 보호')).toBeInTheDocument();

    // 체크하면 완료 수가 갱신되고 localStorage에 남는다
    expect(screen.getByText(/0\/7 완료/)).toBeInTheDocument();
    fireEvent.click(within(merged).getByRole('checkbox'));
    expect(screen.getByText(/1\/7 완료/)).toBeInTheDocument();
    expect(localStorage.getItem('legal-checklist:1:v1')).toContain('통신판매업 신고');

    // 조건부 주의 + 추가 질문
    const conditional = screen.getByRole('region', { name: /조건부 주의/ });
    expect(within(conditional).getByText('등록료 납부 준비')).toBeInTheDocument();
    expect(screen.getByText('만 14세 미만 이용자가 있나요?')).toBeInTheDocument();

    // 범주별 근거는 하나의 종합 판정으로 모이되 10개 범주가 모두 나열된다 (커버리지 보증)
    const verdict = screen.getByRole('region', { name: /법 범주별 근거/ });
    expect(within(verdict).getByText('종합 판정')).toBeInTheDocument();
    CATEGORIES.forEach((category) => {
      expect(within(verdict).getByText(LEGAL_CATEGORY_LABELS[category])).toBeInTheDocument();
    });
    expect(screen.getByText(/검토 범위 밖의 법령이 존재할 수 있습니다./)).toBeInTheDocument();
  }, 15000); // 풀스위트 병렬 부하 시 5초 기본 타임아웃 초과 (단독 3초)

  it('explains each article in plain language inside the verdict card', () => {
    useLegalReview.mockReturnValue({ status: 'result', review: reviewFixture(), start: vi.fn(), retry: vi.fn() });
    renderPage();

    const verdict = screen.getByRole('region', { name: /법 범주별 근거/ });
    const row = within(verdict).getByText('개인정보·데이터').closest('details');

    // 조문마다 쉬운 설명과 이 사업에 걸리는 이유가 붙는다
    expect(within(row).getByText(/고객 정보를 다루려면 처리방침을 만들어 공개해야 합니다/))
      .toBeInTheDocument();
    expect(within(row).getByText(/구매 이력을 저장해 추천에 쓰므로/)).toBeInTheDocument();
    // 원문은 "발췌"로 명시해 접어 둔다 — 전문이라고 표기하지 않는다
    expect(within(row).getByText(/조문 발췌 보기/)).toBeInTheDocument();
    expect(within(row).getByRole('link', { name: /국가법령정보센터/ })).toBeInTheDocument();

    // 논리 사슬이 단계로 보인다
    expect(within(row).getByText('기획서 근거')).toBeInTheDocument();
    expect(within(row).getByText('걸리는 규제 영역')).toBeInTheDocument();
    expect(within(row).getByText('그래서 생기는 의무')).toBeInTheDocument();
    expect(within(row).getByText('지키지 않으면')).toBeInTheDocument();
    expect(within(row).getByText(/공개하지 않으면 과태료 대상이 될 수 있습니다/)).toBeInTheDocument();
  }, 15000);

  it('falls back to plain rationale when a finding has no reasoning chain', () => {
    useLegalReview.mockReturnValue({ status: 'result', review: reviewFixture(), start: vi.fn(), retry: vi.fn() });
    renderPage();

    const verdict = screen.getByRole('region', { name: /법 범주별 근거/ });
    const legacy = within(verdict)
      .getByText(LEGACY_CATEGORY_LABEL).closest('details');
    expect(within(legacy).getByText('판단 이유')).toBeInTheDocument();
    expect(within(legacy).queryByText('걸리는 규제 영역')).not.toBeInTheDocument();
  }, 15000);

  it('renders the formal report tab with the docx-style sections', () => {
    useLegalReview.mockReturnValue({ status: 'result', review: reviewFixture(), start: vi.fn(), retry: vi.fn() });
    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: '정식 보고서' }));

    expect(screen.getByText('법 률 검 토 보 고 서')).toBeInTheDocument();
    expect(screen.getByText('사전검토 제1-1호')).toBeInTheDocument();
    expect(screen.getByText('Ⅰ. 검토의 목적 및 범위')).toBeInTheDocument();
    expect(screen.getByText('Ⅱ. 사실관계의 요지 (검토의 전제)')).toBeInTheDocument();
    expect(screen.getByText('Ⅲ. 검토 결과의 요지')).toBeInTheDocument();
    expect(screen.getByText('Ⅳ. 항목별 검토')).toBeInTheDocument();
    expect(screen.getByText('Ⅴ. 추가 확인이 필요한 사항')).toBeInTheDocument();
    expect(screen.getByText('Ⅵ. 결론')).toBeInTheDocument();
    expect(screen.getByText('[별첨] 검토 대상 법령 및 조문 일람')).toBeInTheDocument();

    // 이행사항 일람에도 병합된 액션이 한 번만 나온다
    expect(screen.getByText('【이행사항 일람】')).toBeInTheDocument();
    expect(screen.getAllByText('통신판매업 신고')).toHaveLength(1);
    expect(screen.getByText('등록료 납부 준비')).toBeInTheDocument();

    // 계획 인용 + 책임의 한계
    expect(screen.getByText(/악취 30% 이상 개선을 핵심 카피로/)).toBeInTheDocument();
    expect(screen.getByText('책임의 한계')).toBeInTheDocument();
  });

  it('hides the to-do section when every recommendation is a no-action fallback', () => {
    const review = reviewFixture();
    review.findings = review.findings.map((item) => ({
      ...item,
      recommendedAction: '현재 계획 기준으로는 별도 조치가 필요하지 않습니다. 사업 내용이 바뀌면 다시 확인하세요.',
    }));
    useLegalReview.mockReturnValue({ status: 'result', review, start: vi.fn(), retry: vi.fn() });
    renderPage();
    expect(screen.queryByRole('region', { name: /판매 전 할 일/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('region', { name: /조건부 주의/ })).not.toBeInTheDocument();
  });
});
