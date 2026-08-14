import { Button, Textarea, TextInput } from '../../shared/ui';
import {
  CONSTRAINT_FIELDS, LIST_FIELDS, PLAN_FIELDS,
} from './bmPlan.js';

/**
 * BM 분석이 <b>추가로 필요한 것</b>만 받는다.
 *
 * <p>「입력하세요」가 아니라 「이것만 더 필요합니다」다. 컨셉이 이미 주는 것
 * (수익모델·채널·차별점·가격·SOM·지역·경쟁사)은 <b>여기서 묻지 않는다</b> — 가설 4가
 * 이미 사용자 승인을 거쳤고, 다시 물으면 아이디어 단계에서 친 것을 또 치게 된다.
 *
 * <p>⚠ <b>전부 선택 입력이다.</b> 필수 표시를 달지 않는다. 비운 칸은 캔버스에서 그만큼
 * 비고, 그 사실을 제출 전에 확인받는다 — 모델이 지어내서 메우지 않는다.
 */
export default function BmPlanForm({ draft, onChange, onSubmit, busy, submitLabel = '저장하고 캔버스 만들기' }) {
  const set = (key) => (event) => onChange(key, event.target.value);

  return (
    <form
      className="bm-plan"
      onSubmit={(event) => { event.preventDefault(); onSubmit(); }}
    >
      <div className="bm-plan__workspace">
        <section className="bm-plan__operations" aria-labelledby="bm-plan-operations-title">
          <header><h3 id="bm-plan-operations-title">사업 운영</h3><span>선택 입력</span></header>
          {PLAN_FIELDS.map(([key, question, , hint]) => (
            <div key={key} className="bm-plan__row">
              <div className="bm-plan__q">
                <label htmlFor={`bm-plan-${key}`}>{question}</label>
                <span className="bm-plan__optional">선택</span>
              </div>
              <Textarea
                id={`bm-plan-${key}`}
                rows={LIST_FIELDS.includes(key) ? 3 : 2}
                value={draft[key]}
                onChange={set(key)}
                disabled={busy}
              />
              <p className="bm-plan__hint">{hint}</p>
            </div>
          ))}
        </section>

        <section className="bm-plan__resources" aria-labelledby="bm-plan-resources-title">
          <header><h3 id="bm-plan-resources-title">현재 사용할 수 있는 자원</h3><span>선택 입력</span></header>
          <p>정확히 정해지지 않았다면 비워 두어도 됩니다.</p>
          <div className="bm-plan__nums">
            {CONSTRAINT_FIELDS.map(([key, label, unit]) => (
              <TextInput
                key={key}
                label={`${label} (${unit})`}
                type="number"
                min="0"
                step="1"
                inputMode="numeric"
                value={draft[key]}
                onChange={set(key)}
                disabled={busy}
              />
            ))}
          </div>
        </section>
      </div>

      <div className="mr-actions">
        <Button type="submit" disabled={busy}>
          {busy ? '저장 중…' : submitLabel}
        </Button>
      </div>
    </form>
  );
}
