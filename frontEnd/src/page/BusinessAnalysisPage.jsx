import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Head from '../Head';
import './BusinessAnalysisPage.css';

function BusinessAnalysisPage() {
    const navigate = useNavigate();

    // 선택된 카테고리 상태 ('market' | 'bm' | 'tech' | null)
    const [selectedCategory, setSelectedCategory] = useState(null);

    // 카테고리별 독립된 개별 보고서 데이터 (추후 AI 에이전트 응답값 매핑)
    const categoryReports = {
        market: {
            id: 'market',
            title: '시장 분석',
            icon: '📊',
            shortDesc: '목표 시장의 규모, 성장성 및 주요 타깃 고객층 분석 결과입니다.',
            // 시장 분석 전용 독립 보고서 내용
            reportContent: `[시장 분석 보고서]

1. 목표 시장 규모 (TAM / SAM / SOM)
   - 전체 시장(TAM): 약 1.2조 원
   - 유효 시장(SAM): 약 3,500억 원
   - 목표 점유 시장(SOM): 초기 3년 내 150억 원 달성 목표

2. 타깃 고객 페르소나 및 니즈
   - 주요 연령대: 20대 후반 ~ 30대 직장인
   - 핵심 니즈: 빠른 의사결정과 데이터 기반의 객관적 검증 리포트`,
        },
        bm: {
            id: 'bm',
            title: 'BM 분석',
            icon: '📈',
            shortDesc: '수익 구조, 가격 정책 및 비즈니스 모델의 지속 가능성 평가 결과입니다.',
            // BM 분석 전용 독립 보고서 내용
            reportContent: `[BM 분석 보고서]

1. 수익 구조 (Revenue Model)
   - B2B 월간/연간 구독형 서비스 (SaaS)
   - 엔터프라이즈 맞춤형 AI 시뮬레이션 및 커스텀 패키지 제공

2. 가격 책정 전략 (Pricing)
   - Basic Plan: 월 49,000원 (기본 시뮬레이션 제공)
   - Pro Plan: 월 199,000원 (고급 분석 및 시나리오 테스트 포함)`,
        },
        tech: {
            id: 'tech',
            title: '기술/운영',
            icon: '⚙️',
            shortDesc: '핵심 개발 기술, 인프라 요건 및 운영 리스크 관리 분석 결과입니다.',
            // 기술/운영 전용 독립 보고서 내용
            reportContent: `[기술/운영 보고서]

1. 핵심 기술 아키텍처
   - Multi-Agent 기반 데이터 자동 수집 및 분석 워크플로우
   - FastAPI + React 기반 고성능 비동기 레이어 구성

2. 시스템 인프라 및 보안
   - 개인정보 비식별화 처리 프로세스 자동 적용
   - AWS 오토스케일링 인프라 기반의 99.9% 가동률 확보`,
        },
    };

    // 카드 클릭 시 해당 카테고리 보고서만 확대
    const handleCardClick = (categoryId) => {
        setSelectedCategory(categoryId);
    };

    // 목록으로 돌아가기
    const handleBackToList = () => {
        setSelectedCategory(null);
    };

    // 선택된 카테고리의 단일 보고서만 파일로 다운로드
    const handleDownloadSingleReport = (reportItem) => {
        const element = document.createElement('a');
        const file = new Blob([reportItem.reportContent], { type: 'text/plain;charset=utf-8' });
        element.href = URL.createObjectURL(file);
        element.download = `${reportItem.title}_보고서.txt`;
        document.body.appendChild(element);
        element.click();
        document.body.removeChild(element);
    };

    // 현재 선택된 단일 보고서 객체
    const currentReport = selectedCategory ? categoryReports[selectedCategory] : null;

    return (
        <div className="business-analysis-container">

            <div className="business-analysis-body">
                {/* 좌측 사이드바 */}
                <aside className="virtual-market-sidebar">
                    <button
                        type="button"
                        className="virtual-market-home-button"
                        onClick={() => navigate('/')}
                        aria-label="홈으로 이동"
                    >
                        ⌂
                    </button>

                    <nav className="virtual-market-side-menu" aria-label="사용자 메뉴">
                        <button type="button" onClick={() => navigate('/project-create')}>
                            기획서 등록
                        </button>
                        <button
                            type="button"
                            className="active"
                            onClick={() => navigate('/business-analysis')}
                        >
                            사업성 분석
                        </button>
                        <button type="button" onClick={() => navigate('/virtual-market')}>
                            가상 시장 검증
                        </button>
                        <button type="button" onClick={() => navigate('/dashboard')}>
                            대시 보드
                        </button>
                    </nav>
                </aside>

                {/* 메인 영역 */}
                <main className="business-analysis-main">
                    {/* [상태 1] 아무 카드도 클릭하지 않은 3가지 항목 선택 화면 */}
                    {!selectedCategory ? (
                        <div className="business-content-wrapper">
                            <div className="business-header-group">
                                <span className="business-badge">사업성 분석</span>
                                <p className="business-header-text">
                                    기획서 하나면 충분합니다. 보고 싶으신 분석 카드를 선택해 주세요.
                                </p>
                            </div>

                            {/* 3가지 카드 목록 */}
                            <div className="analysis-cards-grid">
                                {Object.values(categoryReports).map((item) => (
                                    <div
                                        key={item.id}
                                        className="analysis-card clickable"
                                        onClick={() => handleCardClick(item.id)}
                                    >
                                        <div className="card-header-chip">{item.title}</div>
                                        <div className="card-content-box">
                                            <div className="card-icon">{item.icon}</div>
                                            <p className="card-description">{item.shortDesc}</p>
                                        </div>
                                    </div>
                                ))}
                            </div>

                            <div className="business-action-group">
                                <button
                                    type="button"
                                    className="action-btn financial-next-btn"
                                    onClick={() => navigate('/financial-analysis')}
                                >
                                    재무분석 화면으로
                                </button>
                            </div>
                        </div>
                    ) : (
                        /* [상태 2] 특정 카드가 선택되었을 때: 해당 보고서 전용 확대 View */
                        <div className="detail-content-wrapper">
                            {/* 상단: 선택된 단일 카테고리 타원형 제목 (예: 시장 분석) & 목록으로 버튼 */}
                            <div className="detail-header-group">
                                <span className="detail-title-chip">{currentReport.title}</span>
                                <button
                                    type="button"
                                    className="back-to-list-btn"
                                    onClick={handleBackToList}
                                >
                                    ← 분석 목록으로
                                </button>
                            </div>

                            {/* 해당 분석 전용 개별 보고서만 표시되는 넓은 민트색 컨테이너 */}
                            <div className="large-detail-card">
                                <div className="detail-icon">{currentReport.icon}</div>
                                <pre className="detail-text-content">
                  {currentReport.reportContent}
                </pre>
                            </div>

                            {/* 우측 하단: 클릭한 카테고리의 전용 보고서 다운로드 버튼 */}
                            <div className="detail-action-group">
                                <button
                                    type="button"
                                    className="download-report-btn"
                                    onClick={() => handleDownloadSingleReport(currentReport)}
                                >
                                    {currentReport.title} <br />
                                    보고서 다운로드 📥
                                </button>
                            </div>
                        </div>
                    )}
                </main>
            </div>
        </div>
    );
}

export default BusinessAnalysisPage;