import { useParams } from 'react-router-dom';

import { Button, LoadingState } from '../../../shared/ui/index.js';
import { JobTimeline } from '../../../shared/async-events/index.js';
import IdeaBriefReview from '../components/IdeaBriefReview.jsx';
import IdeaIntakeForm from '../components/IdeaIntakeForm.jsx';
import QuestionGroup from '../components/QuestionGroup.jsx';
import useIdeaIntake from '../hooks/useIdeaIntake.js';
import { IDEA_INTAKE_SCREEN_STATE } from '../model/ideaIntakeModel.js';
import '../styles/idea-intake.css';

function StatePanel({ tone = 'info', title, description, action, role = 'status' }) {
  return <section className="idea-state-panel" data-tone={tone} role={role} aria-live="polite"><span aria-hidden="true" /><div><h3>{title}</h3><p>{description}</p>{action}</div></section>;
}

export default function IdeaIntakePage() {
  const { projectId } = useParams();
  const intake = useIdeaIntake(projectId);

  return <section className="idea-intake-page" aria-labelledby="idea-intake-title">
    <header className="idea-page-heading"><p>1단계 · Idea Brief</p><h2 id="idea-intake-title">아이디어 정리</h2><span>대화형 Workspace 없이 입력, 후속 질문, Brief 검토 순서로 아이디어를 구체화합니다.</span></header>
    <div className="visually-hidden" aria-live="polite">현재 화면 상태: {intake.screenState}</div>

    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.LOADING && <LoadingState label="아이디어 Draft를 준비하고 있습니다." />}
    {[IDEA_INTAKE_SCREEN_STATE.EMPTY, IDEA_INTAKE_SCREEN_STATE.READY].includes(intake.screenState) && (
      <IdeaIntakeForm draft={intake.draft} errors={intake.errors} onChange={intake.updateIntake} onFilesChange={intake.setFiles} onSubmit={intake.organizeIdea} />
    )}
    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.RUNNING && <><StatePanel title="아이디어를 정리하고 있습니다" description="입력 내용을 Idea Brief 필드와 후속 질문으로 구성하고 있습니다." /><JobTimeline events={intake.jobEvents.events} title="Idea Brief 진행 상황" /></>}
    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.NEEDS_INPUT && (
      <QuestionGroup questions={intake.questions} answers={intake.draft.answers} errors={intake.errors} onAnswer={intake.answerQuestion} onSubmit={intake.submitAnswers} />
    )}
    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.REVIEW && (
      <IdeaBriefReview draft={intake.draft} onFieldChange={intake.updateBriefField} onConfirm={intake.confirmBrief} />
    )}
    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.FAILED && <StatePanel tone="danger" role="alert" title="아이디어 정리를 완료하지 못했습니다" description={intake.failureMessage || '잠시 후 다시 시도해 주세요.'} action={<Button type="button" variant="outline" onClick={intake.retry}>입력 화면으로 돌아가기</Button>} />}
    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.CONFIRMED && <StatePanel tone="success" title="Idea Brief가 준비되었습니다" description="R2A에서는 확인 요청을 로컬 경계까지만 준비했습니다. 실제 컨셉 생성 연결은 후속 단계에서 제공됩니다." />}
  </section>;
}
