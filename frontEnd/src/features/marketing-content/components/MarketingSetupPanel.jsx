import { cloneElement } from 'react';

import { FileDropzone, ProjectFormRow } from '../../../shared/ui/index.js';
import { CONTENT_TYPES, LENGTHS, setupIsValid } from '../model/marketingContentModel.js';

const Field = ({ label, children, hint }) => <ProjectFormRow label={label} description={hint}>{(fieldProps) => cloneElement(children, fieldProps)}</ProjectFormRow>;
export default function MarketingSetupPanel({ value, onChange, onSubmit, disabled, busy }) {
  const set = (key) => (event) => onChange({ ...value, [key]: event.target.value });
  const setImage = (files) => onChange({ ...value, referenceImage: files[0] ?? null });
  return <section className="mk-panel mk-setup" aria-labelledby="mk-setup-title">
    <div className="mk-panel__heading"><div><p>생성 설정</p><h2 id="mk-setup-title">어떤 콘텐츠를 만들까요?</h2></div></div>
    <FileDropzone label="이미지 선택" description="참고 상품 이미지를 끌어 놓거나 선택하세요" acceptLabel="PNG 또는 JPG · 최대 20MB" accept="image/png,image/jpeg" files={value.referenceImage ? [value.referenceImage] : []} onFilesChange={setImage} disabled={busy} />
    <div className="project-form-layout">
    <Field label="콘텐츠 유형"><select value={value.contentType} onChange={set('contentType')}>{CONTENT_TYPES.map(([id,label])=><option key={id} value={id}>{label}</option>)}</select></Field>
    <Field label="채널"><input value={value.channel} onChange={set('channel')} placeholder="예: Instagram, 자사몰" /></Field>
    <Field label="목적"><input value={value.purpose} onChange={set('purpose')} placeholder="예: 출시 인지도 확보" /></Field>
    <Field label="톤"><input value={value.tone} onChange={set('tone')} /></Field>
    <Field label="길이"><select value={value.length} onChange={set('length')}>{LENGTHS.map(([id,label])=><option key={id} value={id}>{label}</option>)}</select></Field>
    <Field label="CTA"><input value={value.callToAction} onChange={set('callToAction')} placeholder="예: 지금 자세히 보기" /></Field>
    <Field label="포함 문구" hint="쉼표 또는 줄바꿈으로 구분"><textarea value={value.requiredPhrases} onChange={set('requiredPhrases')} rows="3" /></Field>
    <Field label="제외 문구"><textarea value={value.excludedPhrases} onChange={set('excludedPhrases')} rows="3" /></Field>
    <Field label="추가 요청"><textarea value={value.additionalInstruction} onChange={set('additionalInstruction')} rows="4" /></Field>
    </div>
    <button className="mk-primary" type="button" disabled={disabled || busy || !setupIsValid(value)} onClick={onSubmit}>{busy ? '초안 요청 중…' : '마케팅 초안 만들기'}</button>
  </section>;
}
