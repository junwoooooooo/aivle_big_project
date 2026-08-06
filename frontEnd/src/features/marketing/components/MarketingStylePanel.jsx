import { Checkbox, Select, TextInput } from '../../../shared/ui/index.js';
import { STYLE_PRESETS, TEMPLATES, TONES } from '../model/marketingPresets.js';

function Options({ values }) {
  return values.map(([value, label]) => <option key={value} value={value}>{label}</option>);
}

export default function MarketingStylePanel({
  draft,
  onChange,
  recommendedPresetIds = [],
}) {
  const orderedPresets = [
    ...recommendedPresetIds
      .map((id) => STYLE_PRESETS.find((preset) => preset.id === id))
      .filter(Boolean),
    ...STYLE_PRESETS.filter((preset) => !recommendedPresetIds.includes(preset.id)),
  ];
  const set = (field) => (event) => onChange({
    ...draft,
    [field]: event.target.type === 'checkbox' ? event.target.checked : event.target.value,
  });
  return (
    <section className="marketing-panel">
      <h2>디자인</h2>
      <div className="marketing-presets" aria-label="추천 디자인 Preset">
        {orderedPresets.map((preset, index) => (
          <button key={preset.id} type="button" onClick={() => onChange({ ...draft, ...preset })}>
            <span className={`marketing-preset-swatch marketing-preset-swatch--${preset.id.toLowerCase()}`} aria-hidden="true" />
            {preset.label}
            {index < recommendedPresetIds.length && <small>검증 결과 추천</small>}
          </button>
        ))}
      </div>
      <Select label="템플릿" value={draft.layoutTemplate} onChange={set('layoutTemplate')}><Options values={TEMPLATES} /></Select>
      <Select label="톤앤매너" value={draft.visualStyle} onChange={set('visualStyle')}><Options values={TONES} /></Select>
      <Select label="배경 유형" value={draft.backgroundType} onChange={set('backgroundType')}>
        <option value="SOLID">단색</option>
        <option value="GRADIENT">Gradient</option>
        <option value="PATTERN">추상 Pattern</option>
      </Select>
      {draft.backgroundType === 'GRADIENT' ? (
        <TextInput label="Gradient 색상" description="#RRGGBB,#RRGGBB" value={draft.backgroundValue} onChange={set('backgroundValue')} />
      ) : (
        <TextInput label="배경색" type="color" value={/^#[\da-f]{6}$/i.test(draft.backgroundValue) ? draft.backgroundValue : '#17363a'} onChange={set('backgroundValue')} />
      )}
      <div className="marketing-field-row">
        <TextInput label="Accent" type="color" value={draft.accentColor} onChange={set('accentColor')} />
        <TextInput label="텍스트" type="color" value={draft.textColor} onChange={set('textColor')} />
      </div>
      <Select label="텍스트 정렬" value={draft.textAlignment} onChange={set('textAlignment')}>
        <option value="LEFT">왼쪽</option><option value="CENTER">가운데</option><option value="RIGHT">오른쪽</option>
      </Select>
      <TextInput label="Headline 크기" type="range" min="28" max="180" value={draft.headlineSize} onChange={set('headlineSize')} />
      <Checkbox label="CTA 표시" checked={draft.showCta} onChange={set('showCta')} />
      <Checkbox label="Persona Tag 표시" checked={draft.showPersonaTag} onChange={set('showPersonaTag')} />
    </section>
  );
}
