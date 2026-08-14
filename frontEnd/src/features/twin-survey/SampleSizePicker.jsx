import { SAMPLE_SIZES, formatDuration, planFor } from './sampleSize.js';

/**
 * 가상 페르소나 수.
 *
 * **슬라이더지만 3단으로 스냅한다.** 연속 값을 받지 않는 이유는 UI 취향이 아니라 측정이다 —
 * 서버(`TwinSurveyService.SAMPLE_SIZES`)와 AI(`models.py` 의 `SampleSize`)가 50·100·300 만
 * 받고, MDE 표(`sampleSize.js`)도 그 셋으로만 실측돼 있다. 표에 없는 n 을 허용하면 화면이
 * **재본 적 없는 측정 한계**로 답하게 된다. 그래서 슬라이더 값은 «인덱스»이고 값이 아니다.
 *
 * 이전에는 세 선택지의 MDE 설명을 항상 다 펼쳐 뒀는데, 그 문단들이 화면을 채우는 동안 정작
 * 「지금 몇 명인가」는 안 보였다. 이제 **고른 값 한 줄**만 보이고, 측정 한계 이야기는
 * 그 표본으로 못 잴 위험이 있을 때만 나온다.
 */
export default function SampleSizePicker({ pairs = [], value, onChange, disabled = false }) {
  const index = Math.max(0, SAMPLE_SIZES.indexOf(value));
  const plan = planFor(SAMPLE_SIZES[index], pairs);

  return (
    <div className="twin-sample">
      <label className="twin-sample__label" htmlFor="twin-sample-size">
        가상 페르소나 수
      </label>

      <output className="twin-sample__value" htmlFor="twin-sample-size">
        {SAMPLE_SIZES[index].toLocaleString()}명
      </output>

      <input
        id="twin-sample-size"
        className="twin-sample__slider"
        type="range"
        min={0}
        max={SAMPLE_SIZES.length - 1}
        step={1}
        value={index}
        disabled={disabled}
        onChange={(event) => onChange?.(SAMPLE_SIZES[Number(event.target.value)])}
        aria-valuetext={`${SAMPLE_SIZES[index]}명`}
      />

      <div className="twin-sample__ticks" aria-hidden="true">
        {SAMPLE_SIZES.map((size) => <span key={size}>{size}</span>)}
      </div>

      <p className="twin-sample__cost">
        {plan.cells > 0
          ? `${plan.cells.toLocaleString()}회 응답 · ${formatDuration(plan.seconds)}`
          : '비교안을 먼저 정하면 응답 수와 시간을 계산한다'}
      </p>

      {/* ⚠ 표본이 부족할 때만 말한다. 늘 떠 있으면 읽히지 않고, 정작 «못 잼»으로 끝난 뒤에
          사용자는 그 결과를 «차이 없음»으로 읽는다 — 있지도 않은 결론이 생긴다. */}
      {plan.warnings.map((warning) => (
        <p key={warning} className="twin-sample__warning" role="note">{warning}</p>
      ))}

      <p className="twin-sample__note">
        응답 수 = 표본 × 자극쌍 × 2방향 × 반복. 양방향 제시는 옵션이 아니라 설계다 —
        한 방향만 물으면 위치편향 때문에 답이 뒤집힌다.
      </p>
    </div>
  );
}

export { SAMPLE_SIZES };
