import { gateSurvey } from './taskTypeGate.js';

const TYPE_VIEW = {
  DOMINANCE: { label: '명백한 우열형', tone: 'success' },
  PRICE: { label: '가격형', tone: 'warning' },
  ETHICAL_VALUE: { label: '윤리·가치형 — 제공하지 않음', tone: 'danger' },
  UNMEASURABLE: { label: '측정 불가', tone: 'danger' },
  IDENTICAL: { label: '두 안이 같음', tone: 'danger' },
};

/**
 * 자극 편집 — 사용자가 두 상품안을 확정하는 자리.
 *
 * 여기서 유형 판정을 **즉시** 보여주는 이유: 팔 수 없는 질문(윤리·가치형, 다속성 경합)을
 * 실행 뒤에 거절하면 사용자는 기다린 뒤에 빈손이 된다. 고치는 방법까지 그 자리에서 말한다.
 *
 * ⚠ 판정의 정본은 서버다(`ai/app/twin/task_type.py`). 이건 거울이라 갈릴 수 있고,
 * 갈리면 서버가 이긴다. 그래서 실행 버튼을 여는 최종 근거로 쓰지 않는다 —
 * 화면은 막고, 서버도 막는다.
 */
export default function StimulusEditor({
  situation = '',
  pairs = [],
  onSituationChange,
  onChange,
  disabled = false,
}) {
  const gate = gateSurvey(pairs);

  const updatePair = (index, next) => {
    onChange?.(pairs.map((pair, cursor) => (cursor === index ? next : pair)));
  };

  const updateAttr = (index, side, name, value) => {
    const pair = pairs[index];
    updatePair(index, {
      ...pair,
      [side]: { ...pair[side], attrs: { ...pair[side].attrs, [name]: value } },
    });
  };

  const updatePrice = (index, side, raw) => {
    const trimmed = String(raw).trim();
    const parsed = trimmed === '' ? null : Number.parseInt(trimmed, 10);
    const pair = pairs[index];
    updatePair(index, {
      ...pair,
      [side]: { ...pair[side], priceKrw: Number.isFinite(parsed) ? parsed : null },
    });
  };

  return (
    <div className="twin-editor">
      <label className="twin-editor__situation">
        <span>상황 문장</span>
        <input
          type="text"
          value={situation}
          disabled={disabled}
          onChange={(event) => onSituationChange?.(event.target.value)}
          placeholder="가게에서 하나를 고릅니다. 아래 두 상품이 있습니다."
        />
      </label>

      {pairs.map((pair, index) => {
        const verdict = gate.verdicts[index];
        const view = TYPE_VIEW[verdict.taskType] ?? { label: verdict.taskType, tone: 'danger' };
        const names = [...new Set([
          ...Object.keys(pair.X?.attrs ?? {}),
          ...Object.keys(pair.Y?.attrs ?? {}),
        ])];

        return (
          <section key={pair.pairId} className="twin-editor__pair">
            <h3>{pair.pairId}</h3>

            <table>
              <thead>
                <tr>
                  <th scope="col">속성</th>
                  <th scope="col">{pair.X?.label ?? 'A안'}</th>
                  <th scope="col">{pair.Y?.label ?? 'B안'}</th>
                </tr>
              </thead>
              <tbody>
                {names.map((name) => {
                  const differs = pair.X?.attrs?.[name] !== pair.Y?.attrs?.[name];
                  return (
                    <tr key={name} className={differs ? 'is-differing' : undefined}>
                      <th scope="row">{name}</th>
                      {['X', 'Y'].map((side) => (
                        <td key={side}>
                          <input
                            type="text"
                            aria-label={`${pair.pairId} ${name} ${side}`}
                            value={pair[side]?.attrs?.[name] ?? ''}
                            disabled={disabled}
                            onChange={(event) => updateAttr(index, side, name, event.target.value)}
                          />
                        </td>
                      ))}
                    </tr>
                  );
                })}
                <tr className={pair.X?.priceKrw !== pair.Y?.priceKrw ? 'is-differing' : undefined}>
                  <th scope="row">가격(원)</th>
                  {['X', 'Y'].map((side) => (
                    <td key={side}>
                      <input
                        type="number"
                        step="1"
                        min="0"
                        aria-label={`${pair.pairId} 가격 ${side}`}
                        value={pair[side]?.priceKrw ?? ''}
                        disabled={disabled}
                        onChange={(event) => updatePrice(index, side, event.target.value)}
                      />
                    </td>
                  ))}
                </tr>
              </tbody>
            </table>

            <p className={`twin-editor__verdict tone-${view.tone}`} role="status">
              <strong>{view.label}</strong> {verdict.reason}
            </p>
          </section>
        );
      })}

      {gate.blocked.length > 0 && (
        <p className="twin-editor__blocked" role="alert">
          팔 수 없는 질문이 {gate.blocked.length}개 있다. 위 이유대로 고쳐야 실행할 수 있다.
        </p>
      )}
      {pairs.length === 0 && (
        <p className="twin-editor__empty">자극 쌍이 없다. 비교할 두 안을 먼저 넣어라.</p>
      )}
    </div>
  );
}
