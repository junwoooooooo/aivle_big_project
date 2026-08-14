import { Button, ProjectFormRow } from '../../../shared/ui/index.js';

import ErrorSummary from './ErrorSummary.jsx';

const PROMPTS = Object.freeze({
  ideaOverview: '어떤 제품이나 서비스를 생각하고 있는지 알려 주세요.',
  problem: '해결하려는 핵심 문제를 알려 주세요.',
  targetUsers: '예상 사용자나 고객을 알려 주세요.',
});

export default function MissingRequiredFieldsForm({
  fieldKeys, catalog, fields, errors, onChange, onSubmit,
}) {
  const catalogByKey = Object.fromEntries(catalog.map((field) => [field.key, field]));
  if (fieldKeys.length === 0) return null;
  return <form className="idea-question-flow" onSubmit={onSubmit} noValidate>
    <div className="idea-section-heading">
      <p>필수 정보 확인</p>
      <h3>사업안 탐색에 필요한 최소 정보를 확인해 주세요.</h3>
      <span>법률·운영 상세가 아니라 문제와 사용자 의도만 확인합니다.</span>
    </div>
    <ErrorSummary errors={errors} title="입력이 필요한 항목이 있습니다." />
    <div className="idea-question-grid project-form-layout">{fieldKeys.map((fieldKey) => {
      const definition = catalogByKey[fieldKey];
      if (!definition) return null;
      return <ProjectFormRow key={fieldKey} id={`missing-field-${fieldKey}`} label={definition.label}
        description={PROMPTS[fieldKey] ?? `${definition.label}에 필요한 사실을 입력해 주세요.`} error={errors[fieldKey]}>
        {(fieldProps) => <textarea value={fields[fieldKey]?.value ?? ''}
          onChange={(event) => onChange(fieldKey, event.target.value)} {...fieldProps} />}
      </ProjectFormRow>;
    })}</div>
    <div className="idea-primary-action idea-primary-action--sticky">
      <Button type="submit">누락 정보 반영하고 다시 정리하기</Button>
    </div>
  </form>;
}
