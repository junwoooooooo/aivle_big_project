import { COMPREHENSION_VIEW, profileLines } from './marketInterviewResult.js';

/**
 * 카드에 <b>펼쳐 두는 세 문항</b>. 나머지 여섯은 「나머지 답 보기」 안으로 접는다.
 *
 * <p>왜 이 셋인가: 아홉을 다 펼치니 카드 다섯 장이 문단 45개가 됐고 사용자가
 * 「이것도 너무 많다」고 했다. 셋은 <b>화면 맨 위 결론이 쓰는 축과 같다</b> —
 * 끌림(Pull) · 안 사는 이유(Anxiety) · 무엇을 고치면 되나. 결론에서 본 수를
 * 「누가 그렇게 말했는지」로 되짚을 때 필요한 것이 이 셋이다.
 *
 * <p>⚠ <b>나머지 여섯을 지우지 않는다.</b> 접을 뿐이고, 펴면 아홉이 그대로 있다.
 */
const CARD_PRIMARY = Object.freeze(['like', 'barrier', 'suggestion']);

/**
 * 응답자 한 명. 프로필 6칸 + 그 사람이 실제로 한 말.
 *
 * <p>배지가 «선택»이 아니라 <b>이해도</b>인 것이 우열 조사와의 차이다. 「다른 물건으로
 * 이해」 카드가 한 장 섞여 있는 것은 실패가 아니라 <b>설계</b>다 — 그 카드가
 * 「컨셉이 나쁘다」와 「설명이 나쁘다」를 눈으로 가르게 한다.
 *
 * <p>대표 카드와 전원 응답이 <b>같은 컴포넌트를 쓴다.</b> 전원 응답 쪽만 `badge` 로
 * 타겟/비타겟을 덧붙인다 — 근거를 되짚는 자리라 누구의 말인지가 함께 보여야 한다.
 */
export default function InterviewCard({ card, badge = null }) {
  const { head, sub } = profileLines(card.profile);
  const view = COMPREHENSION_VIEW[card.comprehension] ?? COMPREHENSION_VIEW.unclassified;
  const primary = card.answers.filter((answer) => CARD_PRIMARY.includes(answer.key));
  const rest = card.answers.filter((answer) => !CARD_PRIMARY.includes(answer.key));
  const rows = (list) => list.map((answer) => (
    <div key={answer.key}>
      <dt>{answer.label}</dt>
      <dd>&ldquo;{answer.value}&rdquo;</dd>
    </div>
  ));

  return (
    <article className="mi-interview">
      <div className="mi-interview__head">
        <span className={`mi-interview__avatar tone-${view.tone}`}>
          {card.profile.age ?? '—'}
        </span>
        <div className="mi-interview__who">
          <p className="mi-interview__line">{head}</p>
          <p className="mi-interview__sub">{sub}</p>
        </div>
        {badge ? <span className="mi-interview__badge tone-neutral">{badge}</span> : null}
        <span className={`mi-interview__badge tone-${view.tone}`}>{view.label}</span>
      </div>
      <dl className="mi-interview__answers">{rows(primary)}</dl>
      {rest.length > 0 ? (
        <details className="mi-more">
          <summary>나머지 답 {rest.length}개 보기</summary>
          <dl className="mi-interview__answers">{rows(rest)}</dl>
        </details>
      ) : null}
    </article>
  );
}
