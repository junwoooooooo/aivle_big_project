import { SAMPLE_SIZES, formatDuration, planFor } from './sampleSize.js';

/**
 * 응답자 수.
 *
 * **슬라이더지만 3단으로 스냅한다.** 연속 값을 받지 않는 이유는 UI 취향이 아니라 계약이다 —
 * 서버(`MarketInterviewService`)와 AI(`models.py` 의 `SampleSize`)와 DB CHECK 가 20·40·80 만
 * 받는다. 그래서 슬라이더 값은 «인덱스»이고 값이 아니다.
 */
export default function SampleSizePicker({ value, onChange, disabled = false }) {
  const index = Math.max(0, SAMPLE_SIZES.indexOf(value));
  const plan = planFor(SAMPLE_SIZES[index]);

  return (
    <div className="mi-sample">
      <label className="mi-sample__label" htmlFor="mi-sample-size">응답자 수</label>

      <output className="mi-sample__value" htmlFor="mi-sample-size">
        {SAMPLE_SIZES[index]}명
      </output>

      <input
        id="mi-sample-size"
        className="mi-sample__slider"
        type="range"
        min={0}
        max={SAMPLE_SIZES.length - 1}
        step={1}
        value={index}
        disabled={disabled}
        onChange={(event) => onChange?.(SAMPLE_SIZES[Number(event.target.value)])}
        aria-valuetext={`${SAMPLE_SIZES[index]}명`}
      />

      <div className="mi-sample__ticks" aria-hidden="true">
        {SAMPLE_SIZES.map((size) => <span key={size}>{size}</span>)}
      </div>

      <p className="mi-sample__cost">
        {plan.cells}명에게 9문항 · {formatDuration(plan.seconds)}
      </p>

      {plan.notes.map((note) => (
        <p key={note} className="mi-sample__note" role="note">{note}</p>
      ))}

      <p className="mi-sample__hint">
        한 사람에게 한 번 묻는다. 결과의 분모는 뽑은 사람 수가 아니라 <strong>답한 사람 수</strong>다 —
        형식을 어긴 응답은 분모에서 빠지고 그 수를 결과에 적는다.
      </p>
    </div>
  );
}

export { SAMPLE_SIZES };
