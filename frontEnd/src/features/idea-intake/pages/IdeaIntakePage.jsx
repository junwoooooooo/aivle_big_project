import { useEffect, useRef } from 'react';
import { useOutletContext, useParams } from 'react-router-dom';

import { Button, LoadingState, ProjectExecutionExperience, ProjectStageHeader, ProjectWorkspace } from '../../../shared/ui/index.js';
import IdeaBriefReview from '../components/IdeaBriefReview.jsx';
import IdeaIntakeForm from '../components/IdeaIntakeForm.jsx';
import MissingRequiredFieldsForm from '../components/MissingRequiredFieldsForm.jsx';
import QuestionGroup from '../components/QuestionGroup.jsx';
import useIdeaIntake, { IDEA_FAILURE_KIND } from '../hooks/useIdeaIntake.js';
import { IDEA_INTAKE_SCREEN_STATE } from '../model/ideaIntakeModel.js';
import { ideaExecutionPresentation } from '../model/ideaExecutionPresentation.js';
import '../styles/idea-intake.css';

function StatePanel({ tone = 'info', title, description, action, role = 'status' }) {
  return <section className="idea-state-panel" data-tone={tone} role={role} aria-live="polite"><span aria-hidden="true" /><div><h3>{title}</h3><p>{description}</p>{action}</div></section>;
}

export default function IdeaIntakePage() {
  const { projectId } = useParams();
  const outlet = useOutletContext() ?? {};
  const intake = useIdeaIntake(projectId);
  const confirmedRefreshed = useRef(false);
  useEffect(() => {
    if (intake.screenState !== IDEA_INTAKE_SCREEN_STATE.CONFIRMED) {
      confirmedRefreshed.current = false;
      return;
    }
    if (!confirmedRefreshed.current) {
      confirmedRefreshed.current = true;
      outlet.moduleState?.retry?.();
    }
  }, [intake.screenState, outlet.moduleState]);

  const execution = ideaExecutionPresentation(intake.jobEvents?.events ?? []);
  const conceptsStatus = outlet.modules?.find((module) => module.id === 'concepts')?.status;
  const hasDownstream = ['QUEUED', 'RUNNING', 'NEEDS_INPUT', 'COMPLETED', 'FAILED', 'STALE'].includes(conceptsStatus);
  return <ProjectWorkspace as="section" mode="compose" className="idea-intake-page" aria-labelledby="idea-intake-title">
    <ProjectStageHeader step={1} eyebrow="사업 기획" titleId="idea-intake-title" title="사업 아이디어의 출발점을 알려주세요"
      description="해결하려는 문제와 예상 사용자를 입력하면, 다음 검토에 필요한 사업안으로 정리합니다." />
    <div className="visually-hidden" aria-live="polite">현재 화면 상태: {intake.screenState}</div>

    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.LOADING && <LoadingState label="아이디어 Draft를 준비하고 있습니다." />}
    {[IDEA_INTAKE_SCREEN_STATE.EMPTY, IDEA_INTAKE_SCREEN_STATE.READY].includes(intake.screenState) && (
      <IdeaIntakeForm draft={intake.draft} errors={intake.errors} attachmentError={intake.attachmentError}
        uploadingAttachments={intake.uploadingAttachments} onChange={intake.updateIntake}
        onFilesChange={intake.setFiles} onSubmit={intake.organizeIdea} />
    )}
    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.RUNNING && <ProjectExecutionExperience
      title={intake.isReanalyzing ? '변경한 아이디어를 다시 정리하고 있습니다' : '아이디어를 정리하고 있습니다'}
      {...execution} failureMessage="아이디어 정리를 완료하지 못했습니다."
      needsInputMessage="아이디어를 계속 정리하려면 추가 정보가 필요합니다." />}
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
      <IdeaBriefReview draft={intake.draft} projectId={projectId} confirming={intake.isConfirming} onInterpretationChange={intake.updateInterpretation}
        onCommitmentValueChange={intake.updateCommitmentValue} onCommitmentAction={intake.setCommitmentAction}
        onConfirm={intake.confirmBrief} />
    )}
    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.SAFETY_BLOCKED && <StatePanel tone="warning" role="alert"
      title="현재 형태로는 다음 단계로 진행하기 어렵습니다"
      description={intake.draft.safetyReview?.userFacingReason || '진행 가능한 방향으로 아이디어 내용을 조정해 주세요.'}
      action={<Button type="button" variant="outline" onClick={intake.restart}>아이디어 다시 입력</Button>} />}
    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.FAILED && <StatePanel tone="danger" role="alert" title="아이디어 상태를 확인하지 못했습니다" description={intake.failureMessage || '잠시 후 다시 시도해 주세요.'} action={<Button type="button" variant="outline" onClick={intake.failureKind === IDEA_FAILURE_KIND.DERIVATION_FAILURE ? intake.reanalyze : intake.refresh}>{intake.failureKind === IDEA_FAILURE_KIND.DERIVATION_FAILURE ? '다시 분석하기' : '상태 다시 확인하기'}</Button>} />}
    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.CONFIRMED && <IdeaBriefReview draft={intake.draft}
      projectId={projectId} confirmed onEdit={intake.editConfirmed} hasDownstream={hasDownstream} />}
  </ProjectWorkspace>;
}
