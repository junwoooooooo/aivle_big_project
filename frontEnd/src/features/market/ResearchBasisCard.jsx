import { Card } from '../../shared/ui';
import { useConceptRevision } from './useConceptRevision.js';
// ⚠ 값 → 글자 규칙은 **한 벌만** 둔다. 다듬기 화면의 원문 카드가 같은 값을 그리고,
//    거기서 초록 표시가 그 글자를 원문에서 찾는다 — 두 벌로 적으면 조용히 어긋난다.
import { hypothesisText } from './conceptRevision.js';

/** 확정 가설 키 → 사람이 읽는 이름. 순서가 곧 화면 순서다. */
const HYPOTHESIS_ROWS = Object.freeze([
  ['targetRegion', '목표 지역'],
  ['revenueModel', '수익 모델'],
  ['price', '가격'],
  ['channels', '판매·제공 채널'],
  ['differentiators', '차별점'],
  ['preMarketSomShare', '시장 점유 가정'],
  ['preMarketSom', '초기 확보 시장 규모'],
]);

/**
 * <b>무엇으로 조사하는가</b> — 조사 버튼 바로 위에 세운다.
 *
 * <p>예전에는 「현재 선택한 사업안의 확정 가설과 최종 법률 결과, 저장된 시장 입력을 사용합니다」
 * 한 줄뿐이었다. <b>그 한 줄은 무엇으로 조사하는지를 말하지 않는다</b> — 사용자는 앞 단계에서
 * 자기가 고른 값이 이 조사에 실렸는지 확인할 길이 없었다(2026-08-16 사용자 지적).
 *
 * <p>그래서 <b>시장 씨앗 스냅샷</b>을 그대로 편다. 새 계약을 만들지 않는다 — 이 스냅샷이
 * 조사에 실려 가는 <b>바로 그 값</b>이고, 여기 없는 것은 조사에도 안 간다.
 *
 * <p>못 읽으면 조용히 접는다. 조사를 막을 일이 아니다.
 */
export default function ResearchBasisCard({ client, api, projectId, conceptName }) {
  // ⚠ 시드를 여기서 따로 부르지 않는다. 다듬기 화면이 쓰는 **같은 훅**을 쓴다 —
  //    두 벌로 읽으면 「어느 판을 보고 있나」가 조용히 갈리고, 실제로 한 번 갈렸다.
  const revision = useConceptRevision(client, api, projectId, true);
  const snapshot = revision.concept ?? null;
  const concept = snapshot?.selectedConcept ?? null;
  const hypotheses = snapshot?.finalHypotheses ?? null;
  const rows = HYPOTHESIS_ROWS
    .map(([key, label]) => [label, hypothesisText(hypotheses?.[key]?.value)])
    .filter(([, text]) => text);

  return (
    <Card title="이 값으로 조사해요">
      <p className="market-note">
        <strong>{concept?.identity?.conceptName || conceptName || '현재 선택한 사업안'}</strong>
        {concept?.identity?.conceptDefinition ? ` — ${concept.identity.conceptDefinition}` : ''}
      </p>
      {rows.length > 0 ? (
        <dl className="mr-basis__list">
          {rows.map(([label, text]) => <div key={label}><dt>{label}</dt><dd>{text}</dd></div>)}
        </dl>
      ) : (
        <p className="market-note">
          확정한 값을 아직 읽지 못했어요. 사업안 단계에서 검증 가정을 확인하면 여기에 나와요.
        </p>
      )}
      {/* ⚠ 고치는 자리는 여기가 아니다. 조사 «중»에 값을 바꾸면 어느 값으로 돈 결과인지가
          갈린다 — 고치려면 사업안 단계로 돌아가 가설을 다시 확정한다. */}
      <p className="market-note">
        고치려면 <strong>사업안 단계</strong>로 돌아가 검증 가정을 다시 확정해 주세요.
      </p>
    </Card>
  );
}
