import { useMemo, useState } from 'react';

import { dedupeTimeline, slotNumberFromEvent } from '../model/conceptFactoryModel.js';

export default function ConceptTimeline({ events, connectionState, onReconnect }) {
  const [filter, setFilter] = useState('all');
  const debugEnabled = import.meta.env.VITE_ENABLE_PIPELINE_DEBUG === 'true';
  const ordered = useMemo(() => dedupeTimeline(events).filter((event) => {
    if (filter === 'all') return true;
    return slotNumberFromEvent(event) === Number(filter);
  }), [events, filter]);
  return <details className="concept-timeline" open={debugEnabled}>
    <summary>실행 상세 보기</summary>
    <div className="concept-timeline__connection" aria-live="polite">
      <span data-state={connectionState}>{connectionLabel(connectionState)}</span>
      {['error', 'stopped'].includes(connectionState) && <button type="button" onClick={onReconnect}>다시 연결</button>}
    </div>
    <label>표시 Slot
      <select value={filter} onChange={(event) => setFilter(event.target.value)}>
        <option value="all">전체</option>
        {[1, 2, 3, 4, 5].map((slot) => <option key={slot} value={slot}>Slot {slot}</option>)}
      </select>
    </label>
    <ol aria-live="polite">
      {ordered.map((event) => <li key={`${event.jobId}-${event.sequence}`}>
        <span>#{event.sequence} {traceHeading(event)}</span>
        <strong>{eventLabel(event.eventType)}</strong>
        {debugEnabled && <TraceDetails event={event} />}
        <time dateTime={event.occurredAt}>{formatTime(event.occurredAt)}</time>
      </li>)}
      {ordered.length === 0 && <li>아직 표시할 진행 기록이 없습니다.</li>}
    </ol>
  </details>;
}

function connectionLabel(state) {
  if (state === 'terminal') return '작업 연결 종료';
  if (state === 'live') return 'SSE 실시간 연결';
  if (state === 'connecting') return 'SSE 연결 중';
  if (state === 'error') return '연결 오류';
  return '연결 대기';
}

function eventLabel(type) {
  return ({
    'job.concept.run.queued': '컨셉 작업 대기', 'job.concept.run.started': '컨셉 작업 시작',
    'job.concept.legal-context.started': '법률 컨텍스트 확인', 'job.concept.legal-context.completed': '법률 컨텍스트 준비',
    'job.concept.slot.started': '후보 생성 시작', 'job.concept.slot.generated': '후보 생성 완료',
    'job.concept.slot.validating_origin': '아이디어 조건 확인', 'job.concept.slot.validating_distinctness': '후보 차별성 확인',
    'job.concept.slot.validating_legal': '법률 근거 확인',
    'job.concept.slot.retrying': '일시 오류 재시도',
    'job.concept.slot.generation_failed': '후보 생성 실패',
    'job.concept.slot.review_failed': '법률 검토 처리 실패',
    'job.concept.slot.redesigning': '필수 통제 반영', 'job.concept.slot.replacing': '대체 후보 생성',
    'job.concept.slot.eligible': '법률검토 통과', 'job.concept.slot.rejected': '후보 폐기',
    'job.concept.run.needs_input': '추가 정보 필요', 'job.concept.run.completed': '5개 컨셉 준비 완료',
    'job.concept.run.failed': '컨셉 작업 실패',
  })[type] ?? '진행 상태 갱신';
}

function TraceDetails({ event }) {
  const params = event?.messageParams ?? event?.safeMessageParams ?? {};
  const details = [
    ['단계', params.phase], ['작업', params.taskType], ['시도', params.attemptNumber],
    ['결과', params.event], ['분류', params.errorClassification],
    ['오류', params.safeErrorCode], ['진단', params.safeReason],
    ['필드', params.failedField], ['재시도 가능', typeof params.retryable === 'boolean'
      ? (params.retryable ? '예' : '아니요') : null],
    ['소요', Number.isFinite(params.durationMs) ? `${params.durationMs}ms` : null],
    ['상관 ID', params.correlationId],
  ].filter(([, value]) => value !== null && value !== undefined && value !== '');
  if (details.length === 0) return null;
  return <dl className="concept-timeline__trace">
    {details.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{String(value)}</dd></div>)}
  </dl>;
}

function traceHeading(event) {
  const params = event?.messageParams ?? event?.safeMessageParams ?? {};
  const slot = params.slotNumber ?? params.slot;
  return slot ? `Slot ${slot}${params.variationFocus ? ` · ${params.variationFocus}` : ''}` : '';
}

function formatTime(value) {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? '' : new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit' }).format(parsed);
}
