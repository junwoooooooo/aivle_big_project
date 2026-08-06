import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';

import { Alert, Badge, Button, Card, Dialog, EmptyState, ErrorState, LoadingState, PageHeader, Select, TextInput } from '../../../shared/ui/index.js';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { useServicePolicy } from '../../service-policy/useServicePolicy.js';
import { projectRoutes } from '../../projects/routing/projectRoutes.js';
import PanelInterviewResult from '../components/PanelInterviewResult.jsx';
import PersonaChoiceCards from '../components/PersonaChoiceCards.jsx';
import QuestionEditor from '../components/QuestionEditor.jsx';
import useValidationData from '../hooks/useValidationData.js';
import { DEFAULT_QUESTIONS } from '../model/validationModel.js';
import '../validation.css';

const PURPOSES = [
  ['PROBLEM_DISCOVERY', '고객 문제 확인'],
  ['VALUE_PROPOSITION', '가치 제안 검증'],
  ['PURCHASE_MOTIVATION', '구매 동기 확인'],
  ['MESSAGE_REACTION', '마케팅 메시지 반응'],
  ['CUSTOM', '직접 설정'],
];

const EMPTY = {
  title: '',
  purpose: 'PROBLEM_DISCOVERY',
  personaIds: [],
  questions: DEFAULT_QUESTIONS.PROBLEM_DISCOVERY,
};

function fromDetail(detail) {
  return {
    title: detail.interview.title,
    purpose: detail.interview.purpose,
    personaIds: detail.personaIds,
    questions: detail.questions,
  };
}

export default function PanelInterviewPage() {
  const { projectId, interviewId } = useParams();
  const navigate = useNavigate();
  const data = useValidationData(projectId, 'interview', interviewId);
  const { policy } = useServicePolicy();
  const [form, setForm] = useState(null);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState(false);
  const [working, setWorking] = useState(false);
  const [message, setMessage] = useState(null);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const blocked = policy.maintenanceMode;
  const activeForm = form ?? (data.detail ? fromDetail(data.detail) : EMPTY);
  const showingEditor = creating || (interviewId && (data.detail?.interview.status === 'DRAFT' || editing));

  function update(patch) {
    setForm({ ...activeForm, ...patch });
  }

  function validate() {
    if (!activeForm.title.trim()) return '인터뷰 제목을 입력해 주세요.';
    if (activeForm.personaIds.length < 1 || activeForm.personaIds.length > 3) return 'Persona를 1~3개 선택해 주세요.';
    const questions = activeForm.questions.filter((question) => question.trim());
    if (questions.length < 3 || questions.length > 10) return '질문을 3~10개 입력해 주세요.';
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
      questions: activeForm.questions.map((question) => question.trim()),
    };
    try {
      let detail;
      if (interviewId) {
        detail = await data.api.updateInterview(projectId, interviewId, payload);
      } else {
        detail = await data.api.createInterview(projectId, payload);
      }
      const id = detail.interview.id;
      if (runAfter) detail = await data.api.runInterview(projectId, id);
      data.setDetail(detail);
      setForm(null);
      setCreating(false);
      setEditing(false);
      if (!interviewId) {
        navigate(projectRoutes.interviewDetail(projectId, id), { replace: true });
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
      const detail = await data.api.runInterview(projectId, interviewId);
      data.setDetail(detail);
      setEditing(false);
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
      await data.api.deleteInterview(projectId, interviewId);
      navigate(projectRoutes.interview(projectId), { replace: true });
    } catch (error) {
      setMessage(getUserErrorMessage(error));
    } finally {
      setWorking(false);
      setDeleteOpen(false);
    }
  }

  if (data.loading && data.items.length === 0 && !data.detail) return <LoadingState label="예상 인터뷰를 불러오고 있습니다" />;
  if (data.error && !data.detail && data.items.length === 0) {
    return <ErrorState description={getUserErrorMessage(data.error)} onRetry={data.refresh} />;
  }

  if (!interviewId && !creating) {
    return (
      <div className="validation-page">
        <PageHeader
          eyebrow="Persona Validation"
          title="패널 인터뷰"
          description="Persona 특성을 바탕으로 질문별 예상 반응과 핵심 우려를 비교합니다."
          actions={<Button disabled={blocked} onClick={() => { setForm(EMPTY); setCreating(true); }}>새 인터뷰 만들기</Button>}
        />
        <Alert title="예상 인터뷰 안내" tone="warning">실제 사람을 대상으로 한 인터뷰가 아니며 실제 고객 조사 결과를 대체하지 않습니다.</Alert>
        {data.items.length === 0 ? (
          <EmptyState title="아직 인터뷰가 없습니다" description="Persona와 질문을 선택해 첫 예상 인터뷰를 만들어 보세요." />
        ) : (
          <div className="validation-list-grid">
            {data.items.map((item) => (
              <Card key={item.id}>
                <Badge tone={item.status === 'COMPLETED' ? 'success' : 'neutral'}>{item.status === 'COMPLETED' ? '최근 완료' : '초안 작성 중'}</Badge>
                <h2>{item.title}</h2>
                <p>{PURPOSES.find(([value]) => value === item.purpose)?.[1]} · Persona {item.personaCount}명 · 질문 {item.questionCount}개</p>
                <p>최근 수정 {new Date(item.updatedAt).toLocaleString('ko-KR')}</p>
                <Link to={projectRoutes.interviewDetail(projectId, item.id)}>{item.status === 'COMPLETED' ? '결과 보기' : '계속 작성'}</Link>
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
        eyebrow="Persona Validation · Panel Interview"
        title={interviewId ? data.detail?.interview.title ?? '패널 인터뷰' : '새 예상 인터뷰'}
        description="목적, 대상 Persona, 질문을 구성한 뒤 예상 답변을 생성합니다."
        actions={interviewId && <Button variant="danger" disabled={blocked} onClick={() => setDeleteOpen(true)}>삭제</Button>}
      />
      {blocked && <Alert title="현재 서비스 점검 중입니다" tone="warning">기존 결과는 조회할 수 있지만 생성·수정·실행은 잠시 사용할 수 없습니다.</Alert>}
      {message && <Alert tone="danger">{message}</Alert>}
      {showingEditor ? (
        <div className="validation-editor-layout">
          <section className="validation-editor-panel">
            <TextInput label="인터뷰 제목" required value={activeForm.title} onChange={(event) => update({ title: event.target.value })} />
            <Select label="인터뷰 목적" value={activeForm.purpose} onChange={(event) => {
              const purpose = event.target.value;
              update({ purpose, questions: DEFAULT_QUESTIONS[purpose] });
            }}>
              {PURPOSES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </Select>
            <PersonaChoiceCards personas={data.personas} selectedIds={activeForm.personaIds} onChange={(personaIds) => update({ personaIds })} />
          </section>
          <section className="validation-editor-panel">
            <QuestionEditor questions={activeForm.questions} onChange={(questions) => update({ questions })} />
            <div className="validation-editor-actions">
              <Button variant="outline" disabled={blocked} loading={working} onClick={() => void save(false)}>초안 저장</Button>
              <Button disabled={blocked} loading={working} onClick={() => void save(true)}>예상 인터뷰 실행</Button>
            </div>
          </section>
        </div>
      ) : data.detail?.interview.status === 'COMPLETED' ? (
        <>
          <div className="validation-editor-actions">
            <Button variant="outline" disabled={blocked} onClick={() => { setForm(fromDetail(data.detail)); setEditing(true); }}>질문·Persona 수정</Button>
            <Button disabled={blocked} loading={working} onClick={runDraft}>같은 조건으로 다시 실행</Button>
          </div>
          <PanelInterviewResult detail={data.detail} />
        </>
      ) : null}
      <Dialog open={deleteOpen} onClose={() => setDeleteOpen(false)} title="인터뷰를 삭제할까요?">
        <p>기본 목록에서 제외되며 이미 저장된 Marketing Source Snapshot은 변경되지 않습니다.</p>
        <div className="validation-editor-actions">
          <Button variant="ghost" onClick={() => setDeleteOpen(false)}>취소</Button>
          <Button variant="danger" loading={working} onClick={remove}>삭제</Button>
        </div>
      </Dialog>
    </div>
  );
}
