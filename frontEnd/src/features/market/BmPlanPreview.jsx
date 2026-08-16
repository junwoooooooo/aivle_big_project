import BmCanvas from './BmCanvas.jsx';
import { CANVAS_LAYOUT, CELL_KIND } from './marketResult.js';
import { LIST_FIELDS, PLAN_CELL, PLAN_FIELDS } from './bmPlan.js';

/**
 * 타이핑하는 대로 차는 캔버스.
 *
 * <p>「꽉 차게 나왔으면 좋겠다」는 요청에 화면이 직접 답하는 자리다. 기존 `BmCanvas` 를
 * 그대로 쓴다 — 미리보기용 격자를 따로 만들면 두 개가 갈라지고, 갈라지는 순간 미리보기가
 * 결과와 다른 것을 보여 준다.
 *
 * <p>⚠ <b>관측 4칸은 여기서 채우지 않는다.</b> 그 칸은 시장 근거가 채우고, 실행하기 전에는
 * 무엇이 들어올지 모른다. 미리 그려 두면 「이미 있다」로 읽힌다 — 회색으로 자리만 세운다.
 */
const OBSERVED_NOTE = '실행하면 시장조사 근거가 채운다';

export default function BmPlanPreview({ draft }) {
  const typed = new Map();
  for (const [key] of PLAN_FIELDS) {
    const raw = String(draft[key] ?? '');
    const content = LIST_FIELDS.includes(key)
      ? raw.split('\n').map((s) => s.trim()).filter(Boolean)
      : (raw.trim() ? [raw.trim()] : []);
    typed.set(PLAN_CELL[key], content);
  }
  const cost = ['budget_krw', 'months', 'team']
    .map((key) => String(draft[key] ?? '').trim())
    .filter(Boolean);
  typed.set(PLAN_CELL.constraint, cost.length > 0 ? ['예산·기간·인원을 입력했다'] : []);

  const cells = CANVAS_LAYOUT.map((slot) => {
    const [kind, origin] = CELL_KIND[slot.cell] ?? ['관측', ''];
    const content = typed.get(slot.cell) ?? [];
    return {
      ...slot,
      kind,
      origin,
      // 아직 실행 전이다. **아무것도 검증되지 않았다** — 채운 칸도 PLAN 이다.
      status: content.length > 0 ? 'PLAN' : 'UNVERIFIED',
      content,
      reason: kind === '관측' ? OBSERVED_NOTE : '아직 입력하지 않았다',
      sourceLabels: [],
      evidenceIds: [],
      evidence: [],
      missingEvidence: [],
      caveats: [],
      absent: false,
    };
  });

  return (
    <div className="bm-plan__preview">
      <h3>지금까지 채운 캔버스</h3>
      <p className="market-note">
        아직 실행 전이라 <strong>어느 칸도 검증되지 않았다.</strong>
        관측 4칸은 캔버스를 만들 때 시장조사 근거가 채운다.
      </p>
      <BmCanvas cells={cells} onJump={() => {}} />
    </div>
  );
}
