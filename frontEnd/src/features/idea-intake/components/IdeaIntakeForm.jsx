import { useState } from 'react';

import { Button, FileDropzone, ProjectFormRow, ProjectFormSection, ProjectOptionalField, ProjectOptionalFields, ProjectSplitWorkspace, ProjectWorkspaceActions } from '../../../shared/ui/index.js';

import ErrorSummary from './ErrorSummary.jsx';
import { IDEA_REFERENCE_ACCEPT } from '../model/ideaIntakeModel.js';

const REQUIRED_FIELDS = Object.freeze([
  ['ideaOverview', '아이디어 개요', '어떤 제품이나 서비스를 생각하고 있는지 자유롭게 적어 주세요.'],
  ['problem', '해결하려는 문제', '현재 어떤 불편이나 문제가 있고 왜 해결해야 하는지 적어 주세요.'],
  ['targetUsers', '예상 사용자', '이 제품이나 서비스를 사용할 사람이나 조직을 적어 주세요.'],
]);

const OPTIONAL_FIELDS = Object.freeze([
  ['targetRegion', '대상 지역', '국가, 도시 또는 온라인 서비스 범위'],
  ['knownCompetitors', '알려진 경쟁자', '이미 알고 있는 경쟁 제품이나 서비스'],
  ['revenueModel', '수익 모델', '구독, 판매, 중개 수수료 등 이미 정한 방식'],
  ['price', '가격', '예: 월 9,900원 정기 구독'],
  ['channels', '채널', '앱, 웹, 오프라인 매장, 파트너 판매 등'],
  ['differentiators', '차별점', '반드시 유지할 경쟁 우위'],
  ['budgetConstraint', '예산 제약', '사용 가능한 예산 범위'],
  ['teamConstraint', '팀 제약', '현재 인력이나 역량의 제약'],
  ['timelineConstraint', '일정 제약', '출시 또는 검증 일정'],
  ['otherConstraint', '기타 제약', '그 밖에 반드시 지켜야 할 조건'],
]);

export default function IdeaIntakeForm({ draft, errors, attachmentError, submissionError, uploadingAttachments, organizing, onChange, onFilesChange, onSubmit }) {
  const firstError = OPTIONAL_FIELDS.find(([field]) => errors[field])?.[0] ?? null;
  const [openOptional, setOpenOptional] = useState(firstError);
  const optionalCompleted = OPTIONAL_FIELDS.filter(([field]) => draft.intake[field]?.trim()).length;
  const requiredCompleted = REQUIRED_FIELDS.filter(([field]) => draft.intake[field]?.trim()).length;
  const summarize = (value) => value?.trim().replace(/\s+/g, ' ') || '';
  const primary = <ProjectFormSection className="idea-form-section idea-required-workspace" eyebrow={`필수 입력 · ${requiredCompleted} / ${REQUIRED_FIELDS.length} 완료`} title="아이디어의 출발점을 알려주세요" description="세 가지 핵심 정보와 참고 자료를 바탕으로 사업안을 탐색합니다.">
    {REQUIRED_FIELDS.map(([field, label, description]) => <ProjectFormRow key={field} id={field} label={label} description={description} required error={errors[field]}>
      {(fieldProps) => <textarea rows="4" value={draft.intake[field]} onChange={(event) => onChange(field, event.target.value)} {...fieldProps} />}
    </ProjectFormRow>)}
    <div className="idea-attachment-field"><div><strong>참고 자료</strong><span>문서 내용은 사업안 분석의 참고 근거로 사용합니다.</span></div><FileDropzone id="referenceFiles" label="파일 선택" description="DOCX, TXT, MD 파일을 끌어 놓거나 선택하세요" acceptLabel="파일당 최대 20MB · 최대 20개" accept={IDEA_REFERENCE_ACCEPT} files={draft.referenceFiles} multiple onFilesChange={onFilesChange} uploading={uploadingAttachments} error={attachmentError} /></div>
  </ProjectFormSection>;
  const secondary = <ProjectOptionalFields completed={optionalCompleted} total={OPTIONAL_FIELDS.length} description="입력한 조건은 이후 분석에서도 그대로 사용합니다.">
    {OPTIONAL_FIELDS.map(([field, label, description]) => <ProjectOptionalField key={field} id={field} label={label}
      summary={errors[field] || summarize(draft.intake[field])} error={errors[field]} expanded={openOptional === field}
      onToggle={() => setOpenOptional((current) => current === field ? null : field)}>
      <ProjectFormRow id={`${field}-input`} label={label} description={description} error={errors[field]}>
        {(fieldProps) => <textarea rows="3" value={draft.intake[field]} onChange={(event) => onChange(field, event.target.value)} {...fieldProps} />}
      </ProjectFormRow>
    </ProjectOptionalField>)}
  </ProjectOptionalFields>;
  return (
    <form className="idea-intake-form" onSubmit={onSubmit} noValidate>
      <ErrorSummary errors={errors} />
      {submissionError && <div className="idea-intake-form__submit-error" role="alert">{submissionError}</div>}
      <ProjectSplitWorkspace primary={primary} secondary={secondary} />

      <ProjectWorkspaceActions className="idea-primary-action">
        <Button type="submit" loading={organizing} disabled={uploadingAttachments || organizing}>{organizing ? '아이디어를 정리하고 있습니다...' : '입력 내용으로 아이디어 정리하기'}</Button>
      </ProjectWorkspaceActions>
    </form>
  );
}
