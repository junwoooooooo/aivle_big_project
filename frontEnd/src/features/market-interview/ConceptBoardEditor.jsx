import { priceText } from './marketInterviewResult.js';

/**
 * 컨셉보드 편집기 — 응답자가 볼 자극 여섯 칸.
 *
 * <p>자동으로 채워진 채 열린다. 사업 검증에서 <b>다듬기까지 마치고 확정한</b> 사업안에서
 * 서버가 그대로 꺼낸 값이고(확정 전이면 서버가 이 화면을 안 연다), 사용자는
 * <b>말을 다듬을 뿐</b>이다. 빈 표로 시작하지 않는 이유는 우열 조사에서 배운 것이다 —
 * 첫 화면이 빈 칸이면 그 칸을 채우는 것이 일이 되고, 기능은 안 쓰인다.
 *
 * <p><b>가격은 여기서 못 고친다.</b> 확정 가설에서 온 값이고, 자극의 가격을 바꿔 반응을
 * 보는 것은 지불의사 측정이다 — 이 조사가 답하지 않기로 한 바로 그것이다. 값이 잘못됐으면
 * 사업안의 가격 가설을 고쳐야 한다.
 */
export default function ConceptBoardEditor({ board, onChange, disabled = false, preview }) {
  const set = (field) => (event) => onChange?.({ ...board, [field]: event.target.value });

  const setFeatures = (event) => onChange?.({
    ...board,
    // 한 줄에 하나. 빈 줄은 보낼 때 걸러진다 — 타이핑 중에 줄을 지우지 않으려고 여기선 남긴다.
    featureSet: event.target.value.split('\n'),
  });

  return (
    <div className="mi-board">
      <label className="mi-board__field">
        <span>이름</span>
        <input type="text" value={board.conceptName} onChange={set('conceptName')}
               disabled={disabled} maxLength={200} />
      </label>

      <label className="mi-board__field">
        <span>누구를 위한 것인가</span>
        <input type="text" value={board.targetUsers} onChange={set('targetUsers')}
               disabled={disabled} maxLength={1000} />
      </label>

      <label className="mi-board__field">
        <span>어떤 상황의 문제인가</span>
        <textarea rows={2} value={board.problemScenario} onChange={set('problemScenario')}
                  disabled={disabled} maxLength={2000} />
      </label>

      <label className="mi-board__field">
        <span>하는 일 <em>한 줄에 하나</em></span>
        <textarea rows={3} value={board.featureSet.join('\n')} onChange={setFeatures}
                  disabled={disabled} />
      </label>

      <label className="mi-board__field">
        <span>다른 것과 다른 점</span>
        <textarea rows={2} value={board.differentiators} onChange={set('differentiators')}
                  disabled={disabled} maxLength={2000} />
      </label>

      <div className="mi-board__field mi-board__field--locked">
        <span>가격</span>
        <p className="mi-board__price">
          {priceText(board.priceKrw)}
          <em>
            확정 가설에서 왔다. 자극의 가격을 바꿔 반응을 보는 것은 지불의사 측정이라
            이 조사가 답하지 않는다 — 값이 잘못됐으면 사업안에서 고친다.
          </em>
        </p>
      </div>

      {/* 응답자가 보는 그대로. 자극을 못 보면 답을 해석할 수 없다. */}
      <details className="mi-board__preview">
        <summary>응답자에게 이렇게 보인다</summary>
        <pre>{preview}</pre>
      </details>
    </div>
  );
}
