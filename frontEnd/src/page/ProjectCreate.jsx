import {
  useRef,
  useState,
} from 'react';

import {
  useNavigate,
} from 'react-router-dom';

import {
  BUSINESS_PLAN_ACCEPT,
  formatFileSize,
  validateBusinessPlanFile,
} from '../features/document/filePolicy.js';

import './ProjectCreate.css';

const GUIDELINE_ITEMS = [
  '사업 개요',
  '시장 규모',
  '타겟 고객',
  '경쟁 분석',
  '제품 · 서비스',
  '비즈니스 모델',
  '원가 · 수익성',
  '판매 목표 · 재무 추정',
  '기술 · 생산',
  '법률 · 인허가',
  '일정 · 리스크',
  '근거 자료 목록',
];

function ProjectCreate() {
  const navigate = useNavigate();
  const fileInputRef = useRef(null);
  const [selectedFile, setSelectedFile] = useState(null);
  const [errorMessage, setErrorMessage] = useState('');

  const openFilePicker = () => {
    fileInputRef.current?.click();
  };

  const clearSelection = () => {
    setSelectedFile(null);
    setErrorMessage('');

    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const handleFileChange = (event) => {
    const file = event.target.files?.[0];

    if (!file) {
      return;
    }

    const validationMessage =
      validateBusinessPlanFile(file);

    if (validationMessage) {
      setSelectedFile(null);
      setErrorMessage(validationMessage);
      event.target.value = '';
      return;
    }

    setSelectedFile(file);
    setErrorMessage('');
  };

  const handleNext = () => {
    if (!selectedFile) {
      setErrorMessage(
        '사업계획서 파일을 먼저 선택해 주세요.',
      );
      return;
    }

    /*
     * Phase 0 keeps the existing screen flow only.
     * Phase 1 replaces this route state with the canonical
     * project-owned document upload and server re-query flow.
     */
    navigate('/legal-check', {
      state: {
        fileName: selectedFile.name,
        fileMetadata: {
          name: selectedFile.name,
          size: selectedFile.size,
          type: selectedFile.type || 'unknown',
          lastModified: selectedFile.lastModified,
        },
      },
    });
  };

  return (
    <section className="project-create-page">
      <div className="project-create-content">
        <header className="project-create-header">
          <span className="project-create-chip">
            사업계획서 등록
          </span>
          <h1>사업계획서를 업로드해 주세요</h1>
          <p>
            아래 항목을 포함한 문서를 선택하면 법률·규제 검토 단계로
            이동합니다. 파일은 아직 서버에 저장되지 않습니다.
          </p>
        </header>

        <a
          className="project-create-guide-download"
          href="/business_plan_guideline.docx"
          download="사업계획서_작성_가이드라인.docx"
        >
          가이드라인 파일 다운로드 <span aria-hidden="true">↓</span>
        </a>

        <div
          className="project-create-guideline"
          aria-label="사업계획서 필수 항목"
        >
          {GUIDELINE_ITEMS.map((item, index) => (
            <div
              key={item}
              className="guideline-item-container"
            >
              <p className="guideline-item-title">
                <span>{index + 1}.</span> {item}
              </p>
              <div className="guideline-status-wrapper">
                <span className="badge badge-idle">
                  확인 전
                </span>
              </div>
            </div>
          ))}
        </div>

        <div className="project-create-upload-area">
          <input
            ref={fileInputRef}
            type="file"
            accept={BUSINESS_PLAN_ACCEPT}
            hidden
            onChange={handleFileChange}
          />

          {selectedFile ? (
            <div className="project-create-file-card">
              <span
                className="project-create-file-icon"
                aria-hidden="true"
              >
                📄
              </span>
              <div>
                <strong title={selectedFile.name}>
                  {selectedFile.name}
                </strong>
                <span>
                  {formatFileSize(selectedFile.size)}
                </span>
              </div>
              <button
                type="button"
                onClick={clearSelection}
              >
                선택 취소
              </button>
              <button
                type="button"
                onClick={openFilePicker}
              >
                다시 선택
              </button>
            </div>
          ) : (
            <button
              type="button"
              className="project-create-upload-button"
              onClick={openFilePicker}
            >
              사업계획서 파일 선택
              <small>
                PDF, DOCX, HWPX · 최대 20MB
              </small>
            </button>
          )}

          {errorMessage && (
            <p
              className="project-create-error"
              role="alert"
            >
              {errorMessage}
            </p>
          )}
        </div>

        <div className="project-create-actions">
          <button
            type="button"
            className="project-create-cancel-button"
            onClick={() => navigate('/dashboard')}
          >
            취소
          </button>
          <button
            type="button"
            className="project-create-next-button"
            onClick={handleNext}
            aria-disabled={!selectedFile}
          >
            법률·규제 검토로
          </button>
        </div>
      </div>
    </section>
  );
}

export default ProjectCreate;
