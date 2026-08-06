import { useId, useState } from 'react';

import { JobTimeline } from '../../shared/async-events/index.js';
import { eventsForSlot, FOCUS_LABELS, SLOT_MESSAGES } from './conceptWorkboardModel.js';

export function ConceptSlotCard({ slot, events }) {
  const [expanded, setExpanded] = useState(false);
  const timelineId = useId();
  const slotEvents = eventsForSlot(events, slot.slotIndex);
  return <article className={`concept-slot concept-slot--${slot.status.toLowerCase()}`} tabIndex="0"
    aria-label={`Slot ${slot.slotIndex + 1}, ${FOCUS_LABELS[slot.variationFocus] || slot.variationFocus}, ${SLOT_MESSAGES[slot.status] || '상태 업데이트'}`}>
    <header>
      <span>Slot {slot.slotIndex + 1}</span>
      <h3>{FOCUS_LABELS[slot.variationFocus] || slot.variationFocus}</h3>
      <strong>{SLOT_MESSAGES[slot.status] || '상태가 업데이트되었습니다.'}</strong>
    </header>
    <dl>
      <div><dt>현재 단계</dt><dd>{phaseLabel(slot.currentPhase)}</dd></div>
      <div><dt>시도 횟수</dt><dd>{slot.attemptCount}</dd></div>
      {slot.legalState && <div><dt>구현 상태</dt><dd>{legalLabel(slot.legalState)}</dd></div>}
      <div><dt>마지막 갱신</dt><dd>{formatTime(slot.updatedAt)}</dd></div>
    </dl>
    <div className="concept-slot__live" aria-live="polite">{SLOT_MESSAGES[slot.status]}</div>
    <button type="button" aria-expanded={expanded} aria-controls={timelineId}
      onClick={() => setExpanded((value) => !value)}>
      Slot Timeline {expanded ? '접기' : '펼치기'}
    </button>
    {expanded && <div id={timelineId}><JobTimeline events={slotEvents} title={`Slot ${slot.slotIndex + 1} 진행 기록`} /></div>}
  </article>;
}

function phaseLabel(value) {
  return ({ INITIAL: '초기 생성', REPAIR: '구조 재정리', REDESIGN: '역할 재설계', REPLACEMENT: '후보 교체' })[value] || value;
}
function legalLabel(value) {
  return ({ IMPLEMENTABLE: '현재 구조로 구현 가능', IMPLEMENTABLE_WITH_CONTROLS: '필수 통제 적용 시 구현 가능' })[value] || '내부 검증 중';
}
function formatTime(value) {
  if (!value) return '기록 전';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '기록 전' : date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}
