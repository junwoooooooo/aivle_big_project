import { CONTENT_TYPES, LENGTHS, setupIsValid } from '../model/marketingContentModel.js';

const Field = ({ label, children, hint }) => <label className="mk-field"><span>{label}</span>{children}{hint && <small>{hint}</small>}</label>;
export default function MarketingSetupPanel({ value, onChange, onSubmit, disabled, busy }) {
  const set = (key) => (event) => onChange({ ...value, [key]: event.target.value });
  return <section className="mk-panel mk-setup" aria-labelledby="mk-setup-title">
    <div className="mk-panel__heading"><div><p>생성 설정</p><h2 id="mk-setup-title">어떤 콘텐츠를 만들까요?</h2></div></div>
    <Field label="콘텐츠 유형"><select value={value.contentType} onChange={set('contentType')}>{CONTENT_TYPES.map(([id,label])=><option key={id} value={id}>{label}</option>)}</select></Field>
    <Field label="채널"><input value={value.channel} onChange={set('channel')} placeholder="예: Instagram, 자사몰" /></Field>
    <Field label="목적"><input value={value.purpose} onChange={set('purpose')} placeholder="예: 출시 인지도 확보" /></Field>
    <Field label="톤"><input value={value.tone} onChange={set('tone')} /></Field>
    <Field label="길이"><select value={value.length} onChange={set('length')}>{LENGTHS.map(([id,label])=><option key={id} value={id}>{label}</option>)}</select></Field>
    <Field label="CTA"><input value={value.callToAction} onChange={set('callToAction')} placeholder="예: 지금 자세히 보기" /></Field>
    <Field label="포함 문구" hint="쉼표 또는 줄바꿈으로 구분"><textarea value={value.requiredPhrases} onChange={set('requiredPhrases')} rows="3" /></Field>
    <Field label="제외 문구"><textarea value={value.excludedPhrases} onChange={set('excludedPhrases')} rows="3" /></Field>
    <Field label="추가 요청"><textarea value={value.additionalInstruction} onChange={set('additionalInstruction')} rows="4" /></Field>
    <button className="mk-primary" type="button" disabled={disabled || busy || !setupIsValid(value)} onClick={onSubmit}>{busy ? '생성 요청 중…' : '콘텐츠 생성'}</button>
  </section>;
}
