import { useParams } from 'react-router-dom';

import { Button, LoadingState } from '../../../shared/ui/index.js';
import { JobTimeline } from '../../../shared/async-events/index.js';
import IdeaBriefReview from '../components/IdeaBriefReview.jsx';
import IdeaIntakeForm from '../components/IdeaIntakeForm.jsx';
import MissingRequiredFieldsForm from '../components/MissingRequiredFieldsForm.jsx';
import QuestionGroup from '../components/QuestionGroup.jsx';
import useIdeaIntake, { IDEA_FAILURE_KIND } from '../hooks/useIdeaIntake.js';
import { IDEA_INTAKE_SCREEN_STATE } from '../model/ideaIntakeModel.js';
import '../styles/idea-intake.css';

function StatePanel({ tone = 'info', title, description, action, role = 'status' }) {
  return <section className="idea-state-panel" data-tone={tone} role={role} aria-live="polite"><span aria-hidden="true" /><div><h3>{title}</h3><p>{description}</p>{action}</div></section>;
}

export default function IdeaIntakePage() {
  const { projectId } = useParams();
  const intake = useIdeaIntake(projectId);

  return <section className="idea-intake-page" aria-labelledby="idea-intake-title">
    <header className="idea-page-heading"><p>1단계 · Market Seed</p><h2 id="idea-intake-title">아이디어 입력</h2><span>최소 Seed를 입력하고 안전 확인과 AI 해석을 검토합니다.</span></header>
    <div className="visually-hidden" aria-live="polite">현재 화면 상태: {intake.screenState}</div>

    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.LOADING && <LoadingState label="아이디어 Draft를 준비하고 있습니다." />}
    {[IDEA_INTAKE_SCREEN_STATE.EMPTY, IDEA_INTAKE_SCREEN_STATE.READY].includes(intake.screenState) && (
      <IdeaIntakeForm draft={intake.draft} errors={intake.errors} onChange={intake.updateIntake} onFilesChange={intake.setFiles} onSubmit={intake.organizeIdea} />
    )}
    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.RUNNING && <><StatePanel
      title={intake.isReanalyzing ? '변경 내용을 다시 해석하고 있습니다.' : '안전 확인과 AI 해석을 진행하고 있습니다'}
      description={intake.isReanalyzing ? '최신 Seed를 다시 확인합니다.' : '입력 의미를 보존하면서 컨셉 탐색 범위를 정리합니다.'} />
      <JobTimeline events={intake.jobEvents.events} title="Market Seed 진행 상황" /></>}
    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.NEEDS_QUESTIONS && intake.questions.length > 0 && (
      <QuestionGroup questions={intake.questions} answers={intake.draft.answers} errors={intake.errors} onAnswer={intake.answerQuestion} onSubmit={intake.submitAnswers} />
    )}
    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.NEEDS_FIELDS && (
      <MissingRequiredFieldsForm fieldKeys={intake.draft.assessment.readiness?.missingFieldKeys ?? []}
        catalog={intake.draft.catalog} fields={intake.draft.fields} errors={intake.errors}
        onChange={intake.updateBriefField} onSubmit={intake.submitMissingFields} />
    )}
    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.RECOVERY && <StatePanel tone="warning"
      title="이전 분석 작업은 종료되었지만 아이디어 상태를 다시 연결해야 합니다"
      description="현재 입력으로 최종 분석을 다시 실행해 주세요."
      action={<Button type="button" variant="outline" onClick={intake.reanalyze}>다시 분석하기</Button>} />}
    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.REVIEW && (
      <IdeaBriefReview draft={intake.draft} onInterpretationChange={intake.updateInterpretation}
        onCommitmentValueChange={intake.updateCommitmentValue} onCommitmentAction={intake.setCommitmentAction}
        onConfirm={intake.confirmBrief} />
    )}
    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.SAFETY_BLOCKED && <StatePanel tone="warning" role="alert"
      title="이 아이디어는 현재 형태로 컨셉 생성을 진행할 수 없습니다"
      description={intake.draft.safetyReview?.userFacingReason || '안전한 방향으로 아이디어를 다시 구성해 주세요.'}
      action={<Button type="button" variant="outline" onClick={intake.restart}>아이디어 다시 입력</Button>} />}
    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.FAILED && <StatePanel tone="danger" role="alert" title="아이디어 상태를 확인하지 못했습니다" description={intake.failureMessage || '잠시 후 다시 시도해 주세요.'} action={<Button type="button" variant="outline" onClick={intake.failureKind === IDEA_FAILURE_KIND.DERIVATION_FAILURE ? intake.reanalyze : intake.refresh}>{intake.failureKind === IDEA_FAILURE_KIND.DERIVATION_FAILURE ? '다시 분석하기' : '상태 다시 확인하기'}</Button>} />}
    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.CONFIRMED && <StatePanel tone="success" title="Market Seed와 AI 해석을 확인했습니다." description="이제 확정 조건을 보존하며 컨셉 후보를 만들 수 있습니다." />}
  </section>;
}
