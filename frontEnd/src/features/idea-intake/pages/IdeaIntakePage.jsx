import { useEffect, useRef } from 'react';
import { Link, useOutletContext, useParams } from 'react-router-dom';

import { Button, LoadingState } from '../../../shared/ui/index.js';
import { JobTimeline } from '../../../shared/async-events/index.js';
import IdeaBriefReview from '../components/IdeaBriefReview.jsx';
import IdeaIntakeForm from '../components/IdeaIntakeForm.jsx';
import MissingRequiredFieldsForm from '../components/MissingRequiredFieldsForm.jsx';
import QuestionGroup from '../components/QuestionGroup.jsx';
import useIdeaIntake, { IDEA_FAILURE_KIND } from '../hooks/useIdeaIntake.js';
import { IDEA_INTAKE_SCREEN_STATE } from '../model/ideaIntakeModel.js';
import { projectRoutes } from '../../../app/routing/projectRoutes.js';
import '../styles/idea-intake.css';

function StatePanel({ tone = 'info', title, description, action, role = 'status' }) {
  return <section className="idea-state-panel" data-tone={tone} role={role} aria-live="polite"><span aria-hidden="true" /><div><h3>{title}</h3><p>{description}</p>{action}</div></section>;
}

const CONFIRMED_USER_FIELDS = Object.freeze([
  ['ideaOverview', '사용자가 입력한 아이디어'], ['problem', '해결하려는 문제'], ['targetUsers', '예상 사용자'],
  ['targetRegion', '대상 지역'], ['knownCompetitors', '경쟁자 맥락'], ['revenueModel', '수익 모델'],
  ['price', '가격'], ['channels', '채널'], ['differentiators', '차별점'],
  ['budgetConstraint', '예산 조건'], ['teamConstraint', '팀 조건'],
  ['timelineConstraint', '일정 조건'], ['otherConstraint', '기타 조건'],
]);
const CONFIRMED_AI_FIELDS = Object.freeze([
  ['interpretedProblem', 'AI가 이해한 문제'], ['interpretedTargetUsers', 'AI가 이해한 예상 사용자'],
  ['usageContext', '사용 맥락'], ['industryCategory', '업종 분류'], ['researchScope', '사업안 탐색 범위'],
  ['conciseIdeaDefinition', '한 줄 아이디어 정의'], ['targetRegionInterpretation', '지역 해석'],
  ['relevantKnownCompetitorContext', '경쟁자 맥락'],
]);

export function ConfirmedIdeaSummary({ draft, projectId, onEdit, hasDownstream = false }) {
  const userValues = CONFIRMED_USER_FIELDS.filter(([key]) => draft?.fields?.[key]?.value?.trim()
    || draft?.intake?.[key]?.trim());
  const aiValues = CONFIRMED_AI_FIELDS.filter(([key]) => draft?.interpretation?.[key]?.trim());
  const beginEdit = () => {
    const accepted = !hasDownstream || window.confirm('아이디어를 변경하면 기존 사업안과 후속 분석은 이전 조건을 기준으로 한 결과가 됩니다. 변경 내용을 확정한 뒤 사업안을 다시 검토해야 합니다.');
    if (accepted) onEdit();
  };
  return <section className="idea-confirmed-summary" aria-labelledby="idea-confirmed-title">
    <header><span className="pipeline-status" data-tone="success">확정 완료</span><div><h3 id="idea-confirmed-title">아이디어 정리가 완료되었습니다.</h3><p>확정한 내용은 읽기 전용으로 보존됩니다.</p></div></header>
    <section><h4>사용자가 확정한 내용</h4><dl>{userValues.map(([key, label]) => <div key={key}><dt>{label}</dt><dd>{draft.fields?.[key]?.value || draft.intake?.[key]}</dd></div>)}</dl></section>
    <section><h4>AI가 이해한 내용</h4><dl>{aiValues.map(([key, label]) => <div key={key}><dt>{label}</dt><dd>{draft.interpretation[key]}</dd></div>)}</dl></section>
    <section><h4>안전 확인 결과</h4><p>{draft?.safetyReview?.userFacingReason || '안전 확인 결과가 확정되었습니다.'}</p>{draft?.safetyReview?.restrictions?.length > 0 && <ul>{draft.safetyReview.restrictions.map((item) => <li key={item}>{item}</li>)}</ul>}</section>
    <footer><Link className="ui-button ui-button--primary" to={projectRoutes.concepts(projectId)}>다음 단계 · 사업안 검토</Link><Button type="button" variant="outline" onClick={beginEdit}>아이디어 수정</Button></footer>
  </section>;
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

  return <section className="idea-intake-page" aria-labelledby="idea-intake-title">
    <header className="idea-page-heading"><p>1단계 · 아이디어 정리</p><h2 id="idea-intake-title">아이디어 입력</h2><span>핵심 조건을 입력하고 안전 확인과 AI 해석을 검토합니다.</span></header>
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
    {intake.screenState === IDEA_INTAKE_SCREEN_STATE.CONFIRMED && <ConfirmedIdeaSummary draft={intake.draft}
      projectId={projectId} onEdit={intake.editConfirmed}
      hasDownstream={['QUEUED', 'RUNNING', 'NEEDS_INPUT', 'COMPLETED', 'FAILED', 'STALE']
        .includes(outlet.modules?.find((module) => module.id === 'concepts')?.status)} />}
  </section>;
}
