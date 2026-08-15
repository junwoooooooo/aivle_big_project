import { useState } from 'react';

import { Button, Dialog } from '../../shared/ui';
import { classifyPair } from './taskTypeGate.js';

const TYPE_VIEW = {
  DOMINANCE: { label: '명백한 우열형', tone: 'success' },
  PRICE: { label: '가격형 — 제공하지 않음', tone: 'danger' },
  ETHICAL_VALUE: { label: '윤리·가치형 — 제공하지 않음', tone: 'danger' },
  UNMEASURABLE: { label: '측정 불가', tone: 'danger' },
  IDENTICAL: { label: '두 안이 같음', tone: 'danger' },
};

/** 한 쌍이 곧 «하나의 질문»이다 — 축 이름 하나와 그 축의 두 값. */
function readPair(pair) {
  const axis = Object.keys(pair?.X?.attrs ?? {})[0]
    ?? Object.keys(pair?.Y?.attrs ?? {})[0] ?? '';
  return {
    axis,
    xLabel: pair?.X?.label ?? 'A안',
    yLabel: pair?.Y?.label ?? 'B안',
    xValue: pair?.X?.attrs?.[axis] ?? '',
    yValue: pair?.Y?.attrs?.[axis] ?? '',
  };
}

/**
 * 편집 결과를 서버 계약 모양으로 되돌린다.
 *
 * ⚠ **가격은 손대지 않는다.** 편집기에서 뺐을 뿐 데이터는 그대로 흘러야 한다 —
 * 양쪽이 같은 값이라 판정에 영향이 없고, 임의로 null 로 만들면 자극 문장에서 가격이 사라진다.
 */
function writePair(pair, form) {
  const axis = form.axis.trim();
  return {
    ...pair,
    X: { ...pair.X, label: form.xLabel.trim(), attrs: { [axis]: form.xValue.trim() } },
    Y: { ...pair.Y, label: form.yLabel.trim(), attrs: { [axis]: form.yValue.trim() } },
  };
}

/**
 * 비교안 한 쌍을 고치는 창.
 *
 * 창으로 뗀 이유는 화면이 아래로 늘어나지 않게 하려는 것만이 아니다. **한 번에 한 질문에만
 * 집중하게 하려는 것**이다 — 목록에서는 「무엇과 무엇을 비교하는가」만 보이고, 고치는 동안에는
 * 그 쌍의 유형 판정이 바로 옆에 붙는다.
 *
 * ⚠ **축 이름을 고칠 수 있다.** 이전 편집기는 속성 이름이 고정이라 「형태」 말고 다른 축으로
 * 물을 방법이 아예 없었다. 축을 바꾸면 양쪽 `attrs` 의 키가 함께 바뀐다 — 키가 갈리면
 * 서버가 «같은 속성 공간이 아니다»로 거절한다(`models.py` 의 `same_attribute_space`).
 *
 * 판정의 정본은 서버다(`ai/app/twin/task_type.py`). 여기 보이는 것은 거울이고,
 * 갈리면 서버가 이긴다 — 그래서 막힌 유형도 **저장은 된다**. 실행을 막는 것은 목록 쪽 게이트다.
 */
export default function PairEditorDialog({ open, pair, onSave, onClose }) {
  // ⚠ 다른 쌍을 열 때 값을 갈아 끼우는 일은 **effect 가 아니라 `key` 로** 한다.
  //    호출부가 `key={editing}` 을 주면 쌍이 바뀔 때 이 컴포넌트가 다시 마운트되고
  //    아래 초기화가 다시 돈다. effect 안에서 setState 하면 렌더가 연쇄되고, 이 저장소
  //    린트(`react-hooks/set-state-in-effect`)가 그것을 막는다.
  const [form, setForm] = useState(() => readPair(pair));

  if (!open || !pair) return null;

  const set = (key) => (event) => setForm((current) => ({ ...current, [key]: event.target.value }));
  const filled = [form.axis, form.xLabel, form.yLabel, form.xValue, form.yValue]
    .every((value) => value.trim());
  const verdict = classifyPair(writePair(pair, form));
  const view = TYPE_VIEW[verdict.taskType] ?? { label: verdict.taskType, tone: 'danger' };

  return (
    <Dialog open={open} onClose={onClose} title="비교안 편집">
      <div className="twin-pair-form">
        <label className="twin-pair-form__field">
          <span>무엇을 비교하나</span>
          {/* 라벨 안에 설명(`small`)이 같이 있어서 암시적 연결로는 이름이 뭉친다 — 명시한다. */}
          <input type="text" value={form.axis} onChange={set('axis')}
                 aria-label="무엇을 비교하나" placeholder="예: 보관 형태" />
          <small>이 한 가지만 다르게 둔다. 둘 이상 바꾸면 측정 한계 이하가 된다.</small>
        </label>

        <div className="twin-pair-form__sides">
          {[
            { side: 'A안', labelKey: 'xLabel', valueKey: 'xValue' },
            { side: 'B안', labelKey: 'yLabel', valueKey: 'yValue' },
          ].map(({ side, labelKey, valueKey }) => (
            <fieldset key={side} className="twin-pair-form__side">
              <legend>{side}</legend>
              <label>
                <span>이름</span>
                <input type="text" value={form[labelKey]} onChange={set(labelKey)}
                       aria-label={`${side} 이름`} />
              </label>
              <label>
                <span>{form.axis.trim() || '값'}</span>
                <input type="text" value={form[valueKey]} onChange={set(valueKey)}
                       aria-label={`${side} 값`} />
              </label>
            </fieldset>
          ))}
        </div>

        <p className={`twin-pair-form__verdict tone-${view.tone}`} role="status">
          <strong>{view.label}</strong> {verdict.reason}
        </p>

        <div className="twin-pair-form__actions">
          <Button variant="outline" onClick={onClose}>취소</Button>
          <Button onClick={() => onSave(writePair(pair, form))} disabled={!filled}>저장</Button>
        </div>
      </div>
    </Dialog>
  );
}
