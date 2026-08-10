import { SAMPLE_SIZES, comparisonTable, formatDuration } from './sampleSize.js';

const TYPE_LABEL = { DOMINANCE: '우열형', PRICE: '가격형' };

/**
 * 표본 크기 선택.
 *
 * 이 컴포넌트의 핵심은 숫자 세 개가 아니라 **각 선택이 무엇을 못 재게 되는지**를
 * 고르는 자리에서 같이 보여주는 것이다. 가격형에서 n=50 을 고르면 웬만한 차이가
 * «못 잼» 으로 끝나는데, 그걸 실행 후에 알게 되면 사용자는 그 결과를
 * «차이 없음» 으로 읽는다 — 있지도 않은 결론이 생긴다.
 */
export default function SampleSizePicker({ pairs = [], value, onChange, disabled = false }) {
  const table = comparisonTable(pairs);

  return (
    <fieldset className="twin-sample" disabled={disabled}>
      <legend>표본 크기</legend>
      <p className="twin-sample__intro">
        표본이 곧 측정 한계다. 아래 «못 재는 최소 차이»보다 작은 차이는 방향을 말할 수 없다.
      </p>

      {table.map((plan) => {
        const selected = plan.sampleSize === value;
        return (
          <label
            key={plan.sampleSize}
            className={`twin-sample__option${selected ? ' is-selected' : ''}`}
          >
            <input
              type="radio"
              name="twin-sample-size"
              value={plan.sampleSize}
              checked={selected}
              onChange={() => onChange?.(plan.sampleSize)}
            />
            <span className="twin-sample__size">{plan.sampleSize}명</span>

            <span className="twin-sample__cost">
              {plan.cells.toLocaleString()}회 응답 · {formatDuration(plan.seconds)}
            </span>

            <span className="twin-sample__mde">
              {plan.rows.length === 0
                ? '자극을 먼저 확정하면 측정 한계를 계산한다'
                : plan.rows.map((row) => (
                  <span key={row.taskType} className="twin-sample__mde-item">
                    {TYPE_LABEL[row.taskType] ?? row.taskType} 못 재는 최소 차이{' '}
                    {row.mde.toFixed(3)}
                  </span>
                ))}
            </span>

            {plan.warnings.map((warning) => (
              <span key={warning} className="twin-sample__warning" role="note">
                {warning}
              </span>
            ))}
          </label>
        );
      })}

      <p className="twin-sample__note">
        응답 수 = 표본 × 자극쌍 × 2방향 × 반복. 양방향 제시는 옵션이 아니라 설계다 —
        한 방향만 물으면 위치편향 때문에 답이 뒤집힌다.
      </p>
    </fieldset>
  );
}

export { SAMPLE_SIZES };
