import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';

import { Alert, Badge, Button, Card, EmptyState, ErrorState, LoadingState, PageHeader } from '../../../shared/ui/index.js';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { useServicePolicy } from '../../service-policy/useServicePolicy.js';
import useAvailablePersonas from '../../personas/hooks/useAvailablePersonas.js';
import MarketingSetupPanel from '../components/MarketingSetupPanel.jsx';
import useMarketingContents from '../hooks/useMarketingContents.js';
import useMarketingValidationSources from '../hooks/useMarketingValidationSources.js';
import { formatDimensions, optionLabel, PURPOSES } from '../model/marketingPresets.js';
import '../marketing.css';

const INITIAL = {
  title: '',
  purpose: 'AWARENESS',
  channel: 'SOCIAL',
  format: 'SQUARE_1080',
  width: 1080,
  height: 1080,
  personaId: '',
  targetOffer: '',
  emphasisMessage: '',
  requiredText: '',
  avoidedText: '',
  brandName: '',
  brandColor: '#0f8878',
  callToAction: '',
  tone: 'TRUSTWORTHY',
  template: 'HERO_CENTER',
};

export default function MarketingContentListPage({ initialCreate = false }) {
  const { projectId } = useParams();
  const { items, loading, error, refresh, api } = useMarketingContents(projectId);
  const { policy } = useServicePolicy();
  const [searchParams] = useSearchParams();
  const panelInterviewId = searchParams.get('panelInterviewId');
  const marketResponseId = searchParams.get('marketResponseId');
  const [creating, setCreating] = useState(initialCreate);
  const [form, setForm] = useState(INITIAL);
  const [submitError, setSubmitError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();
  const personaEnabled = policy.clusterPersonaEnabled;
  const personas = useAvailablePersonas(projectId, personaEnabled);
  const validationSources = useMarketingValidationSources(
    projectId,
    panelInterviewId,
    marketResponseId,
  );
  const blocked = policy.maintenanceMode;
  const sourceLoading = validationSources.loading;
  const sourceError = validationSources.error
    || (validationSources.panel
      && validationSources.panel.interview.status !== 'COMPLETED')
    || (validationSources.market
      && validationSources.market.prediction.status !== 'COMPLETED');

  const personaOptions = useMemo(
    () => personas.data?.items?.filter((item) => !item.disabled) ?? [],
    [personas.data],
  );

  async function create(event) {
    event.preventDefault();
    if (submitting || blocked || sourceLoading || sourceError) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const dimensions = formatDimensions(form.format);
      const created = await api.create(projectId, {
        ...form,
        width: form.format === 'CUSTOM' ? Number(form.width) : dimensions.width,
        height: form.format === 'CUSTOM' ? Number(form.height) : dimensions.height,
        personaId: form.personaId ? Number(form.personaId) : null,
        panelInterviewId: panelInterviewId ? Number(panelInterviewId) : null,
        marketResponseId: marketResponseId ? Number(marketResponseId) : null,
      });
      navigate(`/app/projects/${projectId}/validate/marketing/${created.content.id}`);
    } catch (requestError) {
      setSubmitError(getUserErrorMessage(requestError));
    } finally {
      setSubmitting(false);
    }
  }

  if (loading && items.length === 0) return <LoadingState label="마케팅 콘텐츠를 불러오고 있습니다" />;
  if (error && items.length === 0) {
    return <ErrorState description={getUserErrorMessage(error)} onRetry={refresh} />;
  }

  return (
    <div className="marketing-page">
      <PageHeader
        eyebrow="Persona Validation"
        title="마케팅 콘텐츠 제작"
        description="검증 결과를 바탕으로 편집 가능한 광고 카피와 시안을 만듭니다."
        actions={<Button disabled={blocked} onClick={() => setCreating((current) => !current)}>{creating ? '닫기' : '새 콘텐츠 만들기'}</Button>}
      />
      {blocked && <Alert title="현재 서비스 점검 중입니다" tone="warning">기존 시안 조회와 로컬 PNG 내보내기는 가능하지만 새 생성·수정은 잠시 사용할 수 없습니다.</Alert>}
      {creating && (panelInterviewId || marketResponseId) && (
        <Alert title="검증 결과 반영 예정" tone="info">
          {panelInterviewId && <span>패널 인터뷰 결과 1건을 반영합니다. </span>}
          {marketResponseId && <span>시장 반응 예측 결과 1건을 반영합니다.</span>}
        </Alert>
      )}
      {creating && sourceLoading && <Alert role="status">반영할 검증 결과를 확인하고 있습니다.</Alert>}
      {creating && sourceError && (
        <Alert title="검증 결과를 반영할 수 없습니다" tone="danger">
          완료된 이 프로젝트의 검증 결과인지 확인한 뒤 결과 화면에서 다시 시작해 주세요.
        </Alert>
      )}
      {creating && (
        <Card className="marketing-create">
          <form onSubmit={create}>
            <MarketingSetupPanel value={form} onChange={setForm} />
            {personaOptions.length > 0 && (
              <label className="marketing-native-field">
                <span>대상 Persona</span>
                <select value={form.personaId} onChange={(event) => setForm({ ...form, personaId: event.target.value })}>
                  <option value="">추천 또는 프로젝트 선택 Persona 사용</option>
                  {personaOptions.map((persona) => <option key={persona.id} value={persona.id}>{persona.name}</option>)}
                </select>
              </label>
            )}
            {submitError && <Alert tone="danger">{submitError}</Alert>}
            <div className="marketing-dialog-actions">
              <Button type="button" variant="ghost" onClick={() => setCreating(false)}>취소</Button>
              <Button type="submit" loading={submitting} disabled={blocked || sourceLoading || Boolean(sourceError)}>검증 결과 기반 초안 만들기</Button>
            </div>
          </form>
        </Card>
      )}
      {!creating && items.length === 0 ? (
        <EmptyState
          title="아직 저장된 마케팅 콘텐츠가 없습니다"
          description="콘텐츠 목적과 규격을 정해 첫 시안을 만들어 보세요."
          action={<Button disabled={blocked} onClick={() => setCreating(true)}>새 콘텐츠 만들기</Button>}
        />
      ) : (
        <div className="marketing-content-grid">
          {items.map((item) => (
            <Card key={item.id} className="marketing-content-card">
              <div className={`marketing-content-card__preview marketing-content-card__preview--${item.format.toLowerCase()}`} aria-hidden="true">
                <span>{item.title.slice(0, 1)}</span>
              </div>
              <div>
                <Badge tone={item.status === 'READY' ? 'success' : 'neutral'}>{item.status === 'READY' ? '편집 가능' : item.status}</Badge>
                <h2>{item.title}</h2>
                <p>{optionLabel(PURPOSES, item.purpose)} · {item.width} × {item.height}</p>
                <p>{item.personaName || 'Persona 직접 지정 없음'} · v{item.currentVersion}</p>
                <p>
                  {item.panelInterviewId ? '패널 인터뷰 반영' : ''}
                  {item.panelInterviewId && item.marketResponseId ? ' · ' : ''}
                  {item.marketResponseId ? '시장 반응 반영' : ''}
                  {!item.panelInterviewId && !item.marketResponseId ? '기본 프로젝트 정보만 사용' : ''}
                </p>
                <p>최근 수정 {new Date(item.updatedAt).toLocaleString('ko-KR')}</p>
                <Link className="ui-button ui-button--primary ui-button--small" to={`${item.id}`}>시안 열기</Link>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
