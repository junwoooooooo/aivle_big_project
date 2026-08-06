import {
  useState,
} from 'react';

import {
  useLocation,
} from 'react-router-dom';

import './BusinessAnalysis.css';

const BUSINESS_ANALYSIS_REPORTS = Object.freeze({
  market: Object.freeze({
    id: 'market',
    title: '시장 분석',
    icon: '📊',
    description: '목표 시장의 규모, 성장성 및 주요 타깃 고객층 분석',
    downloadFileName: '시장 분석_보고서.txt',
    reportContent: `[시장 분석 보고서]

※ 이 내용은 AI 분석 연동 전 화면 검증을 위한 데모 결과입니다.

1. 목표 시장 규모 (TAM / SAM / SOM)
   - 전체 시장(TAM): 약 1.2조 원
   - 유효 시장(SAM): 약 3,500억 원
   - 목표 점유 시장(SOM): 초기 3년 내 150억 원 달성 목표

2. 타깃 고객 페르소나 및 니즈
   - 주요 연령대: 20대 후반 ~ 30대 직장인
   - 핵심 니즈: 빠른 의사결정과 데이터 기반의 객관적 검증 리포트`,
  }),
  bm: Object.freeze({
    id: 'bm',
    title: 'BM 분석',
    icon: '📈',
    description: '수익 구조, 가격 정책 및 비즈니스 모델 지속 가능성 분석',
    downloadFileName: 'BM 분석_보고서.txt',
    reportContent: `[BM 분석 보고서]

※ 이 내용은 AI 분석 연동 전 화면 검증을 위한 데모 결과입니다.

1. 수익 구조 (Revenue Model)
   - B2B 월간/연간 구독형 서비스 (SaaS)
   - 엔터프라이즈 맞춤형 AI 시뮬레이션 및 커스텀 패키지 제공

2. 가격 책정 전략 (Pricing)
   - Basic Plan: 월 49,000원 (기본 시뮬레이션 제공)
   - Pro Plan: 월 199,000원 (고급 분석 및 시나리오 테스트 포함)`,
  }),
  tech: Object.freeze({
    id: 'tech',
    title: '기술/운영',
    icon: '⚙️',
    description: '핵심 기술, 인프라 요건 및 운영 리스크 분석',
    downloadFileName: '기술운영_보고서.txt',
    reportContent: `[기술/운영 보고서]

※ 이 내용은 AI 분석 연동 전 화면 검증을 위한 데모 결과입니다.

1. 핵심 기술 아키텍처
   - Multi-Agent 기반 데이터 자동 수집 및 분석 워크플로우
   - FastAPI + React 기반 고성능 비동기 레이어 구성

2. 시스템 인프라 및 보안
   - 개인정보 비식별화 처리 프로세스 자동 적용
   - AWS 오토스케일링 인프라 기반의 99.9% 가동률 확보`,
  }),
});

const BUSINESS_ANALYSIS_REPORT_LIST = Object.freeze(
  Object.values(BUSINESS_ANALYSIS_REPORTS),
);

const formatFileSize = (size) => {
  if (!Number.isFinite(size) || size < 0) {
    return null;
  }

  if (size < 1024 * 1024) {
    return `${Math.max(1, Math.ceil(size / 1024))}KB`;
  }

  return `${(size / (1024 * 1024)).toFixed(1)}MB`;
};

function BusinessAnalysis() {
  const location = useLocation();
  const [selectedCategory, setSelectedCategory] = useState(null);

  const fileName = location.state?.fileName;
  const legalResult = location.state?.legalResult;
  const fileMetadata = location.state?.fileMetadata;
  const currentReport = selectedCategory
    ? BUSINESS_ANALYSIS_REPORTS[selectedCategory]
    : null;

  const fileSize = formatFileSize(fileMetadata?.size);
  const hasProjectContext =
    typeof fileName === 'string' && fileName.trim().length > 0;

  const handleDownloadReport = (report) => {
    const blob = new Blob(
      [report.reportContent],
      {
        type: 'text/plain;charset=utf-8',
      },
    );
    const objectUrl = URL.createObjectURL(blob);
    const downloadLink = document.createElement('a');

    try {
      downloadLink.href = objectUrl;
      downloadLink.download = report.downloadFileName;
      document.body.appendChild(downloadLink);
      downloadLink.click();
    } finally {
      downloadLink.remove();
      URL.revokeObjectURL(objectUrl);
    }
  };

  return (
    <section className="business-analysis-page">
      <div className="business-analysis-content">
        <header className="business-analysis-header">
          <div>
            <span className="business-analysis-chip">사업성 분석</span>
            <h1>
              {currentReport
                ? currentReport.title
                : '사업성 분석'}
            </h1>
            <p>
              {currentReport
                ? '선택한 영역의 예시 분석 보고서를 확인합니다.'
                : '기획서 하나면 충분합니다. 확인할 분석 카드를 선택해 주세요.'}
            </p>
          </div>

          <span className="business-analysis-demo-badge">
            AI 연동 전 Mock 결과
          </span>
        </header>

        <section
          className={
            hasProjectContext
              ? 'business-analysis-context has-file'
              : 'business-analysis-context no-file'
          }
          aria-label="분석 대상 기획서 및 법률 검토 정보"
        >
          {hasProjectContext ? (
            <>
              <div className="business-analysis-file-summary">
                <span>분석 대상 파일</span>
                <strong title={fileName}>{fileName}</strong>
                {fileSize && <small>{fileSize}</small>}
              </div>

              <div className="business-analysis-legal-summary">
                <span>법률 검토 상태</span>
                <strong
                  className={
                    legalResult?.status === 'PASS'
                      ? 'business-analysis-legal-pass'
                      : 'business-analysis-legal-fail'
                  }
                >
                  {legalResult?.status === 'PASS'
                    ? 'PASS · 통과'
                    : legalResult?.status === 'FAIL'
                      ? 'FAIL · 불통과'
                      : '검토 결과 없음'}
                </strong>
              </div>

              <div className="business-analysis-legal-reason">
                <span>법률 검토 사유</span>
                <p>
                  {legalResult?.reason ||
                    '전달된 법률 검토 사유가 없습니다.'}
                </p>
              </div>
            </>
          ) : (
            <div className="business-analysis-empty-context">
              <strong>선택된 기획서 정보가 없습니다.</strong>
              <p>
                직접 접근한 경우에도 아래 데모 분석 화면은 확인할 수 있습니다.
              </p>
            </div>
          )}
        </section>

        {!currentReport ? (
          <>
            <div
              className="business-analysis-card-grid"
              aria-label="사업성 분석 항목"
            >
              {BUSINESS_ANALYSIS_REPORT_LIST.map((report) => (
                <button
                  type="button"
                  className="business-card-button"
                  key={report.id}
                  onClick={() => setSelectedCategory(report.id)}
                  aria-label={`${report.title} 데모 보고서 보기`}
                >
                  <span className="business-card-title">
                    {report.title}
                  </span>
                  <span className="business-card-body">
                    <span className="business-card-icon" aria-hidden="true">
                      {report.icon}
                    </span>
                    <span className="business-card-description">
                      {report.description}
                    </span>
                    <span className="business-card-link">
                      상세 보고서 보기 →
                    </span>
                  </span>
                </button>
              ))}
            </div>

            <div className="business-analysis-financial-area">
              <button
                type="button"
                className="business-analysis-action-button"
                disabled
                aria-describedby="business-analysis-financial-note"
              >
                재무 분석 다음 단계
              </button>
              <span id="business-analysis-financial-note">
                재무 분석 화면 준비 중
              </span>
            </div>
          </>
        ) : (
          <article className="business-detail-view">
            <div className="business-detail-toolbar">
              <button
                type="button"
                className="business-detail-back-button"
                onClick={() => setSelectedCategory(null)}
              >
                ← 분석 목록으로 돌아가기
              </button>
            </div>

            <div className="business-detail-report">
              <span className="business-detail-icon" aria-hidden="true">
                {currentReport.icon}
              </span>
              <pre className="business-detail-text">
                {currentReport.reportContent}
              </pre>
            </div>

            <div className="business-detail-actions">
              <button
                type="button"
                className="business-analysis-action-button"
                onClick={() => handleDownloadReport(currentReport)}
              >
                {currentReport.title} 보고서 다운로드
              </button>
            </div>
          </article>
        )}
      </div>
    </section>
  );
}

export default BusinessAnalysis;
