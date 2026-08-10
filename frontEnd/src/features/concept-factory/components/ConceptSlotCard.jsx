import { SLOT_STATUS_COPY, VARIATION_LABELS } from '../model/conceptFactoryModel.js';
import { formatLocalTime } from '../../../shared/async-events/formatLocalTime.js';

export default function ConceptSlotCard({ slot }) {
  const ready = slot.status === 'ELIGIBLE';
  return <article className="concept-slot" aria-label={`컨셉 ${slot.slotNumber}, ${VARIATION_LABELS[slot.variationFocus]}, ${slot.status}`}>
    <header><span>컨셉 {slot.slotNumber}</span><strong>{VARIATION_LABELS[slot.variationFocus]}</strong></header>
    <div className="concept-slot__status" data-status={slot.status}>
      <span aria-hidden="true" />{ready ? '컨셉 준비됨 · 법률검토 통과' : slot.status}
    </div>
    <p>{SLOT_STATUS_COPY[slot.status] ?? '진행 상태를 확인하고 있습니다.'}</p>
    <dl>
      <div><dt>후보 생성 횟수</dt><dd>{slot.candidateCount ?? 0}</dd></div>
      <div><dt>법률 검토 상태</dt><dd>{slot.status === 'REVIEW_RETRY_PENDING' ? '다시 시도 필요' : slot.status}</dd></div>
      <div><dt>재설계 횟수</dt><dd>{slot.legalRedesignCount ?? 0}</dd></div>
      <div><dt>최근 갱신</dt><dd><time dateTime={slot.updatedAt}>{formatLocalTime(slot.updatedAt) || '아직 없음'}</time></dd></div>
    </dl>
  </article>;
}
