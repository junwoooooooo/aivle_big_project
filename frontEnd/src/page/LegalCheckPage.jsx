import {
  useEffect,
  useState,
} from 'react';

import {
  useLocation,
  useNavigate,
} from 'react-router-dom';

import './LegalCheckPage.css';

const ANALYSIS_STATUS = Object.freeze({
  LOADING: 'loading',
  SUCCESS: 'success',
  FAILURE: 'failure',
});

function LegalCheckPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const fileName = location.state?.fileName;
  const fileMetadata = location.state?.fileMetadata;
  const hasUploadInfo =
    typeof fileName === 'string' && fileName.trim().length > 0;

  const [analysisStatus, setAnalysisStatus] = useState(
    hasUploadInfo
      ? ANALYSIS_STATUS.LOADING
      : ANALYSIS_STATUS.FAILURE,
  );
  const [legalResult, setLegalResult] = useState(null);

  useEffect(() => {
    if (!hasUploadInfo) {
      return undefined;
    }

    const timerId = window.setTimeout(() => {
      setLegalResult({
        status: 'PASS',
        reason:
          '개인정보 보호법 및 전자상거래법 관련 규제 항목을 정상 준수하고 있습니다. 추가적인 법적 인허가 이슈는 발견되지 않았습니다.',
      });
      setAnalysisStatus(ANALYSIS_STATUS.SUCCESS);
    }, 1500);

    return () => {
      window.clearTimeout(timerId);
    };
  }, [fileName, hasUploadInfo]);

  const handleGoToBusinessAnalysis = () => {
    if (!legalResult) {
      return;
    }

    navigate('/business-analysis', {
      state: {
        fileName,
        fileMetadata,
        legalResult,
      },
    });
  };

  if (!hasUploadInfo) {
    return (
      <section className="legal-check-page legal-check-empty">
        <div className="legal-check-content">
          <span className="legal-check-chip">법률/규제 검토</span>
          <h1>업로드한 기획서 정보가 없습니다</h1>
          <p>
            법률·규제 검토를 시작하려면 먼저 사업기획서 파일을 선택해 주세요.
            직접 주소로 접근한 경우에는 임의의 분석을 실행하지 않습니다.
          </p>
          <button type="button" onClick={() => navigate('/project-create')}>
            기획서 등록으로 이동
          </button>
        </div>
      </section>
    );
  }

  const isLoading = analysisStatus === ANALYSIS_STATUS.LOADING;
  const isPass = legalResult?.status === 'PASS';

  return (
    <section className="legal-check-page">
      <div className="legal-check-content">
        <header className="legal-check-header">
          <span className="legal-check-chip">법률/규제 검토</span>
          <h1>법률/규제 검토 결과</h1>
          <p title={fileName}>
            분석 파일: <strong>{fileName}</strong>
          </p>
        </header>

        <div className="legal-check-result-card" aria-live="polite">
          {isLoading ? (
            <div className="legal-check-loading">
              <div className="legal-check-spinner" aria-hidden="true" />
              <strong>AI 법률 검토 에이전트가 분석하고 있습니다</strong>
              <p>기획서의 법률·인허가 및 주요 규제 항목을 확인 중입니다.</p>
            </div>
          ) : (
            <div className="legal-check-result">
              <span
                className={
                  isPass
                    ? 'legal-check-status pass'
                    : 'legal-check-status fail'
                }
              >
                {isPass ? 'PASS · 통과' : 'FAIL · 불통과'}
              </span>
              <p>
                <strong>검토 사유</strong>
                {legalResult?.reason ||
                  '검토 결과를 불러오지 못했습니다. 기획서를 다시 등록해 주세요.'}
              </p>
            </div>
          )}
        </div>

        <div className="legal-check-actions">
          <button
            type="button"
            className="legal-check-secondary-button"
            onClick={() => navigate('/project-create')}
          >
            기획서 다시 선택
          </button>
          <button
            type="button"
            className="legal-check-primary-button"
            onClick={handleGoToBusinessAnalysis}
            disabled={isLoading || !legalResult}
          >
            사업성 분석 화면으로
          </button>
        </div>
      </div>
    </section>
  );
}

export default LegalCheckPage;
