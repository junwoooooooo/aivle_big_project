import { gateSurvey } from './taskTypeGate.js';
import { ProjectFormRow } from '../../shared/ui/index.js';

const TYPE_VIEW = {
  DOMINANCE: { label: '명백한 우열형', tone: 'success' },
  PRICE: { label: '가격형 — 제공하지 않음', tone: 'danger' },
  ETHICAL_VALUE: { label: '윤리·가치형 — 제공하지 않음', tone: 'danger' },
  UNMEASURABLE: { label: '측정 불가', tone: 'danger' },
  IDENTICAL: { label: '두 안이 같음', tone: 'danger' },
};

/** 축 이름과 양쪽 값 — 목록 카드가 보여주는 전부다. */
function summarize(pair) {
  const axis = Object.keys(pair?.X?.attrs ?? {})[0]
    ?? Object.keys(pair?.Y?.attrs ?? {})[0] ?? '';
  return {
    axis,
    x: pair?.X?.attrs?.[axis] ?? '',
    y: pair?.Y?.attrs?.[axis] ?? '',
    xLabel: pair?.X?.label ?? 'A안',
    yLabel: pair?.Y?.label ?? 'B안',
  };
}

/**
 * 자극 목록 — **무엇과 무엇을 비교하는가**만 보이는 자리.
 *
 * 이전에는 여기가 속성×양쪽 값 표였고, 표가 화면을 아래로 늘리는 동안 정작 「이 조사가 무엇을
 * 묻는가」는 어디에도 없었다. 이제 한 쌍이 카드 한 장이고, 고치는 일은 창(`PairEditorDialog`)
 * 에서 한다.
 *
 * ⚠ 판정의 정본은 서버다(`ai/app/twin/task_type.py`). 여기 배지는 거울이고, 갈리면 서버가
 * 이긴다 — 그래서 실행을 여는 최종 근거로 쓰지 않는다. 화면도 막고, 서버도 막는다.
 *
 * ⚠ **가격 칸은 없다.** 초안이 양쪽에 같은 값을 얹고 그대로 서버로 간다 — 양쪽이 같아야
 * 우열형이라 편집할 것이 없고, 편집칸을 두면 한쪽만 고쳐 지불의사를 만들 수 있다.
 */
export default function StimulusEditor({
  situation = '',
  pairs = [],
  onSituationChange,
  onEdit,
  disabled = false,
}) {
  const gate = gateSurvey(pairs);

  return (
    <div className="twin-editor">
      <div className="project-form-layout"><ProjectFormRow label="상황 문장" id="twin-situation">
        {(fieldProps) => <input
          type="text"
          value={situation}
          disabled={disabled}
          onChange={(event) => onSituationChange?.(event.target.value)}
          placeholder="가게에서 하나를 고릅니다. 아래 두 상품이 있습니다."
          {...fieldProps}
        />}
      </ProjectFormRow></div>

      <ul className="twin-editor__list">
        {pairs.map((pair, index) => {
          const { axis, x, y, xLabel, yLabel } = summarize(pair);
          const verdict = gate.verdicts[index];
          const view = TYPE_VIEW[verdict.taskType] ?? { label: verdict.taskType, tone: 'danger' };

          return (
            <li key={pair.pairId}>
              <button
                type="button"
                className="twin-pair-card"
                disabled={disabled}
                onClick={() => onEdit?.(index)}
                aria-label={`${axis || pair.pairId} 비교안 편집`}
              >
                <span className="twin-pair-card__axis">{axis || '축 없음'}</span>
                <span className="twin-pair-card__edit" aria-hidden="true">편집</span>
                {/* 이 두 줄이 이 화면의 본문이다 — 무엇과 무엇을 비교하는가. */}
                <span className="twin-pair-card__versus">
                  <span className="twin-pair-card__side">
                    <b>{xLabel}</b>{x ? ` · ${x}` : ''}
                  </span>
                  <span className="twin-pair-card__vs" aria-hidden="true">↔</span>
                  <span className="twin-pair-card__side">
                    <b>{yLabel}</b>{y ? ` · ${y}` : ''}
                  </span>
                </span>
                <span className={`twin-pair-card__verdict tone-${view.tone}`}>{view.label}</span>
              </button>
            </li>
          );
        })}
      </ul>

      {gate.blocked.length > 0 && (
        <p className="twin-editor__blocked" role="alert">
          팔 수 없는 질문이 {gate.blocked.length}개 있다. 카드를 눌러 고쳐야 실행할 수 있다.
        </p>
      )}
      {pairs.length === 0 && (
        <p className="twin-editor__empty">자극 쌍이 없다. 비교할 두 안을 먼저 넣어라.</p>
      )}
    </div>
  );
}
