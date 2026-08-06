import { Badge } from '../../../shared/ui/index.js';

export default function PersonaChoiceCards({ personas, selectedIds, onChange, max = 3, disabled = false }) {
  function toggle(personaId) {
    if (disabled) return;
    if (selectedIds.includes(personaId)) {
      onChange(selectedIds.filter((id) => id !== personaId));
    } else if (selectedIds.length < max) {
      onChange([...selectedIds, personaId]);
    }
  }
  return (
    <fieldset className="validation-personas" disabled={disabled}>
      <legend>대상 Persona <span>{selectedIds.length}/{max}</span></legend>
      <p>추천, 프로젝트 선택, 운영자가 허용한 Persona 중 최대 {max}개를 선택합니다.</p>
      <div className="validation-persona-grid">
        {personas.map((persona) => {
          const selected = selectedIds.includes(persona.id);
          return (
            <label key={persona.id} className={`validation-persona-card ${selected ? 'is-selected' : ''}`}>
              <input type="checkbox" aria-label={persona.name} checked={selected} onChange={() => toggle(persona.id)} />
              <span className="validation-persona-card__avatar" aria-hidden="true">{persona.name.slice(0, 1)}</span>
              <span><strong>{persona.name}</strong><small>{persona.summary}</small></span>
              <span className="validation-persona-card__badges">
                {persona.recommended && <Badge tone="info">추천</Badge>}
                {persona.selected && <Badge tone="success">프로젝트 선택</Badge>}
                {selected && <Badge tone="neutral">대상</Badge>}
              </span>
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}
