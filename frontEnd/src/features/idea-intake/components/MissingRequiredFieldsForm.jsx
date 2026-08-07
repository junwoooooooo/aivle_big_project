import { Button } from '../../../shared/ui/index.js';

import ErrorSummary from './ErrorSummary.jsx';

const PROMPTS = Object.freeze({
  physicalActivity: '사용자나 파트너가 실제 오프라인 활동을 수행하나요?',
  personalData: '서비스에서 어떤 사용자 정보를 수집·사용하나요?',
});

export default function MissingRequiredFieldsForm({
  fieldKeys, catalog, fields, errors, onChange, onSubmit,
}) {
  const catalogByKey = Object.fromEntries(catalog.map((field) => [field.key, field]));
  if (fieldKeys.length === 0) return null;
  return <form className="idea-question-flow" onSubmit={onSubmit} noValidate>
    <div className="idea-section-heading">
      <p>필수 정보 확인</p>
      <h3>컨셉을 만들기 전에 필요한 정보를 확인해 주세요.</h3>
      <span>답변은 Idea Brief에 반영한 뒤 다시 정리합니다.</span>
    </div>
    <ErrorSummary errors={errors} title="입력이 필요한 항목이 있습니다." />
    <div className="idea-question-grid">{fieldKeys.map((fieldKey) => {
      const definition = catalogByKey[fieldKey];
      if (!definition) return null;
      return <div className="idea-question-card" key={fieldKey}>
        <label htmlFor={`missing-field-${fieldKey}`}><strong>{definition.label}</strong></label>
        <p>{PROMPTS[fieldKey] ?? `${definition.label}에 필요한 사실을 입력해 주세요.`}</p>
        <textarea id={`missing-field-${fieldKey}`} value={fields[fieldKey]?.value ?? ''}
          onChange={(event) => onChange(fieldKey, event.target.value)} />
      </div>;
    })}</div>
    <div className="idea-primary-action idea-primary-action--sticky">
      <Button type="submit">누락 정보 반영하고 다시 정리하기</Button>
    </div>
  </form>;
}
