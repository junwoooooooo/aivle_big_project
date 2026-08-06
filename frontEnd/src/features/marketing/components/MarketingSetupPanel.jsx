import { Select, Textarea, TextInput } from '../../../shared/ui/index.js';
import { CHANNELS, FORMATS, PURPOSES, TEMPLATES, TONES } from '../model/marketingPresets.js';

function Options({ values }) {
  return values.map(([value, label]) => <option key={value} value={value}>{label}</option>);
}

export default function MarketingSetupPanel({ value, onChange, mode = 'create' }) {
  const set = (field) => (event) => onChange({ ...value, [field]: event.target.value });
  const custom = value.format === 'CUSTOM';
  return (
    <section className="marketing-panel">
      <h2>기본 설정</h2>
      <TextInput label="콘텐츠 제목" required value={value.title} onChange={set('title')} />
      <Select label="목적" required value={value.purpose} onChange={set('purpose')}><Options values={PURPOSES} /></Select>
      <Select label="채널" required value={value.channel} onChange={set('channel')}><Options values={CHANNELS} /></Select>
      <Select label="규격" required value={value.format} onChange={set('format')}><Options values={FORMATS} /></Select>
      {custom && (
        <div className="marketing-field-row">
          <TextInput label="너비(px)" type="number" min="320" max="4096" value={value.width} onChange={set('width')} />
          <TextInput label="높이(px)" type="number" min="320" max="4096" value={value.height} onChange={set('height')} />
        </div>
      )}
      <Select label="톤앤매너" value={value.tone ?? value.visualStyle} onChange={set(mode === 'create' ? 'tone' : 'visualStyle')}><Options values={TONES} /></Select>
      <Select label="템플릿" value={value.template ?? value.layoutTemplate} onChange={set(mode === 'create' ? 'template' : 'layoutTemplate')}><Options values={TEMPLATES} /></Select>
      {mode === 'create' && (
        <>
          <TextInput label="핵심 홍보 대상" required value={value.targetOffer} onChange={set('targetOffer')} />
          <Textarea label="강조하고 싶은 메시지" value={value.emphasisMessage} onChange={set('emphasisMessage')} />
          <details>
            <summary>고급 메시지 설정</summary>
            <TextInput label="브랜드명" value={value.brandName} onChange={set('brandName')} />
            <TextInput label="브랜드 컬러" type="color" value={value.brandColor} onChange={set('brandColor')} />
            <TextInput label="CTA" value={value.callToAction} onChange={set('callToAction')} />
            <Textarea label="반드시 포함할 문구" value={value.requiredText} onChange={set('requiredText')} />
            <Textarea label="피해야 할 문구" value={value.avoidedText} onChange={set('avoidedText')} />
            <p className="marketing-help">로고·배경 이미지 업로드는 안전한 Asset 저장·검증 계약 연결 후 제공됩니다.</p>
          </details>
        </>
      )}
    </section>
  );
}
