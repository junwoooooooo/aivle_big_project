import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';

import {
  BUSINESS_PLAN_ACCEPT,
  formatFileSize,
  validateBusinessPlanFile,
} from '../document/filePolicy.js';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import {
  AppIcon,
  Alert,
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  Progress,
  StatusBadge,
} from '../../shared/ui/index.js';
import { useDocuments, useDocumentUpload } from './hooks/useDocuments.js';
import { useJobRecovery } from './hooks/useJobRecovery.js';
import { formatDocumentDate } from './model/documentViewModel.js';
import { StructuredPlanCompletion } from '../structured-plan/StructuredPlanCompletion.jsx';
import { projectRoutes } from '../projects/routing/projectRoutes.js';
import { ResourceDownload } from '../projects/BusinessPlanResources.jsx';
import { BUSINESS_PLAN_RESOURCES } from '../projects/businessPlanResources.js';
import { useServicePolicy } from '../service-policy/useServicePolicy.js';
import { getWriteRestriction } from '../service-policy/servicePolicyRestrictions.js';
import './documents.css';

/* Replaced by ModernUploadExperience. Kept only temporarily while reviewing the
   pre-existing implementation; it is intentionally excluded from the bundle. */
/*
function UploadForm({ projectId, newVersion = false }) {
  const navigate = useNavigate();
  const [validationError, setValidationError] = useState('');
  const [dragging, setDragging] = useState(false);
  const errorRef = useRef(null);
  const onSuccess = useCallback(() => {
    navigate(projectRoutes.structure(projectId));
  }, [navigate, projectId]);
  const { file, setFile, upload, uploading, error } =
    useDocumentUpload(projectId, onSuccess);

  const chooseFile = (nextFile) => {
    const nextError = validateBusinessPlanFile(nextFile);
    setValidationError(nextError);
    setFile(nextError ? null : nextFile);
    if (nextError) queueMicrotask(() => errorRef.current?.focus());
  };

  const onFiles = (files) => {
    if (files.length !== 1) {
      setValidationError('한 번에 DOCX 파일 하나만 선택해 주세요.');
      setFile(null);
      queueMicrotask(() => errorRef.current?.focus());
      return;
    }
    chooseFile(files[0]);
  };

  const submit = async (event) => {
    event.preventDefault();
    if (!file) {
      setValidationError('DOCX 파일을 선택해 주세요.');
      queueMicrotask(() => errorRef.current?.focus());
      return;
    }
    await upload();
  };

  return (
    <section className={`document-upload ${dragging ? 'is-dragging' : ''}`}>
      <h2>{newVersion ? '새 버전 업로드' : '사업계획서 업로드'}</h2>
      <p>DOCX 형식, 최대 20MB까지 업로드할 수 있습니다.</p>
      <form onSubmit={submit}>
        <div
          className="document-drop-zone"
          onDragEnter={(event) => { event.preventDefault(); setDragging(true); }}
          onDragOver={(event) => event.preventDefault()}
          onDragLeave={(event) => {
            if (!event.currentTarget.contains(event.relatedTarget)) setDragging(false);
          }}
          onDrop={(event) => {
            event.preventDefault();
            setDragging(false);
            onFiles([...event.dataTransfer.files]);
          }}
        >
          <span className="document-drop-zone__icon" aria-hidden="true">⇧</span>
          <FileInput
            label="사업계획서 파일"
            description="파일 선택 버튼은 키보드와 화면 읽기 도구에서도 사용할 수 있습니다."
            accept={BUSINESS_PLAN_ACCEPT}
            onChange={(event) => onFiles([...event.target.files])}
            disabled={uploading}
          />
          <span aria-hidden="true">또는 이 영역에 DOCX 파일을 놓으세요.</span>
        </div>
        {file && (
          <div className="document-file-summary">
            <div>
              <strong title={file.name}>{file.name}</strong>
              <span>{formatFileSize(file.size)}</span>
            </div>
            <Button type="button" variant="ghost" onClick={() => setFile(null)} disabled={uploading}>
              제거
            </Button>
          </div>
        )}
        {validationError && (
          <div ref={errorRef} className="document-form-error" role="alert" tabIndex="-1">
            {validationError}
          </div>
        )}
        {error && (
          <Alert title="업로드하지 못했습니다" tone="danger">
            <p>{getUserErrorMessage(error)}</p>
            {error.retryable && <p>같은 파일로 다시 시도하면 중복 생성 없이 요청을 재사용합니다.</p>}
          </Alert>
        )}
        {uploading && (
          <Alert title="파일을 업로드하고 있습니다">
            실제 전송률은 제공되지 않습니다. 완료될 때까지 창을 닫지 마세요.
          </Alert>
        )}
        <Button type="submit" loading={uploading} disabled={!file || uploading}>
          {newVersion ? '새 버전 분석 시작' : '업로드하고 분석 시작'}
        </Button>
      </form>
    </section>
  );
}

*/
function ModernUploadExperience({ projectId, newVersion = false }) {
  const navigate = useNavigate();
  const servicePolicy = useServicePolicy();
  const restriction = getWriteRestriction({
    ...servicePolicy,
    documentProcessing: true,
  });
  const inputId = `business-plan-file-${projectId}`;
  const [fileError, setFileError] = useState('');
  const [dragging, setDragging] = useState(false);
  const { file, setFile, upload, uploading, error } = useDocumentUpload(projectId, () => navigate(projectRoutes.structure(projectId)));

  const selectFile = (candidate) => {
    if (restriction.blocked) return;
    const nextError = validateBusinessPlanFile(candidate);
    setFileError(nextError);
    setFile(nextError ? null : candidate);
  };
  const receiveFiles = (files) => {
    if (restriction.blocked) return;
    if (files.length !== 1) {
      setFileError('한 번에 DOCX 파일 하나만 선택해 주세요.');
      setFile(null);
      return;
    }
    selectFile(files[0]);
  };
  const submit = async (event) => {
    event.preventDefault();
    if (restriction.blocked) return;
    if (!file) {
      setFileError('업로드할 DOCX 파일을 선택해 주세요.');
      return;
    }
    await upload();
  };

  return <section className="document-upload document-upload--modern" aria-labelledby="document-upload-title">
    <div className="document-upload__heading">
      <p>Business plan</p>
      <h2 id="document-upload-title">{newVersion ? '새 버전 업로드' : '사업계획서를 업로드하세요'}</h2>
      <span>DOCX 파일을 업로드하면 프로젝트의 문서 분석을 시작합니다.</span>
    </div>
    <form onSubmit={submit}>
      {restriction.blocked && (
        <Alert
          tone={restriction.code === 'POLICY_UNAVAILABLE' ? 'danger' : 'warning'}
          title="새 문서 작업을 시작할 수 없습니다"
        >
          <p>{restriction.message}</p>
          {restriction.code === 'POLICY_UNAVAILABLE' && (
            <Button type="button" variant="outline" size="small" onClick={() => void servicePolicy.refresh().catch(() => undefined)}>
              다시 시도
            </Button>
          )}
        </Alert>
      )}
      <input id={inputId} aria-label="사업계획서 파일" className="document-file-control" type="file" accept={BUSINESS_PLAN_ACCEPT} onChange={(event) => receiveFiles([...event.target.files])} disabled={uploading || restriction.blocked} />
      {!file ? <div
        className="document-drop-zone document-drop-zone--modern"
        aria-disabled={restriction.blocked}
        onDragEnter={(event) => { event.preventDefault(); if (!restriction.blocked) setDragging(true); }}
        onDragOver={(event) => event.preventDefault()}
        onDragLeave={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setDragging(false); }}
        onDrop={(event) => {
          event.preventDefault();
          setDragging(false);
          if (!restriction.blocked) receiveFiles([...event.dataTransfer.files]);
        }}
        data-dragging={dragging || undefined}
      >
        <span className="document-drop-zone__icon"><AppIcon name="upload" size={24} /></span>
        <strong>사업계획서 DOCX를 놓아주세요</strong>
        <span>또는 아래 버튼으로 파일을 선택해 주세요.</span>
        <label className={`ui-button ui-button--primary ${restriction.blocked ? 'is-disabled' : ''}`} aria-disabled={restriction.blocked} htmlFor={restriction.blocked ? undefined : inputId}><AppIcon name="upload" />파일 선택</label>
        <small>DOCX · 최대 20MB · 1개</small>
      </div> : <div className="document-selected-file">
        <span className="document-selected-file__icon"><AppIcon name="file" size={22} /></span>
        <div><strong title={file.name}>{file.name}</strong><span>{formatFileSize(file.size)} · 업로드 준비 완료</span></div>
        <Button type="button" variant="outline" onClick={() => { setFile(null); setFileError(''); }} disabled={uploading}>다시 선택</Button>
        <Button type="button" variant="ghost" onClick={() => { setFile(null); setFileError(''); }} disabled={uploading}>제거</Button>
      </div>}
      {fileError && <p className="document-form-error" role="alert">{fileError}</p>}
      {error && <Alert title="업로드하지 못했습니다" tone="danger"><p>{getUserErrorMessage(error)}</p></Alert>}
      {uploading && <Alert title="파일을 업로드하고 있습니다"><p>파일 전송이 끝날 때까지 이 화면을 닫지 말아 주세요.</p></Alert>}
      <Button type="submit" loading={uploading} disabled={!file || uploading || restriction.blocked}><AppIcon name="upload" />{newVersion ? '업로드하고 분석 시작' : '업로드하고 분석 시작'}</Button>
    </form>
  </section>;
}

function DocumentList({ projectId, documentState }) {
  const { status, documents, versions, retry } = documentState;
  if (status === 'loading') return <LoadingState label="문서 목록을 불러오는 중입니다" />;
  if (status === 'error') {
    return <ErrorState title="문서 목록을 불러오지 못했습니다" onRetry={retry} />;
  }
  if (documents.length === 0) {
    return (
      <EmptyState
        title="등록된 사업계획서가 없습니다"
        description="첫 DOCX 파일을 선택해 분석을 시작하세요."
      />
    );
  }
  return (
    <section className="document-list" aria-labelledby="document-list-title">
      <h2 id="document-list-title">등록된 문서와 최신 버전</h2>
      {documents.map((document) => {
        const version = versions[document.documentId];
        return (
          <Card key={document.documentId} className="document-card">
            <div className="document-card__heading">
              <div>
                <strong>사업계획서</strong>
                <span>버전 {document.currentVersion}</span>
              </div>
              <StatusBadge status={version?.parseStatus ?? document.status} />
            </div>
            {version && (
              <dl>
                <div><dt>파일명</dt><dd title={version.originalFileName}>{version.originalFileName}</dd></div>
                <div><dt>파일 크기</dt><dd>{formatFileSize(version.sizeBytes)}</dd></div>
                <div><dt>최근 업로드</dt><dd>{formatDocumentDate(version.uploadedAt)}</dd></div>
              </dl>
            )}
            <Link to={projectRoutes.structure(projectId)}>구조화 결과 확인</Link>
          </Card>
        );
      })}
    </section>
  );
}

export function DocumentUploadPage() {
  const { projectId } = useParams();
  const documentState = useDocuments(projectId);
  return (
    <div className="document-page">
      <PageHeader
        title="사업계획서 문서"
        description="DOCX 원본과 분석 버전을 프로젝트 단위로 관리합니다."
      />
      <div className="document-upload-layout">
        <div>
          <ModernUploadExperience projectId={projectId} newVersion={documentState.documents.length > 0} />
          <DocumentList projectId={projectId} documentState={documentState} />
        </div>
        <aside className="document-resources" aria-labelledby="document-resources-title">
          <p>Resources</p>
          <h2 id="document-resources-title">사업계획서가 준비되지 않았나요?</h2>
          <span>가이드와 완성 예시를 참고해 DOCX 파일을 준비해 주세요.</span>
          {BUSINESS_PLAN_RESOURCES.map((resource) => <ResourceDownload key={resource.id} resource={resource} />)}
          <small>업로드 전 개인정보와 민감한 정보가 포함되어 있는지 확인해 주세요.</small>
        </aside>
      </div>
    </div>
  );
}

function JobState({ job, retry }) {
  if (!job) return null;
  const active = job.status === 'QUEUED' || job.status === 'RUNNING';
  return (
    <Card className="job-state" aria-live="polite">
      <div>
        <StatusBadge status={job.status} />
        <h2>{job.label}</h2>
        <p>{job.description}</p>
      </div>
      {job.status === 'RUNNING' && <Progress value={job.progress} label="문서 분석 진행률" />}
      {job.status === 'QUEUED' && <p role="status">분석 대기열을 확인하고 있습니다.</p>}
      {job.nextAttemptAt && job.retryable && (
        <p>서버가 {formatDocumentDate(job.nextAttemptAt)} 이후 자동으로 다시 시도합니다.</p>
      )}
      {!active && (job.status === 'FAILED' || job.status === 'CANCELED') && (
        <div className="document-actions">
          <Button variant="outline" onClick={retry}>상태 새로고침</Button>
          <Link className="primary-link" to="../documents">새 버전 업로드</Link>
        </div>
      )}
    </Card>
  );
}

function PlanResults({ plan }) {
  const [filter, setFilter] = useState('all');
  const filtered = plan.sections.filter((section) => (
    filter === 'all' || section.statusView.group === filter
  ));
  return (
    <div className="structured-results">
      {plan.isMock && (
        <Alert title="데모 분석 결과">
          개발 환경의 Mock AI가 생성한 결과입니다. 실제 AI 분석 결과로 오인하지 마세요.
        </Alert>
      )}
      <Card className="plan-summary">
        <div>
          <span>완성도</span>
          <strong>{plan.completionRate}%</strong>
        </div>
        <Progress value={plan.completionRate} label="사업계획서 완성도" />
        <p>보완이 필요한 필수 항목 {plan.missingFields.filter((field) => field.status === 'OPEN').length}개</p>
      </Card>
      <div className="result-filter" role="group" aria-label="분석 결과 상태 필터">
        {[
          ['all', '전체'],
          ['complete', '충족'],
          ['needs-input', '보완 필요'],
          ['review', '확인 필요'],
        ].map(([value, label]) => (
          <Button
            key={value}
            type="button"
            variant={filter === value ? 'primary' : 'outline'}
            aria-pressed={filter === value}
            onClick={() => setFilter(value)}
          >
            {label}
          </Button>
        ))}
      </div>
      <section className="section-results" aria-labelledby="section-results-title">
        <h2 id="section-results-title">12개 사업계획 항목</h2>
        {filtered.map((section) => (
          <details className="section-result" key={section.sectionCode}>
            <summary>
              <span>{section.sequence}. {section.displayName}</span>
              <Badge tone={section.status === 'PRESENT' ? 'success' : section.status === 'INVALID' ? 'danger' : 'warning'}>
                {section.statusView.shortLabel} · {section.statusView.label}
              </Badge>
            </summary>
            <div className="section-result__content">
              <div><h3>추출 내용</h3><p>{section.extractedContent || '추출된 내용이 없습니다.'}</p></div>
              <div><h3>판정 이유</h3><p>{section.reason || '제공된 이유가 없습니다.'}</p></div>
              {section.confidence != null && <p>분석 신뢰도: {Math.round(Number(section.confidence) * 100)}%</p>}
              {section.evidence.length > 0 && (
                <div>
                  <h3>근거</h3>
                  <ul>{section.evidence.map((evidence, index) => <li key={`${section.sectionCode}-${index}`}>{evidence}</li>)}</ul>
                </div>
              )}
              {section.sourceBlockReferences.length > 0 && (
                <p className="source-reference">
                  문서 근거 위치 {section.sourceBlockReferences.map((value) => `#${value}`).join(', ')}
                </p>
              )}
            </div>
          </details>
        ))}
      </section>
    </div>
  );
}

export function StructuredPlanPage() {
  const { projectId } = useParams();
  const { status, job, plan, error, retry } = useJobRecovery(projectId);
  const [planOverride, setPlanOverride] = useState(null);
  const overrideMatches = planOverride
    && plan
    && String(planOverride.planId) === String(plan.planId)
    && String(planOverride.sourceDocumentVersionId) === String(plan.sourceDocumentVersionId)
    && Number(planOverride.lockVersion) >= Number(plan.lockVersion);
  const currentPlan = overrideMatches ? planOverride : plan;

  const sourceIsLatest = !(
    job?.sourceDocumentVersionId != null
    && currentPlan?.sourceDocumentVersionId != null
    && String(job.sourceDocumentVersionId) !== String(currentPlan.sourceDocumentVersionId)
  );

  return (
    <div className="document-page">
      <PageHeader
        title="구조화된 사업계획"
        description="서버가 복구한 최신 분석 작업과 12개 표준 항목을 확인합니다."
      />
      {status === 'loading' && <LoadingState label="최신 분석 상태를 복구하는 중입니다" />}
      {status === 'empty' && (
        <EmptyState
          title="분석할 문서가 없습니다"
          description="DOCX 사업계획서를 먼저 등록해 주세요."
          action={<Link className="primary-link" to="../documents">문서 업로드</Link>}
        />
      )}
      {status === 'error' && (
        <ErrorState
          title="분석 상태를 복구하지 못했습니다"
          description={getUserErrorMessage(error)}
          onRetry={retry}
        />
      )}
      {(status === 'processing' || status === 'terminal') && <JobState job={job} retry={retry} />}
      {status === 'waiting' && (
        <Card className="job-state job-state--waiting" aria-live="polite">
          <div><StatusBadge status="QUEUED" /><h2>분석 준비 상태</h2><p>문서 업로드는 완료되었습니다. 구조화 결과가 준비되면 이 화면에서 확인할 수 있습니다.</p></div>
          <p>현재는 자동 상태 확인을 멈췄습니다. 잠시 후 직접 새로고침하거나 업로드 문서를 확인해 주세요.</p>
          <div className="document-actions"><Button variant="outline" onClick={retry}>상태 새로고침</Button><Link className="primary-link" to={projectRoutes.documents(projectId)}>업로드 문서 보기</Link><Link to={projectRoutes.overview(projectId)}>프로젝트 Overview</Link></div>
        </Card>
      )}
      {currentPlan && (
        <>
          <PlanResults plan={currentPlan} />
          <StructuredPlanCompletion
            key={`${currentPlan.planId}:${currentPlan.sourceDocumentVersionId}`}
            projectId={projectId}
            plan={currentPlan}
            onPlanChange={setPlanOverride}
            sourceIsLatest={sourceIsLatest}
          />
        </>
      )}
    </div>
  );
}
