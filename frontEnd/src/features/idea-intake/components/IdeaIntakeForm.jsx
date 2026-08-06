import { Button, FileInput, Textarea } from '../../../shared/ui/index.js';

import ErrorSummary from './ErrorSummary.jsx';

const OPTIONAL_FIELDS = Object.freeze([
  ['problem', '해결하려는 문제', '현재 어떤 문제가 있고 왜 해결해야 하는지 적어 주세요.'],
  ['expectedUsers', '예상 사용자', '서비스를 사용할 사람이나 조직을 적어 주세요.'],
  ['region', '서비스 지역', '국가, 도시 또는 온라인 서비스 범위를 적어 주세요.'],
  ['desiredOutcome', '원하는 결과', '이 아이디어로 만들고 싶은 변화를 적어 주세요.'],
  ['constraints', '반드시 지켜야 할 조건', '예산, 일정, 운영 방식 등 고정 조건을 적어 주세요.'],
  ['avoidMethods', '피하고 싶은 방식', '사용하지 않을 기술, 영업 방식 또는 위험 요소를 적어 주세요.'],
]);

export default function IdeaIntakeForm({ draft, errors, onChange, onFilesChange, onSubmit }) {
  return (
    <form className="idea-intake-form" onSubmit={onSubmit} noValidate>
      <ErrorSummary errors={errors} />
      <section className="idea-form-section" aria-labelledby="idea-core-heading">
        <div className="idea-section-heading"><p>첫 단계</p><h3 id="idea-core-heading">아이디어를 들려주세요</h3><span>완성된 사업계획이 아니어도 괜찮습니다. 아이디어 개요만 필수입니다.</span></div>
        <Textarea
          id="overview"
          label="아이디어 개요"
          description="무엇을, 누구를 위해 만들고 싶은지 자유롭게 적어 주세요."
          required
          rows="6"
          value={draft.intake.overview}
          error={errors.overview}
          onChange={(event) => onChange('overview', event.target.value)}
        />
      </section>

      <section className="idea-form-section" aria-labelledby="idea-details-heading">
        <div className="idea-section-heading"><p>선택 입력</p><h3 id="idea-details-heading">알고 있는 내용을 더해 주세요</h3><span>비어 있는 내용은 AI 후속 질문에서 보완할 수 있습니다.</span></div>
        <div className="idea-form-grid">
          {OPTIONAL_FIELDS.map(([field, label, description]) => (
            <Textarea
              key={field}
              id={field}
              label={label}
              description={description}
              rows="3"
              value={draft.intake[field]}
              onChange={(event) => onChange(field, event.target.value)}
            />
          ))}
        </div>
      </section>

      <section className="idea-form-section" aria-labelledby="idea-files-heading">
        <div className="idea-section-heading"><p>선택 입력</p><h3 id="idea-files-heading">참고 파일</h3><span>아이디어를 설명하는 문서가 있다면 함께 선택해 주세요.</span></div>
        <FileInput
          id="referenceFiles"
          label="참고 파일 선택"
          description="여러 파일을 선택할 수 있습니다."
          multiple
          onChange={(event) => onFilesChange(Array.from(event.target.files ?? []))}
        />
        {draft.referenceFiles.length > 0 && <ul className="idea-file-list" aria-label="선택한 참고 파일">{draft.referenceFiles.map((file) => <li key={`${file.name}-${file.size}`}>{file.name}</li>)}</ul>}
      </section>

      <div className="idea-primary-action idea-primary-action--sticky">
        <Button type="submit">AI로 아이디어 정리하기</Button>
      </div>
    </form>
  );
}
