import { useCallback, useMemo, useState } from 'react';
import { Alert, Badge, Button, Card, Checkbox } from '../../shared/ui';
import Emphasis from './emphasis.jsx';

import { SUBJECT_LABEL, sectionAnchor, subjectNumber } from './marketResult.js';
import {
  REVISION_FIELD_LABEL, conceptDocument, highlightChanges, narrativeParts, normalizeDeltaLegal,
} from './conceptRevision.js';

/**
 * <b>짝이 맞지 않는 별표를 지운다.</b> `Emphasis` 앞에 한 번 씌워 쓴다.
 *
 * <p>왜 필요한가 — 모델이 쓴 문장에 `**소형 마트 입점으로 접근성 강화*` 처럼 <b>여는
 * 별표만</b> 있는 것이 섞여 온다. `Emphasis` 는 짝이 맞는 `**…**` 만 굵게 바꾸므로,
 * 짝이 없는 별표는 그대로 <b>글자로</b> 화면에 남는다(2026-08-16 실측).
 *
 * <p>⚠ 짝이 맞는 것은 <b>건드리지 않는다</b> — 그건 `Emphasis` 가 굵게 만들 몫이다.
 * 여기서 지우면 규칙 파일이 정본으로 쓰는 강조까지 평평해진다.
 */
function 평문(text) {
  if (typeof text !== 'string' || !text.includes('*')) return text;
  // 짝이 맞는 강조를 잠시 치워 두고, 남은 별표만 지운 뒤 되돌린다.
  // ⚠ 자리표는 **본문에 절대 안 나오는 모양**이어야 한다 — 숫자만 쓰면 되돌릴 때
  //   본문의 숫자가 강조로 바뀐다.
  const 보관 = [];
  const 치움 = text.replace(/\*\*[\s\S]+?\*\*/g, (m) => `«‹${보관.push(m) - 1}›»`);
  return 치움.replace(/\*/g, '')
    .replace(/«‹(\d+)›»/g, (_, n) => 보관[Number(n)]);
}


/**
 * 사업 검증의 <b>둘째 화면 — 「다듬어진 컨셉」</b>.
 *
 * <p>라운드 이력은 보이지 않는다. 3라운드의 시행착오를 그대로 늘어놓으면 결론이 묻힌다.
 * 다만 <b>못 푼 것은 반드시 보인다</b> — 수렴 못 한 채 끝난 것을 성공처럼 보이면
 * 그것이 조용한 거짓말이다.
 *
 * <p>읽는 순서가 이 화면의 전부다: <b>컨셉 원문 → 무엇이 왜 바뀌었나 → 그 근거가 있는
 * 화면 1 의 과목 → 법률 검토</b>. 세 걸음이 끊기면 「우리가 만든 방식의 다 패스」와
 * 구분이 안 된다.
 */
const OUTCOME_VIEW = {
  // ⚠ 각주는 `noteOf()` 가 정한다 — 아래 상수는 **법률을 실제로 다시 본 경우**의 말이다.
  //   법률 델타 없이 이 각주를 쓰면 같은 화면에서 「통과했다」와 「본 적 없다」가 동시에
  //   선다(실측: 법률 민감 5면을 안 건드린 다듬기에서 도달한다).
  CONVERGED: { label: '다듬기 완료', tone: 'success',
    note: '법률 검토까지 통과했어요.' },
  NOTHING_TO_FIX: { label: '고칠 것 없음', tone: 'neutral',
    note: '시장 근거로 바꿀 것이 나오지 않았어요 — 컨셉은 그대로예요.' },
  // ⚠ **2026-08-15 신설. 이 칸이 없어서 「법이 막았다」가 위의 「고칠 것 없음」으로 떴다.**
  //   막힌 사업안을 「괜찮대」로 읽고 **안심하고 확정하게** 만드는 종류의 거짓이었다.
  //   사유는 아래 「못 푼 것」에 이미 실려 온다(`Controller.legalReasonsOf`).
  LEGAL_BLOCKED: { label: '법이 막았어요', tone: 'warning',
    note: '법률 검토가 이 변경을 막았어요. 아래 「못 푼 것」이 그 사유예요.' },
  ROUND_LIMIT: { label: '못 푼 것이 남았어요', tone: 'warning',
    note: '3라운드 안에 수렴하지 못했어요. 아래 「못 푼 것」이 그 사유예요.' },
  RUNNING: { label: '다듬는 중', tone: 'info', note: '법률 검토를 기다리고 있어요.' },
  NOT_STARTED: { label: '아직 안 함', tone: 'neutral',
    note: '아직 다듬은 결과가 없어요 — 아래는 확정된 사업안 원문이에요.' },
  // ⚠ 이 칸이 생기기 전에는 **실패가 「아직 안 함」으로 보였다.** 라운드 행은 채택 성공
  //    때만 생기므로 화면은 실패와 시작 전을 구별하지 못했고, 사용자는 영원히 기다렸다.
  FAILED: { label: '다듬기 실패', tone: 'danger',
    note: '다듬기를 돌리다 실패했어요. 다시 시도할 수 있어요.' },
  // ───── 사람이 고르는 문이 생기며 함께 만든 칸들 ─────
  // ⚠ 이 셋이 없으면 화면이 「다듬는 중」에 갇힌다. 사용자가 답할 차례인데 화면은
  //   기다리라고 말하는 상태 — 이 저장소가 이미 두 번 앓은 병이다.
  AWAITING_DECISION: { label: '고를 차례예요', tone: 'info',
    note: '조사가 이렇게 고치자고 해요. 근거를 읽고 반영할 것만 체크하세요.' },
  DECLINED: { label: '제안을 모두 넘겼어요', tone: 'neutral',
    note: '컨셉은 그대로예요. 다른 제안을 받아 볼 수도 있어요.' },
  DECISION_NOT_APPLIED: { label: '고른 값이 서지 않았어요', tone: 'warning',
    note: '체크한 값이 컨셉 검사를 통과하지 못했어요 — 법은 아직 보지도 못했어요.' },
};

/** 출처를 <b>사람 말</b>로. 모르는 값은 지어내지 않고 그대로 보인다. */
/* ⚠ 정본은 시장조사 규칙(`rules/scoring.v1.json`)의 12종이다. 빠뜨리면 `community` 같은
   **영문 토큰이 그대로 사용자 화면에 선다** — 이 제품이 반복해 앓은 「기계 어휘가 샌다」다.
   하필 블로그·카페(`community`)야말로 사용자가 꼭 알아야 할 출처다. */
const SOURCE_KIND_LABEL = {
  gov_stat: '정부 통계', public_filing: '공시 서류', legal_source: '법령',
  panel_data: '패널 데이터', survey_firm: '조사기관 원자료', official_page: '기업·기관 공식',
  press: '언론 보도', press_release: '보도자료', research_firm: '시장조사 리포트',
  aggregate: '모아 정리한 자료', community: '블로그·카페',
};

/**
 * 라운드 상한. ⚠ **정본은 자바 {@code ConceptRefinementRound.MAX_ROUNDS} 다** — 서버가 바꾸면
 * 화면만 조용히 틀린다. 응답에 안 실려 오므로 사본을 두되, 사본이라는 것을 여기 적어 둔다.
 */
const MAX_ROUNDS = 3;

/** 「누가 쟀나」의 가장 정직한 표시 — 주소의 집 이름. */
function hostOf(url) {
  if (!url) return null;
  try { return new URL(url).hostname.replace(/^www\./, ''); } catch { return null; }
}

/**
 * 결말 각주. <b>법률을 다시 본 적이 없으면 「통과했다」고 말하지 않는다.</b>
 *
 * <p>다듬을 수 있는 7면 중 법률 민감은 5면이고, 나머지(`targetUsers`·`featureSet`)만
 * 고친 라운드는 법률 델타를 부르지 않는다. 그때도 결말은 `CONVERGED` 이므로 예전 각주는
 * 「법률 검토까지 통과했어요」라고 말했고, 같은 화면 아래 법률 카드는 「아직 다시 본 법이
 * 없어요」라고 말했다. <b>한 화면에서 두 말이 부딪히면 사용자는 어느 쪽도 못 믿는다.</b>
 */
function noteOf(view, outcome, legal) {
  if (outcome !== 'CONVERGED' || legal) return view.note;
  return '바꾼 것이 법률과 무관한 칸이라 법을 다시 보지는 않았어요.';
}

export default function RefinementSummary({
  result, concept,
  evidenceSubjects, evidenceById = null, onJumpSubject, onBack, onNext = null,
  onFinalize, finalizing = false,
  error = null, onRetry = null, retrying = false, onDecide = null, deciding = false,
}) {
  const view = OUTCOME_VIEW[result?.outcome] ?? OUTCOME_VIEW.NOT_STARTED;
  const changes = result?.changes ?? [];
  // 「고를 차례」인 라운드. 지난 라운드 제안까지 체크 가능해지면 **눌러도 아무 일이 없는
  // 체크박스**가 생기고, 사용자는 반영됐다고 믿는다.
  const awaiting = result?.outcome === 'AWAITING_DECISION';
  const openRound = awaiting
    ? changes.reduce((max, change) => Math.max(max, change.round ?? 0), 0) : null;
  const [picked, setPicked] = useState(() => new Set());
  const toggle = useCallback((field) => setPicked((was) => {
    const next = new Set(was);
    if (!next.delete(field)) next.add(field);
    return next;
  }), []);
  const blocks = useMemo(() => conceptDocument(concept), [concept]);
  // 서술문이 있으면 한 문단으로 읽힌다. 없으면(아직 안 썼거나 검증을 못 통과했으면)
  // 칸 나열로 폴백한다 — 반쯤 맞는 문장을 컨셉 원문 자리에 세우지 않는다.
  const narrative = useMemo(() => narrativeParts(result?.narrative), [result?.narrative]);
  // 델타만. 전체 보고서는 읽지도 않는다 — `useRevision` 주석 참고.
  const legal = useMemo(() => normalizeDeltaLegal(result?.deltaLegal), [result?.deltaLegal]);

  // 카드 안 점프(원문 → 변경 항목)는 화면을 넘지 않는다. 착지만 잠깐 물들인다.
  const [landed, setLanded] = useState(null);
  const jump = useCallback((id) => {
    setLanded(id);
    document.getElementById(id)?.scrollIntoView({ block: 'center' });
  }, []);

  return (
    <>
      {/* ⚠ **페이지 제목이 아니라 구획 제목이다.** 이 부품은 사업 모델 탭 «안»에 서므로
          자기 단계 번호를 말하면 안 된다 — 옛 판은 「3. 사업 검증」이라고 적었는데
          그 번호는 지금 여정에서 **2번**이라 틀린 데다, 탭이 이미 제목을 갖고 있어
          한 화면에 큰 제목이 둘이 됐다. */}
      <div className="cr-heading">
        <h3>다듬어진 컨셉</h3>
        {/* ⚠ **결정 전에 「이미 고쳤어요」라고 말하지 않는다.** 이 문장은 자동 적용 시절의
            것이라 모든 결말에서 떴는데, 「고를 차례」에서는 적용된 것도 초록도 없다 —
            사용자가 읽는 첫 문장이 바로 옆 배지와 정반대가 된다. */}
        <span>
          {awaiting
            ? '조사가 이렇게 고치자고 해요. 아직 아무것도 바뀌지 않았어요 — 근거를 읽고 반영할 것만 체크하세요.'
            : '검증 결과를 반영해 컨셉을 이렇게 고쳤어요. 초록색 부분이 바뀐 곳이에요. 번호를 누르면 이유를 볼 수 있어요.'}
        </span>
      </div>

      {error ? <Alert tone="danger">{error}</Alert> : null}

      <p className="cr-outcome">
        <Badge tone={view.tone}>{view.label}</Badge>
        {' '}<span className="market-note">{noteOf(view, result?.outcome, legal)}</span>
      </p>

      {/* 실패했을 때만 선다. 사유는 **서버가 준 것만** 적는다 — 없으면 그 줄을 비운다.
          지어낸 사유는 사용자를 엉뚱한 곳으로 보낸다.
          ⚠ 자동 재시도는 일부러 없다. 유료 호출이 사용자 의도 없이 반복되면 되짚을 수 없다. */}
      {result?.retry?.failed ? (
        <Card>
          {result.retry.reason
            ? <p className="market-note">사유: {result.retry.reason}</p>
            : null}
          <p className="market-note">
            시도 {result.retry.attemptsUsed}/{result.retry.maxAttempts}
          </p>
          <div className="bv-foot">
            {result.retry.retryable && onRetry ? (
              <Button onClick={onRetry} disabled={retrying}>
                {retrying ? '다시 거는 중…' : '다시 시도하기'}
              </Button>
            ) : (
              <span className="market-note">
                시도 횟수를 다 썼어요. 사업 검증부터 다시 돌려야 해요.
              </span>
            )}
          </div>
        </Card>
      ) : null}

      {/* ⚠ **「전부 넘겼다」가 막다른 길이 되지 않게 한다.** 재시도 문(`POST /retry`)은 원래
          있었지만 버튼은 «실패했을 때만» 떴다. 그러면 제안을 다 거절한 사용자는 「다 넘겼더니
          아무것도 못 하게 됐다」를 겪는다. 남은 라운드 수도 같이 적는다 — 거절도 라운드를 쓴다. */}
      {['DECLINED', 'DECISION_NOT_APPLIED'].includes(result?.outcome) && !result?.retry?.failed ? (
        <Card>
          <p className="market-note">
            {result?.outcome === 'DECISION_NOT_APPLIED'
              ? '체크한 값이 서지 않아 컨셉은 그대로예요. '
              : ''}
            다른 제안을 받아 볼 수 있어요 — 남은 라운드 {Math.max(0, MAX_ROUNDS - (result?.rounds ?? 0))}번.
          </p>
          <div className="bv-foot">
            {onRetry && (result?.rounds ?? 0) < MAX_ROUNDS ? (
              <Button variant="ghost" onClick={onRetry} disabled={retrying}>
                {retrying ? '다시 거는 중…' : '다른 제안 받기'}
              </Button>
            ) : (
              <span className="market-note">라운드를 다 썼어요 — 이대로 확정할 수 있어요.</span>
            )}
          </div>
        </Card>
      ) : null}

      {/* 컨셉 원문 — 문서처럼 읽히도록 15px / 줄간 2.0.
          ⚠ **화면이 칸을 접착제 문장으로 잇지 않는다.** 한 문단으로 읽히는 것은 서버가
          검증한 서술문이 있을 때뿐이고, 그 검증은 「바뀐 조각이 정말 그 값을 담았나」다.
          없으면 칸 나열로 떨어진다 — 지어낸 접착제는 아무도 쓴 적 없는 말이다. */}
      {/* ⚠⚠ **「고를 차례」에는 컨셉 원문을 아예 안 낸다** (2026-08-16 사용자 지시).
          아직 아무것도 정해지지 않았는데 문단은 «지금 컨셉»을 최종본처럼 적는다 —
          바로 위 머리글이 「아직 아무것도 바뀌지 않았어요」라고 말하는 동안, 문단은 옛
          주기에 적용된 값(1팩 9,500원)을 말하고, 아래 제안은 그것을 6,500원으로 내리자고
          한다. **한 화면에서 세 값이 동시에 말한다.** 결정이 끝난 뒤에 세우면 그 자리는
          「내 컨셉이 결국 이렇게 됐다」가 되어 뜻이 산다. */}
      {awaiting ? null : (
      <Card>
        {narrative ? (
          <div className="cr-doc cr-doc--flow">
            {/* ⚠ 착지는 **`cr-nref-`** 다(`cr-why-` 가 아니다). 서술문 번호는 «채택분» 위에서
                매겨지고 변경표 순번은 «전량» 위에서 매겨진다 — 앞엣것으로 착지시키면
                사용자가 «거절한» 제안의 이유 칸으로 간다(오류 없이 화면만 틀린다). */}
            <p>{narrative.map((part, index) => (
              part.ref ? (
                <button key={`nr-${index}`} type="button" className="cr-chg"
                  onClick={() => jump(`cr-nref-${part.ref}`)}>
                  <Emphasis text={평문(part.text)} /><sup>{part.ref}</sup>
                </button>
              ) : <span key={`nr-${index}`}><Emphasis text={평문(part.text)} /></span>
            ))}</p>
          </div>
        ) : blocks.length > 0 ? (
          <div className="cr-doc">
            {blocks.map((block) => (
              <p key={block.key}>
                <span className="cr-doc__k">{block.label}</span>
                {highlightChanges(block.text, changes).map((part, index) => (
                  part.ref ? (
                    <button key={`${block.key}-${index}`} type="button" className="cr-chg"
                      onClick={() => jump(`cr-why-${part.ref}`)}>
                      <Emphasis text={평문(part.text)} /><sup>{part.ref}</sup>
                    </button>
                  ) : <span key={`${block.key}-${index}`}><Emphasis text={평문(part.text)} /></span>
                ))}
              </p>
            ))}
          </div>
        ) : (
          <p className="bm-cell__none">확정된 사업안 원문을 아직 불러오지 못했어요.</p>
        )}
      </Card>
      )}


      <Card title={awaiting ? '무엇을 고칠까요' : '무엇이, 왜 바뀌었나요'}>
        <p className="market-note">
          {awaiting
            ? '조사 결과 원문을 같이 펴 두었어요. 읽어 보고 반영할 것만 체크하세요.'
            : '바뀐 곳마다 어떤 조사 결과 때문인지 이어 두었어요.'}
        </p>
        {changes.length > 0 ? changes.map((change, index) => (
          <Change key={`${change.round}-${change.field}-${index}`} change={change} no={index + 1}
            landed={landed === `cr-why-${index + 1}`
              || (change.narrativeRef != null && landed === `cr-nref-${change.narrativeRef}`)}
            evidenceSubjects={evidenceSubjects} evidenceById={evidenceById}
            onJumpSubject={onJumpSubject}
            /* 체크는 «제안 단위»다 — 아직 답 안 한, 열린 라운드의 제안만 살아난다. */
            selectable={awaiting && change.accepted == null && change.round === openRound}
            checked={picked.has(change.field)} onToggle={toggle} />
        )) : <p className="bm-cell__none">바뀐 칸이 없어요.</p>}

        {awaiting && onDecide ? (
          <div className="bv-foot">
            <Button variant="ghost" disabled={deciding}
              onClick={() => onDecide(openRound, [])}>
              전부 넘기기
            </Button>
            {/* ⚠ 하나도 안 골랐을 때 이 버튼이 살아 있으면 「전부 넘기기」와 같은 일을 한다 —
                누른 사람의 뜻과 정반대다. 넘기려면 왼쪽 버튼을 «일부러» 눌러야 한다. */}
            <Button disabled={deciding || picked.size === 0}
              onClick={() => onDecide(openRound, [...picked])}>
              {deciding ? '반영하는 중…'
                : picked.size > 0 ? `체크한 ${picked.size}개만 반영하기` : '반영할 것을 고르세요'}
            </Button>
          </div>
        ) : null}
      </Card>

      {/* 판 ㊻ — **「못 푼 것」 상자를 뺐다**(2026-08-16 사용자 지시).
          담긴 문장이 「channels — 추가·교체는 1개까지다 (뺀 것 2 · 더한 것 2)」처럼
          **다듬기 엔진의 규칙 설명**이라, 사업가가 그걸로 정할 것이 없었다.
          ⚠ 위 배지(`ROUND_LIMIT` = 「못 푼 것이 남았어요」)는 그대로 남아 있어
            수렴하지 못한 채 끝났다는 사실 자체는 화면에서 사라지지 않는다. */}
      {/* ★ **고르는 동안은 「무엇이 무엇으로」를 나란히 보인다** (2026-08-16 사용자 제안).
          체크박스만 있으면 「이걸 고르면 내 사업안이 어떻게 되는가」가 어디에도 안 보인다.
          왼쪽은 지금 값, 오른쪽은 **체크한 것만** 반영한 값이다 — 체크를 켜고 끄면
          오른쪽이 따라 움직여서, 고르는 행동과 그 결과가 한 화면에 붙는다. */}
      {awaiting && changes.length > 0 ? (
        <Card title="고르면 이렇게 돼요">
          <div className="cr-cmp">
            <div className="cr-cmp__h"><span>지금 사업안</span><span>고른 것을 반영하면</span></div>
            {changes.filter((change) => change.round === openRound).map((change) => {
              const 고름 = picked.has(change.field);
              return (
                <div key={`cmp-${change.round}-${change.field}`}
                  className={`cr-cmp__r${고름 ? ' is-on' : ''}`}>
                  <span className="cr-cmp__k">{REVISION_FIELD_LABEL[change.field] ?? change.field}</span>
                  <span className="cr-cmp__a"><Emphasis text={평문(change.before ?? '')} /></span>
                  {/* ⚠ **값**을 쓴다. `after` 는 모델이 쓴 사람 말이라 목록 칸에서는
                      「…를 추가했어요」 같은 **설명**이 온다(실측: 네 칸 중 셋). 나란히 놓고
                      비교하는 표에 설명이 서면 비교가 안 된다. 값이 없을 때만 설명으로 돌아간다. */}
                  <span className="cr-cmp__b">
                    {고름 ? <Emphasis text={평문(change.afterValue || change.after || '')} />
                      : <span className="cr-cmp__same">그대로</span>}
                  </span>
                </div>
              );
            })}
          </div>
        </Card>
      ) : null}


      {/* ⚠⚠ **「법률 검토」 카드를 통째로 뺐다**(2026-08-16 사용자 지시: 「애매하다」).
          법령 이름과 조항 번호를 그대로 인용하던 자리인데, 사업가가 그것으로 정할 것이
          없었다 — 자문 자격이 없는 제품이 조항을 펴 놓으면 읽는 쪽만 애매해진다.

          **잃는 것을 적어 둔다.**
          ① 다듬기가 반영한 값이 «어떤 법에 걸리는지»가 화면에서 사라진다. 값은 봉투
             (`result.deltaLegal`)에 그대로 실려 오고, 파이프라인의 델타 법률 검토도
             **그대로 돈다** — 안 그리는 것뿐이다.
          ② 「법이 막았어요」(`LEGAL_BLOCKED`) 배지는 위에 그대로 남아 있어, 막혔다는
             **사실 자체**는 화면에서 사라지지 않는다. 사유는 그 배지 옆 문구가 말한다.
          되살릴 자리는 정확히 여기다. */}

      {/* ⚠ **경계 표시 — 빼지 마라.** 다듬기가 컨셉을 고쳐도 검증 결과(판정·게이트·성적표)는
          **고치기 전 컨셉으로 잰 값** 그대로다. 그 사실을 안 적으면 바로 아래 확정 버튼이
          「이 값들이 고친 컨셉의 것」이라는 **틀린 확신**을 찍는다. 재조사 경로는 별건이고,
          그때까지 사용자가 알아야 할 것은 이 한 줄이다. */}
      {/* ⚠ 조건은 「제안이 있었나」가 «아니라» **「반영된 것이 있나」**다. 사람이 고르는 문이
          생긴 뒤로 `changes` 는 제안 목록이라, 전부 넘겨서 컨셉이 하나도 안 바뀌었는데도
          「고치기 전 기준이에요」라고 말하게 된다 — 사용자를 헛된 재조사로 보내는 거짓이다. */}
      {/* ⚠⚠ **순서를 지켜 적는다 — 2026-08-15 크롬 실측.** 앞서 이 줄은 곧장 「검증 결과로
          돌아가 «다시 조사»를 누르세요」였는데, 그대로 따라가면 **막다른 길**이다.
          반영이 일어나는 순간 `apply()` → `staleDependents()` 가 시드를 낡음으로 만들고,
          시드를 다시 세우는 것은 **확정(BUILD_HANDOFF)**뿐이다. 그래서 확정 전에 조사를 걸면
          `MarketAnalysisSeedLookup.current()` 가 빈손이라 서버가 「확정된 사업안이 없다」로
          거절하고, 화면에는 **「요청을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.」**만
          뜬다(사유가 `getUserErrorMessage` 의 기본 문구에 삼켜진다). 자기 손으로 고친
          사용자에게 「사업안이 없다」는 말은 뜻이 통하지 않는다 — 그래서 **여기서 순서를 말한다.** */}
      {/* 판 ㊻ — 상자를 **한 줄**로 줄였다(2026-08-16 사용자 지시 「없애」).
          ⚠ **문장 자체는 남긴다.** 이 줄이 없으면 바로 아래 확정 버튼이 「앞 화면의 값이
            고친 컨셉의 것」이라는 틀린 확신을 찍는다 — 위 주석의 실측이 그 이유다.
            큰 경고 상자가 화면을 먹던 것이 문제였으므로 **크기만** 줄인다. */}
      {/* 판 ㊻ — 상자 → 한 줄 → **뺌**(2026-08-16 사용자 지시 세 번).
          ⚠ **잃는 것을 적어 둔다.** 반영이 일어나면 `staleDependents()` 가 시드를
            낡음으로 만들고, 앞 화면의 판정·수치·성적표는 **고치기 전 컨셉으로 잰 값**
            그대로 남는다. 그 사실을 말하는 자리가 이제 화면에 없다 — 바로 아래
            「이 컨셉으로 확정하기」가 그 오해 위에서 눌린다. 되살릴 자리를 찾는다면
            확정 버튼 옆이지 이 자리가 아니다. */}

      {/* ⚠ **수렴 못 했어도 막지 않는다.** 못 푼 것을 위에 보인 채로 확정할 수 있어야
          사용자가 자기 사업안을 앞으로 끌고 갈 수 있다. 막으면 길이 끊긴다. */}
      <div className="bv-foot">
        {/* ⚠ 옛 컨테이너에서는 이 부품이 «따로 선 화면»이라 돌아갈 자리가 필요했다. 지금은
            사업 모델 탭 «안»의 구획이라 돌아갈 곳이 없다 — 그때 `onBack` 이 없는데도 그리면
            **눌러도 아무 일이 없는 버튼**이 선다(2026-08-16 화면에서 실측). */}
        {onBack ? <Button variant="ghost" onClick={onBack}>← 검증 결과로</Button> : null}
        {/* ⚠ 조건이 `!== 'RUNNING'` 하나였을 때는 **고르는 중에도 확정 버튼이 떴다** —
            아직 답하지 않은 제안을 두고 「이 컨셉으로 확정」을 누를 수 있었다.
            ⚠ `DECISION_NOT_APPLIED` 도 뺀다. 그 상태의 사업안은 가설이 확정 전으로
            돌아가 있어(`PENDING_HYPOTHESIS_CONFIRMATION`) 누르면 서버가 거절한다 —
            「값이 서지 않았어요」 바로 밑에서 오류 배너가 뜨면 사용자는 길을 잃는다. */}
        {/* ⚠⚠ **확정을 마쳤으면 버튼을 안 낸다**(2026-08-16 실측). 확정은 한 번 성공하면
            사업안 상태와 시드가 바뀌어, 다시 누르면 같은 열쇠에 다른 내용이라 서버가
            `IDEMPOTENCY_CONFLICT` 로 거절한다 — **성공한 일이 「요청을 완료하지 못했습니다」로
            보이고 사용자는 계속 누른다.** 실제로 그렇게 겪었다. */}
        {result?.finalized ? (
          // ★ **같은 자리가 「다음」으로 바뀐다**(2026-08-16 사용자 지시).
          //   컨셉 확정 → 확정하는 중… → 다음. 확정을 마친 사람이 할 일은 하나뿐이라
          //   그 자리에 그 하나를 세운다.
          //   ⚠ 확정 버튼을 그대로 두면 다시 누르게 되고, 서버가 `IDEMPOTENCY_CONFLICT` 로
          //   거절해 **성공한 일이 「요청을 완료하지 못했습니다」로 보인다**(실측).
          onNext ? <Button onClick={onNext}>다음 →</Button> : null
        ) : onFinalize
          && !['RUNNING', 'AWAITING_DECISION', 'DECISION_NOT_APPLIED'].includes(result?.outcome) ? (
          <Button onClick={onFinalize} disabled={finalizing}>
            {finalizing ? '확정하는 중…' : '컨셉 확정'}
          </Button>
        ) : null}
      </div>
      {/* ⚠ **「법률 자문 아님」이 사는 자리 — 여기다**(2026-08-16 사용자 지시).
          법률 카드 안에서는 뺐고, 대신 **확정 직전**으로 옮겼다. 경계가 가장 필요한 순간은
          조항을 읽을 때가 아니라 그것을 **최종 컨셉으로 굳히는** 순간이다 — 여기서부터
          기술·운영·재무·마케팅이 이 값을 읽는다. 프로젝트 규칙(CLAUDE.md §8)이 지키라는
          경계가 이 한 줄이다. **빼지 마라.** */}
      {/* 판 ㊻ — 「확정하면 … 기술·운영·재무·마케팅이 이것을 읽어요」는 뺐다
          (2026-08-16 사용자 지시). 버튼 이름이 「이 컨셉으로 확정하기」라 같은 말이었다. */}
      <p className="market-note cr-foot-note">
        <Emphasis text="**법률 자문은 아니에요** — 판매 전에 직접 확인해야 할 자리는 직접 확인해 주세요." />
      </p>
    </>
  );
}

/**
 * 변경 한 항목 — <b>무엇을 했나 → 옛 문구 → 새 문구 → 이유 → 그 근거</b>.
 *
 * <p>근거는 두 갈래다. <b>시장 근거</b>면 화면 1 의 과목으로, <b>법률</b>이면 아래 법률
 * 검토의 그 조항으로 간다. 어느 쪽도 없으면 배지로 그렇게 말한다 — 근거 없는 변경을
 * 이유만으로 그리면 「조사가 시킨 일」처럼 읽힌다.
 */
function Change({ change, no, landed, evidenceSubjects, evidenceById, onJumpSubject,
    selectable = false, checked = false, onToggle = () => {} }) {
  const ids = change.evidenceIds ?? [];
  const subject = ids.map((id) => evidenceSubjects?.get(id)).find(Boolean) ?? null;
  const number = subject ? subjectNumber(subject) : null;
  // ⚠ 법률 카드를 뺐으므로 «그 조항으로 건너뛰는 자리»도 없다(2026-08-16). 그래도
  //   법률이 시킨 변경을 「근거 없음」으로 떨어뜨리지는 않는다 — 근거가 없는 것이
  //   아니라 그 근거를 이 화면이 안 펴는 것이다.
  const fromLegal = change.source === 'LEGAL';

  return (
    <div id={`cr-why-${no}`} className={`cr-why${landed ? ' is-on' : ''}`}>
      {/* 서술문이 가리키는 번호. 변경표 순번(위 `cr-why-`)과 **일부러 다른 이름**이다 —
          서술문은 채택분만 세고 변경표는 전량을 그린다. */}
      {change.narrativeRef ? <span id={`cr-nref-${change.narrativeRef}`} /> : null}
      <p className="cr-why__h">
        <span className="cr-tag">{no}</span>
        {/* 제목이 없으면(이 칸이 생기기 전 라운드) 필드 라벨로 떨어진다 — 거짓이 되지 않는다. */}
        {selectable ? (
          <Checkbox checked={checked} onChange={() => onToggle(change.field)}
            label={평문(change.title) || REVISION_FIELD_LABEL[change.field] || change.field} />
        ) : (
          <b><Emphasis text={평문(change.title) || REVISION_FIELD_LABEL[change.field] || change.field} /></b>
        )}
        {/* 답이 끝난 뒤에는 «내가 무엇을 넘겼는지»도 보여야 한다 — 목록에서 지우면
            「거절한 제안이 있었다」는 사실 자체가 사라진다. */}
        {change.accepted === false ? <Badge tone="neutral">넘김</Badge> : null}
        {change.accepted === true ? <Badge tone="success">반영함</Badge> : null}
      </p>
      {/* ⚠ 판 ㊻ — **별표를 글자로 내보내지 않는다.** 모델이 쓴 문장에 `**강조**` 가 섞여
          오는데 그대로 찍어서 「**소형 마트 입점으로 접근성 강화*」처럼 보였다.
          짝이 안 맞는 별표까지 지우려고 `평문()` 을 한 번 더 씌운다 — `Emphasis` 는
          «짝이 맞는» 것만 처리하고 남은 하나는 글자로 남기기 때문이다. */}
      <p className="cr-diff">
        <span className="cr-old"><Emphasis text={평문(change.before) || '(비어 있었어요)'} /></span>
        {' → '}<b><Emphasis text={평문(change.after)} /></b>
      </p>
      <p className="cr-why__r"><Emphasis text={평문(change.reason)} /></p>
      {fromLegal ? (
        <Badge tone="neutral">법률 검토가 시킨 변경</Badge>
      ) : ids.length === 0 ? (
        <Badge tone="warning">근거 없음</Badge>
      ) : subject ? (
        // ⚠ **착지는 «접힌 뒤의 자리»로 보낸다** (판 ㊺). 성장률·계산은 1절 안으로,
        //    「찾지 못한 것」은 8절로 접혔다 — 옛 이름 그대로 보내면 `sec-GROWTH` 가
        //    없어서 **화면만 바뀌고 아무 데도 안 간다.** 이름은 그대로 보여 준다(어느
        //    과목의 근거인지가 사업가에게 필요한 정보다).
        <button type="button" className="cr-from" onClick={() => onJumpSubject(sectionAnchor(subject))}>
          근거 보기 — 시장 분석 {number}. {SUBJECT_LABEL[subject] ?? subject}
          {ids.map((id) => <code key={id} className="cr-ev">{id}</code>)}
        </button>
      ) : (
        <p className="cr-why__r">
          {ids.map((id) => <code key={id} className="cr-ev">{id}</code>)}
          {' '}이 근거는 지금 화면의 조사 결과에 없어요.
        </p>
      )}

      {/* ★ **근거 원문.** 기계는 「그 번호가 목록에 있나」만 본다 — 그 근거가 이 제안의
          «방향»을 받치는지는 **사람만 읽을 수 있다**. 실측(`p47-refine-01`)에서 기계 검사를
          통과한 제안 하나는 「시장 규모 38조」로 «차별점»을 바꾸자는 것이었고, 그 근거에는
          「음·식료품 전체다 · 상한으로만 읽어라」는 경계가 붙어 있었다. */}
      {evidenceById && ids.length > 0 ? (
        <ul className="cr-src">
          {ids.map((id) => <EvidenceLine key={id} id={id} item={evidenceById.get(id)} />)}
        </ul>
      ) : null}
    </div>
  );
}

/**
 * 근거 한 줄 — <b>인용문 · 누가 쟀나 · 언제·무엇을 · 경계</b>.
 *
 * <p>⚠ 「누가 쟀나」를 {@code issuer} 로 쓰지 않는다. 실측에서 <b>200건 중 0건</b>이었다.
 * 대신 출처 종류와 주소의 집 이름을 쓴다(199/200) — 통계청인지 블로그인지가 인용문만큼 중요하다.
 */
function EvidenceLine({ id, item }) {
  if (!item) {
    return (
      <li className="cr-src__x">
        <code className="cr-ev">{id}</code> 이 근거는 지금 화면의 조사 결과에 없어요.
      </li>
    );
  }
  const kind = SOURCE_KIND_LABEL[item.sourceKind] ?? item.sourceKind;
  const host = hostOf(item.sourceUrl);
  const 어디것 = [kind, host].filter(Boolean).join(' · ');
  const 무엇을 = [item.subject, item.period].filter(Boolean).join(' · ');
  return (
    <li>
      {/* 계산으로 만든 근거는 인용문이 없다(200건 중 1건). 그때 「근거 있음」 배지만 서면
          읽을 것이 없는 체크박스가 된다 — 무엇이었는지를 대신 적는다. */}
      {item.quote
        ? <span className="cr-src__q">「{item.quote}」</span>
        : (
          <span className="cr-src__q">
            {[item.metric, item.value != null ? `${item.value}${item.unit ?? ''}` : null]
              .filter(Boolean).join(' ')}
            {' '}— 이 근거는 계산으로 만든 값이라 원문이 없어요.
          </span>
        )}
      {어디것 || 무엇을 ? (
        <span className="cr-src__m">{[어디것, 무엇을].filter(Boolean).join(' · ')}</span>
      ) : null}
      {/* ⚠ **경계 표시 — 빼지 마라.** 「19.7%」가 우리 세그먼트가 아니라 전체 간편식
          기준이라는 말이 여기로 온다. 안 펴면 그럴듯한 근거가 그대로 통과한다. */}
      {(item.caveats ?? []).map((line) => (
        <span key={line} className="cr-src__w">⚠ {line}</span>
      ))}
      {/* ⚠ `placement` 는 `caveats` 와 **별개 필드**다. 재료 200건 중 **147건(74%)**이
          서랍이었다 — 조사가 결론에 안 쓴 자료가 경계 없이 오면 멀쩡해 보인다. */}
      {item.placement === '밖' ? (
        <span className="cr-src__w">⚠ 조사가 결론에 쓰지 않은 참고 자료예요.</span>
      ) : null}
    </li>
  );
}

