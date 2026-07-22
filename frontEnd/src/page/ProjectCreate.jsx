import {
  useRef,
  useState,
} from 'react';

import {
  useNavigate,
} from 'react-router-dom';

import './ProjectCreate.css';

const MAX_FILE_SIZE = 20 * 1024 * 1024;

const ALLOWED_EXTENSIONS = [
  '.docx',
  '.doc',
  '.pdf',
  '.hwp',
];

const ALLOWED_MIME_TYPES = new Set([
  'application/pdf',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/x-hwp',
  'application/haansofthwp',
  'application/vnd.hancom.hwp',
]);

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

const formatFileSize = (size) => {
  if (size < 1024 * 1024) {
    return `${Math.ceil(size / 1024)}KB`;
  }

  return `${(size / (1024 * 1024)).toFixed(1)}MB`;
};

const validateFile = (file) => {
  const fileName = file.name.toLowerCase();
  const extension = ALLOWED_EXTENSIONS.find((item) =>
    fileName.endsWith(item),
  );

  if (!extension) {
    return 'DOCX, DOC, PDF, HWP 파일만 선택할 수 있습니다.';
  }

  if (
    file.type &&
    !ALLOWED_MIME_TYPES.has(file.type)
  ) {
    return '파일 형식이 확장자와 일치하지 않거나 지원되지 않습니다.';
  }

  if (file.size > MAX_FILE_SIZE) {
    return '파일 크기는 최대 20MB까지 허용됩니다.';
  }

  return '';
};

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

    const validationMessage = validateFile(file);

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
      setErrorMessage('사업기획서 파일을 먼저 선택해 주세요.');
      return;
    }

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
          <span className="project-create-chip">기획서 등록</span>
          <h1>사업기획서를 업로드해 주세요</h1>
          <p>
            아래 항목을 포함한 문서를 선택하면 법률·규제 검토 단계로
            이동합니다. 파일은 아직 서버에 저장되지 않습니다.
          </p>
        </header>

        <a
          className="project-create-guide-download"
          href="/business_plan_guideline.docx"
          download="사업기획서_작성_가이드라인.docx"
        >
          가이드라인 파일 다운로드 <span aria-hidden="true">↓</span>
        </a>

        <div className="project-create-guideline" aria-label="사업기획서 필수 항목">
          {GUIDELINE_ITEMS.map((item, index) => (
            <p key={item}>
              <span>{index + 1}.</span> {item}
            </p>
          ))}
        </div>

        <div className="project-create-upload-area">
          <input
            ref={fileInputRef}
            type="file"
            accept=".docx,.doc,.pdf,.hwp"
            hidden
            onChange={handleFileChange}
          />

          {selectedFile ? (
            <div className="project-create-file-card">
              <span className="project-create-file-icon" aria-hidden="true">📄</span>
              <div>
                <strong title={selectedFile.name}>{selectedFile.name}</strong>
                <span>{formatFileSize(selectedFile.size)}</span>
              </div>
              <button type="button" onClick={clearSelection}>
                선택 취소
              </button>
              <button type="button" onClick={openFilePicker}>
                다시 선택
              </button>
            </div>
          ) : (
            <button
              type="button"
              className="project-create-upload-button"
              onClick={openFilePicker}
            >
              사업기획서 파일 선택
              <small>DOCX, DOC, PDF, HWP · 최대 20MB</small>
            </button>
          )}

          {errorMessage && (
            <p className="project-create-error" role="alert">
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
            법률/규제 검토로
          </button>
        </div>
      </div>
    </section>
  );
}

export default ProjectCreate;
