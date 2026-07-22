import React, { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Head from '../Head';
import './ProjectCreate.css';

function ProjectCreate() {
    const navigate = useNavigate();
    const fileInputRef = useRef(null);
    const [selectedFile, setSelectedFile] = useState(null);
    const [isSavedToDb, setIsSavedToDb] = useState(false);

    // 파일 업로드 버튼 클릭 시 숨겨진 input[type="file"] 실행
    const handleUploadClick = () => {
        fileInputRef.current?.click();
    };

    // 파일 선택 처리 (선택 시 DB 저장 완료 상태로 가상 전환)
    const handleFileChange = (e) => {
        const file = e.target.files?.[0];
        if (file) {
            setSelectedFile(file);
            // DB 저장 완료 메시지 표시 (추후 백엔드 API 성공 응답 시 호출하도록 연동)
            setIsSavedToDb(true);
        }
    };

    // 취소 버튼 클릭 시 홈으로 이동
    const handleCancel = () => {
        navigate('/');
    };

    // 다음 버튼 클릭 처리 -> 법률/규제 검토 단계로 이동
    const handleNext = () => {
        if (!selectedFile) {
            alert('사업기획서 파일을 먼저 업로드해 주세요.');
            return;
        }

        // 법률/규제 검토 단계로 이동 (선택된 파일 정보 전달)
        navigate('/legal-check', { state: { fileName: selectedFile.name } });
    };

    // 가이드라인 워드 파일 다운로드
    const handleDownloadGuidance = () => {
        // public/business_plan_guideline.docx 파일을 다운로드
        const link = document.createElement('a');
        link.href = './public/business_plan_guideline.docx';
        link.download = '사업기획서_작성_가이드라인.docx'; // 다운로드될 때 사용자가 보게 될 파일명
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    };

    return (
        <div className="project-create-container">

            <div className="project-create-body">
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
                        <button
                            type="button"
                            className="active"
                            onClick={() => navigate('/project-create')}
                        >
                            기획서 등록
                        </button>
                        <button
                            type="button"
                            onClick={() => navigate('/business-analysis')}
                        >
                            사업성 분석
                        </button>
                        <button
                            type="button"
                            onClick={() => navigate('/virtual-market')}
                        >
                            가상 시장 검증
                        </button>
                        <button
                            type="button"
                            onClick={() => navigate('/dashboard')}
                        >
                            대시 보드
                        </button>
                    </nav>
                </aside>

                {/* 메인 본문 영역 */}
                <main className="project-create-main">
                    <div className="create-content-wrapper">
                        {/* 상단 레이아웃: 가이드라인 버튼 + 안내 문구 */}
                        <div className="guide-header-group">
                            <button
                                type="button"
                                className="guide-download-btn"
                                onClick={handleDownloadGuidance}
                            >
                                가이드라인 파일 다운로드 📥
                            </button>
                            <p className="guide-title-text">
                                사업기획서를 업로드 해주세요 사업기획서는 다음과 같은 내용을 포함하고 있어야합니다
                            </p>
                        </div>

                        {/* 12가지 포함 항목 박스 */}
                        <div className="guideline-box">
                            <div className="guideline-column">
                                <p>1. 사업 개요</p>
                                <p>2. 시장 규모</p>
                                <p>3. 타겟 고객</p>
                                <p>4. 경쟁 분석</p>
                                <p>5. 제품 · 서비스</p>
                                <p>6. 비즈니스 모델</p>
                            </div>
                            <div className="guideline-column">
                                <p>7. 원가 · 수익성</p>
                                <p>8. 판매 목표 · 재무 추정</p>
                                <p>9. 기술 · 생산</p>
                                <p>10. 법률 · 인허가</p>
                                <p>11. 일정 · 리스크</p>
                                <p>12. 근거 자료 목록</p>
                            </div>
                        </div>

                        {/* 업로드된 파일 정보 표시 영역 */}
                        {selectedFile && (
                            <div className="uploaded-file-info">
                                📄 선택된 파일: <strong>{selectedFile.name}</strong>
                            </div>
                        )}

                        {/* 숨겨진 파일 Input */}
                        <input
                            type="file"
                            ref={fileInputRef}
                            style={{ display: 'none' }}
                            accept=".docx, .doc, .pdf, .hwp"
                            onChange={handleFileChange}
                        />

                        {/* 하단 버튼 그룹 (취소 / 파일 업로드 / 다음) */}
                        <div className="create-action-buttons">
                            <button
                                type="button"
                                className="action-btn cancel-btn"
                                onClick={handleCancel}
                            >
                                취소
                            </button>
                            <button
                                type="button"
                                className="action-btn upload-btn"
                                onClick={handleUploadClick}
                            >
                                파일 업로드
                            </button>
                            <button
                                type="button"
                                className="action-btn next-btn"
                                onClick={handleNext}
                            >
                                다음
                            </button>
                        </div>
                    </div>
                </main>
            </div>
        </div>
    );
}

export default ProjectCreate;