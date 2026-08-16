import { useState } from 'react';

import { Button, Textarea, TextInput } from '../../shared/ui';
import { CONSTRAINT_FIELDS, LIST_FIELDS, PLAN_FIELDS } from './bmPlan.js';

const DETAILS = Object.freeze({
  customer_relationship: { why: '고객이 서비스를 계속 이용하도록 관계를 유지하는 방식을 구체화할 때 사용합니다.', example: '예: 예약 알림, 정기 안내, 고객 지원, 재구매 혜택' },
  key_activities: { why: '사업 모델 캔버스에서 반복적으로 수행할 핵심 활동을 정리할 때 사용합니다.', example: '예: 매장 등록 검수, 예약 운영, 고객 문의 처리' },
  key_resources: { why: '서비스 운영에 반드시 필요한 시스템·데이터·인력을 정리할 때 사용합니다.', example: '예: 예약 관리 시스템, 매장 데이터, 운영 담당자' },
  key_partners: { why: '외부 협력 없이는 수행하기 어려운 역할과 자격을 정리할 때 사용합니다.', example: '예: 결제 대행사, 물류 파트너, 전문 자격 보유 업체' },
});

const reviewLines = (value) => String(value ?? '').split(/\r?\n/).map((item) => item.trim()).filter(Boolean);

export function BmPlanReview({ draft }) {
  return <section className="bm-plan-review" aria-labelledby="bm-plan-review-title">
    <header><h3 id="bm-plan-review-title">저장한 운영 정보</h3><p>사업 검증을 준비할 때 저장한 내용을 조회하고 있습니다.</p></header>
    <div className="bm-plan-review__operations">{PLAN_FIELDS.map(([key, question]) => {
      const values = reviewLines(draft[key]);
      return <article key={key}><strong>{question}</strong>{values.length === 0
        ? <p data-empty="true">입력하지 않음</p>
        : LIST_FIELDS.includes(key) ? <ul>{values.map((value) => <li key={value}>{value}</li>)}</ul> : <p>{values[0]}</p>}</article>;
    })}</div>
    <section className="bm-plan-review__resources"><h3>저장한 자원 제약</h3><dl>{CONSTRAINT_FIELDS.map(([key, label, unit]) => <div key={key}><dt>{label}</dt><dd data-empty={!String(draft[key] ?? '').trim()}>{String(draft[key] ?? '').trim() ? `${draft[key]} ${unit}` : '입력하지 않음'}</dd></div>)}</dl></section>
  </section>;
}

export default function BmPlanForm({ draft, suggestions = {}, onChange, onSubmit, busy, formId, showSubmit = true, submitLabel = '저장하고 캔버스 만들기' }) {
  const [editing, setEditing] = useState({});
  const set = (key) => (event) => onChange(key, event.target.value);
  const setEditor = (key, open) => setEditing((current) => ({ ...current, [key]: open }));

  return <form id={formId} className="bm-plan" onSubmit={(event) => { event.preventDefault(); onSubmit(); }}>
    <p className="bm-plan__optional-all">모든 항목은 선택 입력입니다. 지금 알고 있는 내용만 준비해도 됩니다.</p>
    <div className="bm-plan__workspace">
      <section className="bm-plan__operations" aria-labelledby="bm-plan-operations-title">
        <header><h3 id="bm-plan-operations-title">사업 운영</h3></header>
        {PLAN_FIELDS.map(([key, question]) => {
          const current = String(draft[key] ?? '').trim();
          const suggestion = !current ? suggestions[key] : '';
          const open = Boolean(editing[key]);
          const details = DETAILS[key];
          return <article key={key} className="bm-plan__row" data-editing={open}>
            <header><div>{open ? <label htmlFor={`bm-plan-${key}`}>{question}</label> : <strong>{question}</strong>}<p>{details.why}</p><small>{details.example}</small></div><button type="button" className="bm-plan__text-action" disabled={busy} onClick={() => setEditor(key, !open)}>{open ? '입력 닫기' : current ? '수정' : '직접 입력'}</button></header>
            {current && !open && <div className="bm-plan__read"><span>현재값</span><p>{current}</p></div>}
            {suggestion && !open && <div className="bm-plan__suggestion"><span>선택한 사업안에서 가져온 초안</span><ul>{suggestion.split(/\r?\n/).map((item) => item.trim()).filter(Boolean).map((item) => <li key={item}>{item}</li>)}</ul><button type="button" disabled={busy} onClick={() => onChange(key, suggestion)}>이 내용 사용</button></div>}
            {!current && !suggestion && !open && <p className="bm-plan__empty">아직 입력하지 않았습니다.</p>}
            {open && <div className="bm-plan__editor"><Textarea id={`bm-plan-${key}`} rows={LIST_FIELDS.includes(key) ? 4 : 3} value={draft[key]} onChange={set(key)} disabled={busy} /></div>}
          </article>;
        })}
      </section>

      <section className="bm-plan__resources" aria-labelledby="bm-plan-resources-title">
        <header><h3 id="bm-plan-resources-title">현재 사용할 수 있는 자원</h3></header>
        <p>정확히 정해진 값만 입력하세요.</p>
        <div className="bm-plan__nums">{CONSTRAINT_FIELDS.map(([key, label, unit]) => <TextInput key={key} label={`${label} (${unit})`} type="number" min="0" step="1" inputMode="numeric" value={draft[key]} onChange={set(key)} disabled={busy} />)}</div>
      </section>
    </div>
    {showSubmit && <div className="mr-actions"><Button type="submit" disabled={busy}>{busy ? '저장 중…' : submitLabel}</Button></div>}
  </form>;
}
