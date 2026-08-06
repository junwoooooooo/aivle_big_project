import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';

import { Alert, Button, Dialog, ErrorState, LoadingState, PageHeader } from '../../../shared/ui/index.js';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { useServicePolicy } from '../../service-policy/useServicePolicy.js';
import MarketingCanvas from '../components/MarketingCanvas.jsx';
import MarketingCopyEditor from '../components/MarketingCopyEditor.jsx';
import MarketingExportDialog from '../components/MarketingExportDialog.jsx';
import MarketingSetupPanel from '../components/MarketingSetupPanel.jsx';
import MarketingSourceSummary from '../components/MarketingSourceSummary.jsx';
import MarketingSourceRefreshDialog from '../components/MarketingSourceRefreshDialog.jsx';
import MarketingStylePanel from '../components/MarketingStylePanel.jsx';
import MarketingVersionPanel from '../components/MarketingVersionPanel.jsx';
import useMarketingAutosave from '../hooks/useMarketingAutosave.js';
import useMarketingContent from '../hooks/useMarketingContent.js';
import useMarketingGeneration from '../hooks/useMarketingGeneration.js';
import { exportMarketingPng, marketingOverflowWarnings } from '../render/marketingRenderer.js';
import '../marketing.css';

function editableFrom(data) {
  if (!data) return null;
  return {
    title: data.content.title,
    purpose: data.content.purpose,
    channel: data.content.channel,
    format: data.content.format,
    width: data.content.width,
    height: data.content.height,
    personaId: null,
    entityVersion: data.entityVersion,
    draft: { ...data.current },
  };
}

function draftFromVersion(version) {
  const {
    headline,
    subheadline,
    bodyCopy,
    callToAction,
    supportingText,
    visualStyle,
    colorTheme,
    layoutTemplate,
    backgroundType,
    backgroundValue,
    accentColor,
    textColor,
    textAlignment,
    headlineSize,
    showCta,
    showPersonaTag,
    contentJson,
  } = version;
  return {
    headline,
    subheadline,
    bodyCopy,
    callToAction,
    supportingText,
    visualStyle,
    colorTheme,
    layoutTemplate,
    backgroundType,
    backgroundValue,
    accentColor,
    textColor,
    textAlignment,
    headlineSize,
    showCta,
    showPersonaTag,
    contentJson,
  };
}

export default function MarketingContentWorkspacePage() {
  const { projectId, contentId } = useParams();
  const navigate = useNavigate();
  const hook = useMarketingContent(projectId, contentId);
  const { policy } = useServicePolicy();
  const [editable, setEditable] = useState(null);
  const [dirty, setDirty] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);
  const [alternativeOpen, setAlternativeOpen] = useState(false);
  const [exportOpen, setExportOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [versionPreview, setVersionPreview] = useState(null);
  const [sourceOpen, setSourceOpen] = useState(false);
  const [working, setWorking] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [exportChecking, setExportChecking] = useState(false);
  const [exportWarnings, setExportWarnings] = useState([]);
  const [alternativeIndex, setAlternativeIndex] = useState(0);
  const editRevision = useRef(0);
  const blocked = policy.maintenanceMode;
  const generation = useMarketingGeneration({
    api: hook.api,
    projectId,
    contentId,
    onSucceeded: hook.refresh,
  });

  const activeEditable = editable ?? editableFrom(hook.data);

  useEffect(() => {
    const warn = (event) => {
      if (!dirty) return;
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  const savePayload = useCallback(async (value) => {
    if (!value || blocked) return null;
    const submittedRevision = editRevision.current;
    try {
      const saved = await hook.api.update(projectId, contentId, value);
      hook.setData(saved);
      if (submittedRevision === editRevision.current) {
        setEditable(editableFrom(saved));
        setDirty(false);
      } else {
        setEditable((current) => ({
          ...(current ?? value),
          entityVersion: saved.entityVersion,
        }));
      }
      setErrorMessage(null);
      return saved;
    } catch (error) {
      setErrorMessage(getUserErrorMessage(error));
      throw error;
    }
  }, [blocked, contentId, hook, projectId]);

  const autosave = useMarketingAutosave({
    value: activeEditable,
    dirty,
    onSave: savePayload,
  });

  const preview = useMemo(() => hook.data && activeEditable ? {
    ...hook.data,
    content: { ...hook.data.content, ...activeEditable },
    current: activeEditable.draft,
  } : hook.data, [activeEditable, hook.data]);

  function updateMetadata(value) {
    editRevision.current += 1;
    setEditable((current) => {
      const base = current ?? editableFrom(hook.data);
      return { ...base, ...value, draft: base.draft };
    });
    setDirty(true);
  }

  function updateDraft(draft) {
    editRevision.current += 1;
    setEditable((current) => ({ ...(current ?? editableFrom(hook.data)), draft }));
    setDirty(true);
  }

  async function replaceDraft() {
    setWorking(true);
    try {
      const response = await hook.api.alternateDraft(projectId, contentId, alternativeIndex);
      updateDraft(response.draft);
      setAlternativeIndex((current) => (current + 1) % 3);
      setAlternativeOpen(false);
    } catch (error) {
      setErrorMessage(getUserErrorMessage(error));
    } finally {
      setWorking(false);
    }
  }

  async function createVersion() {
    if (blocked || working) return;
    setWorking(true);
    try {
      await autosave.save();
      await hook.api.createVersion(projectId, contentId, activeEditable.draft);
      await hook.refresh();
      setEditable(null);
      setDirty(false);
    } catch (error) {
      setErrorMessage(getUserErrorMessage(error));
    } finally {
      setWorking(false);
    }
  }

  async function remove() {
    if (blocked) return;
    try {
      await hook.api.remove(projectId, contentId);
      navigate(`/app/projects/${projectId}/validate/marketing`, { replace: true });
    } catch (error) {
      setErrorMessage(getUserErrorMessage(error));
    }
  }

  async function exportPng() {
    setExporting(true);
    try {
      await exportMarketingPng(preview, editable.title);
      setExportOpen(false);
    } catch (error) {
      setErrorMessage(
        error?.code === 'MARKETING_EXPORT_OVERFLOW'
          ? getUserErrorMessage(error)
          : 'PNG를 생성하지 못했습니다. 폰트 로딩과 브라우저 저장 권한을 확인해 주세요.',
      );
    } finally {
      setExporting(false);
    }
  }

  async function openExport() {
    setExportOpen(true);
    setExportChecking(true);
    try {
      setExportWarnings(await marketingOverflowWarnings(preview));
    } finally {
      setExportChecking(false);
    }
  }

  async function refreshSource(payload) {
    if (blocked || working) return;
    setWorking(true);
    setErrorMessage(null);
    try {
      if (dirty) await autosave.save();
      const response = await hook.api.refreshSource(projectId, contentId, payload);
      hook.setData(response);
      setEditable(editableFrom(response));
      setDirty(false);
      await hook.refresh();
      setSourceOpen(false);
    } catch (error) {
      setErrorMessage(getUserErrorMessage(error));
    } finally {
      setWorking(false);
    }
  }

  if (hook.loading && !hook.data) return <LoadingState label="마케팅 워크스페이스를 준비하고 있습니다" />;
  if (hook.error && !hook.data) {
    return <ErrorState description={getUserErrorMessage(hook.error)} onRetry={hook.refresh} />;
  }
  if (!activeEditable) return null;

  return (
    <div className="marketing-page marketing-workspace">
      <PageHeader
        eyebrow="Marketing Workspace"
        title={activeEditable.title}
        description="카피와 디자인을 편집하고 실제 규격 PNG로 내보냅니다."
        actions={(
          <>
            <span className={`marketing-save-state marketing-save-state--${autosave.status}`} role="status">
              {autosave.status === 'saving' ? '저장 중' : autosave.status === 'error' ? '저장 실패' : dirty ? '저장 대기' : '저장됨'}
            </span>
            <Button variant="outline" disabled={blocked} onClick={() => void autosave.save()}>
              {autosave.status === 'error' ? '저장 재시도' : '저장'}
            </Button>
            <Button onClick={() => void openExport()}>내보내기</Button>
          </>
        )}
      />
      <nav className="marketing-steps" aria-label="콘텐츠 제작 단계">
        {['제작 목적', '검증 결과', '카피', '디자인', '검토·내보내기'].map((step, index) => <span key={step}><b>{index + 1}</b>{step}</span>)}
      </nav>
      {blocked && <Alert tone="warning" title="현재 서비스 점검 중입니다">편집과 새 버전 저장은 잠시 중지되지만 기존 시안과 PNG 내보내기는 이용할 수 있습니다.</Alert>}
      {errorMessage && <Alert tone="danger">{errorMessage}</Alert>}
      {generation.status !== 'IDLE' && (
        <Alert
          tone={generation.status === 'FAILED' ? 'danger' : 'info'}
          title={`AI banner: ${generation.status}`}
        >
          {generation.error
            ? getUserErrorMessage(generation.error)
            : generation.status === 'SUCCEEDED'
              ? '새 마케팅 버전과 결과 이미지가 저장되었습니다.'
              : 'AI 작업 상태를 확인하고 있습니다.'}
        </Alert>
      )}
      <div className="marketing-generation">
        <label>
          AI 배너 원본 이미지
          <input
            type="file"
            accept=".png,.jpg,.jpeg,.webp,image/png,image/jpeg,image/webp"
            disabled={blocked || generation.active}
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) {
                void generation.generate(file, hook.data.current.id);
              }
              event.target.value = '';
            }}
          />
        </label>
        <span>Mock provider가 원본을 결과 artifact로 복사합니다.</span>
      </div>
      {activeEditable.draft.headline.length > 70 && (
        <Alert tone="warning" title="Headline 길이를 확인해 주세요">
          긴 제목은 규격과 템플릿에 따라 최대 4줄 뒤에서 줄임 표시될 수 있습니다. 내보내기 전 미리보기를 확인해 주세요.
        </Alert>
      )}
      <div className="marketing-workspace__grid" aria-busy={autosave.status === 'saving'}>
        <aside>
          <MarketingSetupPanel value={activeEditable} onChange={updateMetadata} mode="edit" />
          <MarketingSourceSummary
            sourceSnapshotJson={hook.data.sourceSnapshotJson}
            legalNotice={hook.data.legalNotice}
            copyEvidence={hook.data.copyEvidence}
            onRefresh={() => setSourceOpen(true)}
            refreshDisabled={blocked}
          />
        </aside>
        <main>
          <MarketingCanvas content={preview} />
          <MarketingVersionPanel
            versions={hook.versions}
            onCreate={createVersion}
            onPreview={setVersionPreview}
            onClone={(version) => {
              updateDraft(draftFromVersion(version));
              setVersionPreview(null);
            }}
            saving={working}
            disabled={blocked}
          />
        </main>
        <aside>
          <MarketingCopyEditor draft={activeEditable.draft} onChange={updateDraft} onAlternative={() => setAlternativeOpen(true)} generating={working} />
          <MarketingStylePanel
            draft={activeEditable.draft}
            onChange={updateDraft}
            recommendedPresetIds={hook.data.recommendedPresets}
          />
        </aside>
      </div>
      <footer className="marketing-workspace__footer">
        <Link to={`/app/projects/${projectId}/validate/marketing`}>목록으로</Link>
        <Button variant="danger" disabled={blocked} onClick={() => setDeleteOpen(true)}>콘텐츠 삭제</Button>
      </footer>
      <Dialog open={alternativeOpen} onClose={() => setAlternativeOpen(false)} title="다른 카피 초안으로 바꿀까요?">
        <p>현재 편집 중인 카피가 대체됩니다. 자동 저장 전에 취소하면 이전 저장본을 다시 불러올 수 있습니다.</p>
        <div className="marketing-dialog-actions">
          <Button variant="ghost" onClick={() => setAlternativeOpen(false)}>취소</Button>
          <Button loading={working} disabled={blocked} onClick={replaceDraft}>초안 바꾸기</Button>
        </div>
      </Dialog>
      <MarketingExportDialog
        open={exportOpen}
        content={preview}
        filename={activeEditable.title}
        warnings={exportWarnings}
        checking={exportChecking}
        exporting={exporting}
        onClose={() => setExportOpen(false)}
        onExport={exportPng}
      />
      {sourceOpen && <MarketingSourceRefreshDialog
        open={sourceOpen}
        projectId={projectId}
        current={{
          panelInterviewId: hook.data.content.panelInterviewId,
          marketResponseId: hook.data.content.marketResponseId,
          capturedAt: (() => {
            try { return JSON.parse(hook.data.sourceSnapshotJson).capturedAt; } catch { return null; }
          })(),
        }}
        loading={working}
        onClose={() => setSourceOpen(false)}
        onSubmit={refreshSource}
      />}
      <Dialog open={deleteOpen} onClose={() => setDeleteOpen(false)} title="콘텐츠를 삭제할까요?">
        <Alert tone="warning">목록에서는 즉시 제외되며 저장된 버전은 운영 기록으로 보존됩니다.</Alert>
        <div className="marketing-dialog-actions">
          <Button variant="ghost" onClick={() => setDeleteOpen(false)}>취소</Button>
          <Button variant="danger" disabled={blocked} onClick={remove}>삭제</Button>
        </div>
      </Dialog>
      <Dialog open={Boolean(versionPreview)} onClose={() => setVersionPreview(null)} title={`버전 ${versionPreview?.versionNumber ?? ''}`}>
        {versionPreview && (
          <>
            <dl className="marketing-version-detail">
              <div><dt>Headline</dt><dd>{versionPreview.headline}</dd></div>
              <div><dt>Subheadline</dt><dd>{versionPreview.subheadline}</dd></div>
              <div><dt>Template</dt><dd>{versionPreview.layoutTemplate}</dd></div>
              <div><dt>저장 시각</dt><dd>{new Date(versionPreview.createdAt).toLocaleString('ko-KR')}</dd></div>
            </dl>
            <div className="marketing-dialog-actions">
              <Button variant="ghost" onClick={() => setVersionPreview(null)}>닫기</Button>
              <Button onClick={() => {
                updateDraft(draftFromVersion(versionPreview));
                setVersionPreview(null);
              }}>이 버전을 새 편집본으로 복제</Button>
            </div>
          </>
        )}
      </Dialog>
    </div>
  );
}
