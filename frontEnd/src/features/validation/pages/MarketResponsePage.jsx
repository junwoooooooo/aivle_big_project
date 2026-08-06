import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';

import { Alert, Badge, Button, Card, Dialog, EmptyState, ErrorState, LoadingState, PageHeader, Select, TextInput } from '../../../shared/ui/index.js';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { useServicePolicy } from '../../service-policy/useServicePolicy.js';
import { projectRoutes } from '../../projects/routing/projectRoutes.js';
import MarketResponseResult from '../components/MarketResponseResult.jsx';
import MessageVariantEditor from '../components/MessageVariantEditor.jsx';
import PersonaChoiceCards from '../components/PersonaChoiceCards.jsx';
import useValidationData from '../hooks/useValidationData.js';
import '../validation.css';

const EMPTY = {
  title: '',
  personaIds: [],
  messages: [{ id: 'A', text: '' }],
  priceContext: '',
  primaryChannel: '',
  panelInterviewId: '',
};

function fromDetail(detail) {
  return {
    title: detail.prediction.title,
    personaIds: detail.personaIds,
    messages: detail.messages,
    priceContext: detail.priceContext ?? '',
    primaryChannel: detail.primaryChannel ?? '',
    panelInterviewId: detail.prediction.panelInterviewId ?? '',
  };
}

export default function MarketResponsePage() {
  const { projectId, predictionId } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const data = useValidationData(projectId, 'market', predictionId);
  const interviewData = useValidationData(projectId, 'interview', null);
  const { policy } = useServicePolicy();
  const [form, setForm] = useState(null);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState(false);
  const [working, setWorking] = useState(false);
  const [message, setMessage] = useState(null);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const blocked = policy.maintenanceMode;
  const queryPanelId = searchParams.get('panelInterviewId') ?? '';
  const queryPersonaIds = searchParams.get('personaIds') ?? '';
  const initial = useMemo(() => ({
    ...EMPTY,
    panelInterviewId: queryPanelId,
    personaIds: queryPersonaIds.split(',')
      .map(Number)
      .filter((value) => Number.isInteger(value) && value > 0)
      .slice(0, 3),
  }), [queryPanelId, queryPersonaIds]);
  const activeForm = form ?? (data.detail ? fromDetail(data.detail) : initial);
  const showingEditor = creating || (predictionId && (data.detail?.prediction.status === 'DRAFT' || editing));
  const completedInterviews = interviewData.items.filter((item) => item.status === 'COMPLETED');

  function update(patch) {
    setForm({ ...activeForm, ...patch });
  }

  function validate() {
    if (!activeForm.title.trim()) return '예측 제목을 입력해 주세요.';
    if (activeForm.personaIds.length < 1 || activeForm.personaIds.length > 3) return 'Persona를 1~3개 선택해 주세요.';
    const messages = activeForm.messages.filter((item) => item.text.trim());
    if (messages.length < 1 || messages.length > 3) return '비교 메시지를 1~3개 입력해 주세요.';
    return null;
  }

  async function save(runAfter = false) {
    const validation = validate();
    if (validation) {
      setMessage(validation);
      return;
    }
    setWorking(true);
    setMessage(null);
    const payload = {
      ...activeForm,
      messages: activeForm.messages.filter((item) => item.text.trim()).map((item) => ({ ...item, text: item.text.trim() })),
      panelInterviewId: activeForm.panelInterviewId ? Number(activeForm.panelInterviewId) : null,
    };
    try {
      let detail;
      if (predictionId) {
        detail = await data.api.updateMarketResponse(projectId, predictionId, payload);
      } else {
        detail = await data.api.createMarketResponse(projectId, payload);
      }
      const id = detail.prediction.id;
      if (runAfter) detail = await data.api.runMarketResponse(projectId, id);
      data.setDetail(detail);
      setForm(null);
      setCreating(false);
      setEditing(false);
      if (!predictionId) {
        navigate(projectRoutes.marketResponseDetail(projectId, id), { replace: true });
      } else {
        await data.refresh();
      }
    } catch (error) {
      setMessage(getUserErrorMessage(error));
    } finally {
      setWorking(false);
    }
  }

  async function runDraft() {
    setWorking(true);
    try {
      const detail = await data.api.runMarketResponse(projectId, predictionId);
      data.setDetail(detail);
      await data.refresh();
    } catch (error) {
      setMessage(getUserErrorMessage(error));
    } finally {
      setWorking(false);
    }
  }

  async function remove() {
    setWorking(true);
    try {
      await data.api.deleteMarketResponse(projectId, predictionId);
      navigate(projectRoutes.marketResponse(projectId), { replace: true });
    } catch (error) {
      setMessage(getUserErrorMessage(error));
    } finally {
      setWorking(false);
      setDeleteOpen(false);
    }
  }

  if (data.loading && data.items.length === 0 && !data.detail) return <LoadingState label="예상 시장 반응을 불러오고 있습니다" />;
  if (data.error && !data.detail && data.items.length === 0) {
    return <ErrorState description={getUserErrorMessage(data.error)} onRetry={data.refresh} />;
  }

  if (!predictionId && !creating) {
    return (
      <div className="validation-page">
        <PageHeader
          eyebrow="Persona Validation"
          title="시장 반응 예측"
          description="Persona와 메시지의 적합도를 상대 지표로 비교합니다."
          actions={<Button disabled={blocked} onClick={() => { setForm(initial); setCreating(true); }}>새 시장 반응 예측</Button>}
        />
        <Alert title="상대 지표 안내" tone="warning">실제 시장조사나 구매 확률이 아니며, 메시지 대안을 비교하기 위한 결정론적 예상 결과입니다.</Alert>
        {data.items.length === 0 ? (
          <EmptyState title="아직 시장 반응 결과가 없습니다" description="Persona와 메시지를 선택해 첫 비교를 시작해 보세요." />
        ) : (
          <div className="validation-list-grid">
            {data.items.map((item) => (
              <Card key={item.id}>
                <Badge tone={item.status === 'COMPLETED' ? 'success' : 'neutral'}>{item.status === 'COMPLETED' ? '최근 완료' : '초안 작성 중'}</Badge>
                <h2>{item.title}</h2>
                <p>Persona {item.personaCount}명 · 메시지 {item.messageCount}개</p>
                <p>최근 수정 {new Date(item.updatedAt).toLocaleString('ko-KR')}</p>
                <Link to={projectRoutes.marketResponseDetail(projectId, item.id)}>{item.status === 'COMPLETED' ? '결과 보기' : '계속 작성'}</Link>
              </Card>
            ))}
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="validation-page">
      <PageHeader
        eyebrow="Persona Validation · Market Response"
        title={predictionId ? data.detail?.prediction.title ?? '시장 반응 예측' : '새 시장 반응 예측'}
        description="최대 세 개 메시지를 같은 조건에서 Persona별로 비교합니다."
        actions={predictionId && <Button variant="danger" disabled={blocked} onClick={() => setDeleteOpen(true)}>삭제</Button>}
      />
      {blocked && <Alert title="현재 서비스 점검 중입니다" tone="warning">기존 결과는 조회할 수 있지만 생성·수정·실행은 잠시 사용할 수 없습니다.</Alert>}
      {message && <Alert tone="danger">{message}</Alert>}
      {showingEditor ? (
        <div className="validation-editor-layout">
          <section className="validation-editor-panel">
            <TextInput label="예측 제목" required value={activeForm.title} onChange={(event) => update({ title: event.target.value })} />
            <PersonaChoiceCards personas={data.personas} selectedIds={activeForm.personaIds} onChange={(personaIds) => update({ personaIds })} />
            <Select label="연결할 패널 인터뷰 결과" value={activeForm.panelInterviewId} onChange={(event) => update({ panelInterviewId: event.target.value })}>
              <option value="">연결하지 않고 실행</option>
              {completedInterviews.map((item) => <option key={item.id} value={item.id}>{item.title}</option>)}
            </Select>
            <TextInput label="가격대·가격 인식" value={activeForm.priceContext} maxLength="300" onChange={(event) => update({ priceContext: event.target.value })} />
            <TextInput label="주요 채널" value={activeForm.primaryChannel} maxLength="80" onChange={(event) => update({ primaryChannel: event.target.value })} />
          </section>
          <section className="validation-editor-panel">
            <MessageVariantEditor messages={activeForm.messages} onChange={(messages) => update({ messages })} />
            <div className="validation-editor-actions">
              <Button variant="outline" disabled={blocked} loading={working} onClick={() => void save(false)}>초안 저장</Button>
              <Button disabled={blocked} loading={working} onClick={() => void save(true)}>예상 시장 반응 실행</Button>
            </div>
          </section>
        </div>
      ) : data.detail?.prediction.status === 'COMPLETED' ? (
        <>
          <div className="validation-editor-actions">
            <Button variant="outline" disabled={blocked} onClick={() => { setForm(fromDetail(data.detail)); setEditing(true); }}>조건 수정</Button>
            <Button disabled={blocked} loading={working} onClick={runDraft}>같은 조건으로 다시 실행</Button>
          </div>
          <MarketResponseResult detail={data.detail} />
        </>
      ) : null}
      <Dialog open={deleteOpen} onClose={() => setDeleteOpen(false)} title="시장 반응 결과를 삭제할까요?">
        <p>기본 목록에서 제외되며 기존 Marketing Source Snapshot은 변경되지 않습니다.</p>
        <div className="validation-editor-actions">
          <Button variant="ghost" onClick={() => setDeleteOpen(false)}>취소</Button>
          <Button variant="danger" loading={working} onClick={remove}>삭제</Button>
        </div>
      </Dialog>
    </div>
  );
}
