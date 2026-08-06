import { Button, Textarea, TextInput } from '../../../shared/ui/index.js';

function Count({ value, recommended }) {
  return <small>{value.length}자 · 권장 {recommended}</small>;
}

export default function MarketingCopyEditor({ draft, onChange, onAlternative, generating }) {
  const set = (field) => (event) => onChange({ ...draft, [field]: event.target.value });
  return (
    <section className="marketing-panel">
      <div className="marketing-panel__heading">
        <div><h2>카피 편집</h2><p>검증 결과 기반 초안이며 직접 수정할 수 있습니다.</p></div>
        <Button variant="outline" size="small" loading={generating} onClick={onAlternative}>다른 초안</Button>
      </div>
      <TextInput label="Headline" value={draft.headline} maxLength="160" onChange={set('headline')} />
      <Count value={draft.headline} recommended="20~45자" />
      <Textarea label="Subheadline" value={draft.subheadline} maxLength="240" onChange={set('subheadline')} />
      <Count value={draft.subheadline} recommended="35~80자" />
      <Textarea label="본문" value={draft.bodyCopy} maxLength="2000" onChange={set('bodyCopy')} />
      <Count value={draft.bodyCopy} recommended="80~240자" />
      <TextInput label="CTA" value={draft.callToAction} maxLength="80" onChange={set('callToAction')} />
      <TextInput label="보조 문구·해시태그" value={draft.supportingText} maxLength="240" onChange={set('supportingText')} />
    </section>
  );
}
