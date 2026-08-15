import { Button, Textarea, TextInput } from '../../shared/ui';
import Emphasis from './emphasis.jsx';
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
export default function BmPlanForm({ draft, onChange, onSubmit, busy }) {
  const set = (key) => (event) => onChange(key, event.target.value);

  return (
    <form
      className="bm-plan"
      onSubmit={(event) => { event.preventDefault(); onSubmit(); }}
    >
      {PLAN_FIELDS.map(([key, question, cell, hint]) => (
        <div key={key} className="bm-plan__row">
          <div className="bm-plan__q">
            <label htmlFor={`bm-plan-${key}`}>{question}</label>
            {/* 어느 칸을 채우는지 옆에 적는다 — 시스템 어휘로 묻지 않으면서도
                사용자가 결과와 이어 볼 수 있게. */}
            <span className="bm-plan__cell">{cell}</span>
          </div>
          <Textarea
            id={`bm-plan-${key}`}
            rows={LIST_FIELDS.includes(key) ? 4 : 2}
            value={draft[key]}
            onChange={set(key)}
            disabled={busy}
          />
          <p className="bm-plan__hint"><Emphasis text={hint} /></p>
        </div>
      ))}

      <div className="bm-plan__row">
        <div className="bm-plan__q">
          <span className="bm-plan__label">가진 자원은 어느 정도인가요?</span>
          <span className="bm-plan__cell">비용 구조</span>
        </div>
        <div className="bm-plan__nums">
          {CONSTRAINT_FIELDS.map(([key, label, unit, origin]) => (
            <TextInput
              key={key}
              label={`${label} (${unit})`}
              // ⚠ 정수만. 소수는 canonical hash 가 거부한다 — 화면에서 막는 편이
              //   서버 400 을 보는 것보다 낫다.
              type="number"
              min="0"
              step="1"
              inputMode="numeric"
              value={draft[key]}
              onChange={set(key)}
              disabled={busy}
              description={`아이디어 단계의 「${origin}」에 대응한다`}
            />
          ))}
        </div>
        <p className="bm-plan__hint">
          아이디어 단계에서는 문장으로 받았다. 여기서 <strong>숫자로 확정</strong>한다 —
          우리가 문장을 숫자로 추측하면 쓰지 않은 정밀도를 지어내는 것이다.
        </p>
      </div>

      <div className="mr-actions">
        <Button type="submit" disabled={busy}>
          {busy ? '저장 중…' : '저장하고 캔버스 만들기'}
        </Button>
      </div>
    </form>
  );
}
