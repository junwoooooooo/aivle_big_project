import { useState } from 'react';

import { Alert, Button, Card } from '../../shared/ui/index.js';

function PersonaCard({ persona, saving, blocked, onSelect }) {
  return (
    <Card className={[
      'available-persona-card',
      persona.recommended ? 'is-recommended' : '',
      persona.selected ? 'is-selected' : '',
    ].filter(Boolean).join(' ')}
    >
      <div className="available-persona-card__heading">
        <span className="available-persona-card__avatar" aria-hidden="true">
          {persona.name.slice(0, 1)}
        </span>
        <div>
          <h3>{persona.name}</h3>
          <div className="available-persona-card__badges">
            {persona.recommended && <span>추천</span>}
            {persona.selected && <strong>선택됨</strong>}
          </div>
        </div>
      </div>
      <p>{persona.summary}</p>
      <ul aria-label={`${persona.name} 대표 키워드`}>
        {persona.keywords.slice(0, 3).map((keyword) => <li key={keyword}>{keyword}</li>)}
      </ul>
      <Button
        size="small"
        variant={persona.selected ? 'outline' : 'primary'}
        disabled={blocked || persona.selected || saving}
        loading={saving}
        onClick={() => onSelect(persona.id)}
      >
        {persona.selected ? '선택됨' : '이 Persona 선택'}
      </Button>
    </Card>
  );
}

export default function AvailablePersonaSection({
  state,
  blocked,
  blockedReason,
}) {
  const [expanded, setExpanded] = useState(false);
  if (state.loading && !state.data) {
    return <section className="available-personas" aria-busy="true"><p>사용 가능한 페르소나를 확인하고 있습니다.</p></section>;
  }
  const items = state.data?.items || [];
  const visible = expanded ? items : items.slice(0, 3);
  return (
    <section className="available-personas" aria-labelledby="available-personas-title">
      <div className="persona-section-heading">
        <div>
          <p className="persona-kicker">프로젝트 선택</p>
          <h2 id="available-personas-title">사용 가능한 페르소나</h2>
        </div>
        <span>{items.length}개</span>
      </div>
      <p>
        추천 결과와 함께 다른 사용자 유형을 비교하고 프로젝트에 사용할 대표
        페르소나를 선택할 수 있습니다.
      </p>
      {blocked && <Alert tone="warning" title="선택을 변경할 수 없습니다">{blockedReason}</Alert>}
      {state.error && (
        <Alert tone="danger" title="추가 페르소나를 불러오거나 선택하지 못했습니다">
          {state.error.message}
          <Button size="small" variant="outline" onClick={() => void state.refresh()}>다시 시도</Button>
        </Alert>
      )}
      {state.data?.selectedUnavailable && (
        <Alert tone="warning" title="현재 선택한 Persona는 사용 중지되었습니다">
          {state.data.selectedUnavailable.name} 선택 기록은 보존됩니다. 관리자가 다시
          허용하기 전에는 새 선택 후보로 사용할 수 없습니다.
        </Alert>
      )}
      {items.length === 0 ? (
        <Card>
          <h3>현재 사용할 수 있는 추가 페르소나가 없습니다.</h3>
          <p>추천 페르소나를 계속 사용할 수 있습니다.</p>
        </Card>
      ) : (
        <>
          <div className="available-persona-grid">
            {visible.map((persona) => (
              <PersonaCard
                key={persona.id}
                persona={persona}
                saving={state.savingId === persona.id}
                blocked={blocked || state.savingId != null}
                onSelect={state.select}
              />
            ))}
          </div>
          {items.length > 3 && (
            <Button variant="ghost" onClick={() => setExpanded((value) => !value)}>
              {expanded ? '접기' : `다른 페르소나 보기 (${items.length - 3})`}
            </Button>
          )}
        </>
      )}
    </section>
  );
}
