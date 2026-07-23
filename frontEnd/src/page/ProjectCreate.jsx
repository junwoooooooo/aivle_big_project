import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom'; // 1. useNavigate 임포트
import './ProjectCreate.css';

const GUIDELINE_ITEMS = [
  "사업 개요 (Business Overview)",
  "시장 규모 (Market Size)",
  "타겟 고객 (Target Customer)",
  "경쟁 분석 (Competitive Analysis)",
  "제품 · 서비스 (Product / Service)",
  "비즈니스 모델 (Business Model)",
  "원가 · 수익성 (Cost & Profitability)",
  "판매 목표 · 재무 추정 (Sales Goals & Financials)",
  "기술 · 생산 (Technology & Production)",
  "법률 · 인허가 (Legal & Compliance)",
  "일정 · 리스크 (Schedule & Risk Management)",
  "근거 자료 목록 (References)"
];

export default function ProjectCreate() {
  const navigate = useNavigate(); // 2. navigate 객체 생성

  const [file, setFile] = useState(null);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [analysisResult, setAnalysisResult] = useState(null);
  const [loadingDots, setLoadingDots] = useState(".");

  // '검토중.', '검토중..', '검토중...' 타이머 애니메이션
  useEffect(() => {
    let interval = null;
    if (isAnalyzing) {
      interval = setInterval(() => {
        setLoadingDots((prev) => {
          if (prev === ".") return "..";
          if (prev === "..") return "...";
          return ".";
        });
      }, 500);
    } else {
      setLoadingDots(".");
    }
    return () => clearInterval(interval);
  }, [isAnalyzing]);

  // 파일 선택
  const handleFileChange = (e) => {
    if (e.target.files && e.target.files[0]) {
      setFile(e.target.files[0]);
    }
  };

  // 파일 업로드 및 AI 분석
  const handleUpload = async () => {
    if (!file) return;

    const formData = new FormData();
    formData.append("file", file);

    try {
      setIsAnalyzing(true);
      setAnalysisResult(null);

      const response = await fetch("http://localhost:8080/api/project/upload-guideline", {
        method: "POST",
        body: formData,
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();
      setAnalysisResult(data);
    } catch (error) {
      console.error("파일 검증 분석 오류:", error);
      alert("파일 분석 중 오류가 발생했습니다. 백엔드 서버 연결 상태를 확인해 주세요.");
    } finally {
      setIsAnalyzing(false);
    }
  };

  // 항목별 결과 매핑
  const getItemAnalysis = (itemIndex) => {
    if (!analysisResult || !analysisResult.itemResults) return null;
    return analysisResult.itemResults.find((r) => r.itemNumber === itemIndex + 1);
  };
  const handleNextStep = async () => {
    if (!analysisResult || !analysisResult.overallPassed) return;

    try {
      // 1) 백엔드로 분석 결과 전달 (선택 사항: 에러 나도 catch에서 다음 페이지 이동)
      const response = await fetch("http://localhost:8080/api/project/save-analysis", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          fileName: file?.name || "사업기획서.docx",
          analysisData: analysisResult
        }),
      });

      if (response.ok) {
        const data = await response.json();
        // 저장 성공 시 projectId와 함께 이동
        navigate("/legal-check", { state: { projectId: data.projectId, analysisResult } });
        return;
      }
    } catch (error) {
      console.warn("DB 저장 미완결 상태이므로 임시 이동합니다:", error);
    }
    // 2) 백엔드 DB 연동 전이어도 다음 페이지(/legal-check)로 바로 이동
    navigate("/legal-check", { state: { analysisResult, fileName: file?.name } });
  };

  return (
      <div className="project-create-page">
        <div className="project-create-content">

          {/* 헤더 영역 */}
          <div className="project-create-header">
            <span className="project-create-chip">STEP 01</span>
            <h1>사업기획서 업로드 및 AI 검증</h1>
            <p>
              가이드라인 서식에 맞춰 작성된 <strong>.docx</strong> 사업기획서를 업로드하시면,
              AI 에이전트가 12가지 필수 항목의 데이터 충실도를 사전 검증합니다.
            </p>
          </div>

          {/* 양식 다운로드 버튼 */}
          <a
              href="/templates/사업기획서_작성_가이드라인.docx"
              download
              className="project-create-guide-download"
          >
            📄 사업기획서 작성 가이드라인 양식 다운로드
          </a>

          {/* 파일 업로드 영역 */}
          <div className="project-create-upload-area">
            {!file ? (
                <label className="project-create-upload-button" style={{ display: 'block', textAlign: 'center' }}>
                  <input
                      type="file"
                      accept=".docx"
                      onChange={handleFileChange}
                      style={{ display: "none" }}
                      disabled={isAnalyzing}
                  />
                  📌 사업기획서 파일(.docx) 선택하기
                  <small>클릭하여 작성하신 워드 문서를 선택해 주세요.</small>
                </label>
            ) : (
                <div className="project-create-file-card">
                  <span className="project-create-file-icon">📄</span>
                  <div>
                    <strong>{file.name}</strong>
                    <span>{(file.size / (1024 * 1024)).toFixed(2)} MB</span>
                  </div>
                  <button onClick={() => { setFile(null); setAnalysisResult(null); }} disabled={isAnalyzing}>
                    파일 변경
                  </button>
                  <button
                      onClick={handleUpload}
                      disabled={isAnalyzing}
                      className="project-create-verify-btn"
                  >
                    {isAnalyzing ? `검토중${loadingDots}` : "AI 검증 실행"}
                  </button>
                </div>
            )}
          </div>

          {/* 가이드라인 항목 & AI 검증 상태 리스트 */}
          <div className="project-create-guideline" aria-label="사업기획서 필수 항목">
            {GUIDELINE_ITEMS.map((item, index) => {
              const itemResult = getItemAnalysis(index);

              return (
                  <div key={item} className="guideline-item-container">
                    <p className="guideline-item-title">
                      <span>{index + 1}.</span> {item}
                    </p>

                    {/* 우측 배지 표현 */}
                    <div className="guideline-status-wrapper">
                      {isAnalyzing ? (
                          <span className="badge badge-analyzing">
                      검토중{loadingDots}
                    </span>
                      ) : itemResult ? (
                          itemResult.status === "PASS" ? (
                              <span className="badge badge-pass">PASS</span>
                          ) : (
                              <div className="badge-reject-box">
                                <span className="badge badge-reject">REJECT</span>
                                <p className="reject-reason">
                                  사유: {itemResult.reason || "데이터가 누락되었거나 양이 부족합니다."}
                                </p>
                              </div>
                          )
                      ) : (
                          <span className="badge badge-idle">대기</span>
                      )}
                    </div>
                  </div>
              );
            })}
          </div>

          {/* 검증 실패 메시지 안내 */}
          {analysisResult && !analysisResult.overallPassed && (
              <p className="project-create-error">
                ⚠️ 누락되거나 데이터가 부족한 항목(REJECT)이 있습니다. 내용을 보완하여 다시 검증을 진행해 주세요.
              </p>
          )}

          {/* 하단 버튼 영역 (전부 PASS일 때만 버튼 활성화) */}
          <div className="project-create-actions">
            <button type="button" className="project-create-cancel-button">
              취소
            </button>
            <button
                type="button"
                className="project-create-next-button"
                aria-disabled={!analysisResult || !analysisResult.overallPassed || isAnalyzing}
                disabled={!analysisResult || !analysisResult.overallPassed || isAnalyzing}
                onClick={handleNextStep}
            >
              법률 · 규제 검토 단계로 이동
            </button>
          </div>

        </div>
      </div>
  );
}