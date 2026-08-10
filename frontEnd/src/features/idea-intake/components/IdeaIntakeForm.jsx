import { Button, FileInput, Textarea } from '../../../shared/ui/index.js';

import ErrorSummary from './ErrorSummary.jsx';

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

export default function IdeaIntakeForm({ draft, errors, onChange, onFilesChange, onSubmit }) {
  return (
    <form className="idea-intake-form" onSubmit={onSubmit} noValidate>
      <ErrorSummary errors={errors} />
      <section className="idea-form-section" aria-labelledby="idea-core-heading">
        <div className="idea-section-heading">
          <p>필수 입력 · 3개</p>
          <h3 id="idea-core-heading">아이디어의 출발점을 알려주세요</h3>
          <span>이 세 가지가 있으면 컨셉 탐색을 시작할 수 있습니다.</span>
        </div>
        <div className="idea-form-grid idea-form-grid--required">
          {REQUIRED_FIELDS.map(([field, label, description]) => (
            <Textarea key={field} id={field} label={label} description={description} required rows="4"
              value={draft.intake[field]} error={errors[field]}
              onChange={(event) => onChange(field, event.target.value)} />
          ))}
        </div>
      </section>

      <details className="idea-optional-section">
        <summary><strong>이미 정한 내용이 있다면 입력해 주세요</strong><span>선택 입력 · 비워 두어도 진행할 수 있습니다.</span></summary>
        <section className="idea-form-section" aria-label="사용자가 확정한 선택 조건">
          <p className="idea-locked-notice">입력한 값은 사용자 확정 조건으로 잠기며 AI가 임의로 변경하지 않습니다.</p>
          <div className="idea-form-grid">
            {OPTIONAL_FIELDS.map(([field, label, description]) => (
              <Textarea key={field} id={field} label={label} description={description} rows="3"
                value={draft.intake[field]}
                onChange={(event) => onChange(field, event.target.value)} />
            ))}
          </div>
        </section>
      </details>

      <section className="idea-form-section" aria-labelledby="idea-files-heading">
        <div className="idea-section-heading"><p>선택 입력</p><h3 id="idea-files-heading">참고 파일</h3><span>아이디어를 설명하는 자료가 있다면 선택해 주세요.</span></div>
        <FileInput id="referenceFiles" label="참고 파일 선택" description="여러 파일을 선택할 수 있습니다." multiple
          onChange={(event) => onFilesChange(Array.from(event.target.files ?? []))} />
        {draft.referenceFiles.length > 0 && <ul className="idea-file-list" aria-label="선택한 참고 파일">{draft.referenceFiles.map((file) => <li key={`${file.name}-${file.size}`}>{file.name}</li>)}</ul>}
      </section>

      <div className="idea-primary-action idea-primary-action--sticky">
        <Button type="submit">안전 확인 및 AI 해석</Button>
      </div>
    </form>
  );
}
