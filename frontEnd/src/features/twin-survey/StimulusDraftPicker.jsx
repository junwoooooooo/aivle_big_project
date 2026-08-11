import { useState } from 'react';

import { Button } from '../../shared/ui';

/**
 * 자극 초안 고르기 — 사용자가 첫 칸을 채우는 대신 고르는 자리.
 *
 * 이 화면이 생긴 이유는 「엔진은 됐는데 못 쓴다」였다. 속성명·양쪽 값·라벨을 손으로 치고
 * 「가격은 양쪽 같게, 속성은 하나만」이라는 규칙까지 사용자가 지켜야 했다. 초안은 그 규칙을
 * 지킨 채로 나오므로 여기서는 **고르기만** 하면 된다.
 *
 * ⚠ 서버가 버린 후보(`dropped`)를 감추지 않는다. 왜 그 축을 못 묻는지 보여야 사용자가
 * 컨셉을 고칠 수 있고, 「AI 가 3개만 줬다」로 읽히지 않는다.
 */
export default function StimulusDraftPicker({ draft, disabled = false, onUse }) {
  const pairs = draft?.pairs ?? [];
  const [selected, setSelected] = useState(() => new Set(pairs.map((pair) => pair.pairId)));

  const toggle = (pairId) => {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(pairId)) next.delete(pairId); else next.add(pairId);
      return next;
    });
  };

  const use = () => {
    // 편집기·서버가 먹는 모양만 남긴다. `axis`·`rationale` 은 고르는 동안만 쓰는 설명이고,
    // 조사 입력 모델이 모르는 필드라 그대로 보내면 400 이다.
    onUse?.(draft.situation, pairs
      .filter((pair) => selected.has(pair.pairId))
      .map((pair) => ({ pairId: pair.pairId, X: pair.X, Y: pair.Y })));
  };

  return (
    <div className="twin-draft">
      <p className="twin-draft__lead">
        컨셉에서 뽑은 비교 쌍이다. <strong>물어볼 것만 골라라</strong> — 고른 뒤에도 아래
        편집기에서 값을 고칠 수 있다.
      </p>

      {pairs.map((pair) => (
        <label key={pair.pairId} className="twin-draft__card">
          <input
            type="checkbox"
            checked={selected.has(pair.pairId)}
            disabled={disabled}
            onChange={() => toggle(pair.pairId)}
          />
          <span className="twin-draft__body">
            <span className="twin-draft__axis">{pair.axis}</span>
            <span className="twin-draft__sides">
              {pair.X.label} «{pair.X.attrs[pair.axis]}» vs {pair.Y.label} «{pair.Y.attrs[pair.axis]}»
            </span>
            <span className="twin-draft__why">{pair.rationale}</span>
          </span>
        </label>
      ))}

      {draft?.dropped?.length > 0 ? (
        <details className="twin-draft__dropped">
          <summary>못 묻는 축 {draft.dropped.length}개</summary>
          <ul>
            {draft.dropped.map((item) => (
              <li key={`${item.axis}-${item.taskType}`}>
                <strong>{item.axis}</strong> — {item.reason}
              </li>
            ))}
          </ul>
        </details>
      ) : null}

      <div className="twin-draft__actions">
        <Button onClick={use} disabled={disabled || selected.size === 0}>
          고른 {selected.size}쌍으로 계속
        </Button>
      </div>
    </div>
  );
}
