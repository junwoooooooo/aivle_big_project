import { useState } from 'react';
import { Alert, Badge, Card } from '../../shared/ui';
import { GradeBadge, SourceLink } from './BmCanvas.jsx';
import AssumptionLedger from './AssumptionLedger.jsx';
import Emphasis from './emphasis.jsx';
import Markdown from './markdown.jsx';
import {
  NOT_FOUND_GROUP, SCORE_STATE_VIEW, SECTION_TITLE, SUBJECT_LABEL,
  competitorGaps, drawerFacts, factName, formatValue, headFacts, hostOf,
  sectionEvidence,
} from './marketResult.js';
import './market.css';

/**
 * 사업 검증의 <b>첫째 걸음</b> — 시장조사 결과.
 *
 * <p><b>성적표 과목이 곧 목차다.</b> 성적표를 맨 아래 접어 두면 「무엇을 쟀나」와
 * 「무엇이 나왔나」가 따로 놀아, 읽는 사람이 빠진 과목을 못 본다.
 *
 * <p>과목은 <b>카드 하나 안의 접히는 줄</b>이다(와이어프레임 정본). 줄은 번호·제목·
 * 상태·한 줄 요약·「근거 N건」이고, 펼치면 지금까지의 표·근거·출처가 그 자리에 나온다.
 * 과목마다 카드를 세우면 첫 화면이 열 장으로 불어나 목차 구실을 못 한다.
 *
 * <p>판 ㊸ 에서 <b>7 → 10 과목</b>이 됐다(채널·원가/수익성·규제). 그 아래에 사람 보고서의
 * 2·8·9절이 선다 — 가격 판단 · 처방 · 지지/흔듦.
 *
 * <p>셸(제목·실행 버튼·진행 표시)은 갖지 않는다. `BusinessValidationPage` 가 갖는다.
 */
// ⚠ `onJump` 은 더 받지 않는다 — 그것을 쓰던 것은 머리의 결론 타일(`Kpis`)뿐이었고
//   판 ㊻ 에서 그 타일을 내렸다. 부르는 쪽이 계속 넘겨도 무해하다.
export function MarketResultBody({ result, activeId }) {
  const market = result.market ?? {};
  // 절 배치는 **서버가 정한다**(판 ㊸). 옛 결과만 화면이 옛 셈으로 물러선다.
  // 절 배치는 서버가 정하고, **줄 세우기는 여기서** 한다 — 절 머리를 앞에, 서랍을 뒤에.
  // ⚠ 아무것도 버리지 않는다. 순서만 바꾼다 — 「버리는 자리는 질문과 게재뿐」.
  const bag = Object.fromEntries(Object.entries(sectionEvidence(result))
    .map(([key, rows]) => [key, [...headFacts(rows), ...drawerFacts(rows)]]));
  const score = Object.fromEntries((result.scorecard ?? []).map((row) => [row.subject, row]));
  const cited = (ids) => ids.map((id) => result.evidenceById.get(id)).filter(Boolean);
  const priceCited = cited(market.price?.evidenceIds ?? []);
  const notFound = market.notFound ?? [];

  // 한 번에 한 과목만 편다. **KPI 착지가 그 과목을 «펼치면서» 내려앉아야** 하므로
  // 펼침 상태는 바깥의 `activeId` 와 묶인다 — 착지했는데 접혀 있으면 아무 일도 안 한 것처럼 보인다.
  // ⚠ effect 로 맞추지 않는다(렌더 → effect → 재렌더로 한 프레임 늦게 열린다).
  //    렌더 중 조정은 React 가 권하는 «prop 이 바뀔 때 state 조정» 패턴이다.
  const [open, setOpen] = useState(null);
  const [seenActive, setSeenActive] = useState(null);
  if (activeId !== seenActive) {
    setSeenActive(activeId);
    if (activeId) setOpen(activeId);
  }

  // 본문은 «열렸을 때만» 만든다 — 열 과목의 표를 늘 그려 두면 접힌 화면이 그만큼 무거워진다.
  //
  // ★ **순서와 묶음은 목표 보고서를 따른다** (판 ㊺ · `docs/market-research-redesign/TARGET_REPORT.md`).
  //   바뀐 것 셋:
  //   ① **성장률·시장 규모 계산을 1절 «안으로» 접었다.** 목표 보고서 1절이 규모와 성장률을
  //      한 표에 놓는다 — 사업가는 「얼마나 크고 얼마나 크나」를 한 번에 묻지 따로 묻지 않는다
  //   ② **가격이 2절로 올라오고 판단이 그 «안»에 든다.** 「값」과 「그래서 어디에 서 있나」가
  //      떨어져 있으면 표를 다 읽고 나서야 판단을 만난다
  //   ③ **「찾지 못한 것」 건수 나열을 버렸다.** 「못 채운 것 41건 · 가정 7건 · 걸러낸 것 9건」은
  //      **엔진 장부이지 사업가의 물건이 아니다.** 그 자리에 8절 처방(무엇/왜/**어디서**)이 선다 —
  //      경계 표시를 «없앤» 것이 아니라 **행동할 수 있는 모양으로 바꾼** 것이다
  const sections = [
    {
      subject: 'MARKET_SIZE',
      count: bag.MARKET_SIZE.length + bag.GROWTH.length + bag.CALCULATION.length,
      // 계산 카드가 0장이어도 가정 원장이 있으면 펼 것이 있다.
      openable: bag.MARKET_SIZE.length + bag.GROWTH.length + bag.CALCULATION.length > 0
        || Boolean(market.growth) || hasLedger(market),
      body: () => (
        <>
          {/* ★ 판 ㊻ — **규모와 성장률을 «한 표»에 놓는다.**
              목표 보고서 1절이 그렇다(`TARGET_REPORT.md`) — 「간편식 판매액 6조 1,013억」
              바로 아래에 「+4.2%」가 붙어야 사업가가 두 수를 한 번에 읽는다. 표를 갈라
              놓으면 규모 표를 다 읽고 나서 성장률 표를 다시 읽어야 한다.
              ⚠ 6행으로 자른다 — 목표 보고서 1절 표가 6행이다. 자른 것은
              **「나머지 N건 더 보기」에 그대로 있다**(`EvidenceTable` 이 접는다). */}
          {bag.MARKET_SIZE.length + bag.GROWTH.length > 0
            ? <EvidenceTable rows={[...bag.MARKET_SIZE, ...bag.GROWTH]} limit={6} />
            : <p className="bm-cell__none">모집단 관측이 없어요.</p>}
          {/* ★ 판 ㊺ — **엔진 장부는 접는다.** 「이 숫자를 읽는 조건」(가정 원장)과 관측
              판정 표는 <b>검산하려는 사람</b>의 물건이지 절을 읽는 사람의 물건이 아니다.
              실측(2026-08-15 사용자 대조): 1절을 열면 표 아래로 판정 표가 이어져
              **목표 보고서엔 없는 화면 두 배**가 붙었다.
              ⚠ **지우지 않는다** — 접어 두고 펴면 그대로 다 있다(경계 문장도 그 안에 산다). */}
          <details className="mr-more">
            <summary>이 숫자를 어떻게 셌는지 — 계산 조건과 관측 판정</summary>
            {/* ★ 판 ㊻ — **계산으로 만든 성장률을 여기로 내렸다.**
                「연 성장률 15.146 %/년」이 절 머리에 큰 글씨로 서 있었는데, 이 값은
                두 해 관측을 직선으로 이어 «화면이» 만든 수지 조사가 가져온 수가 아니다.
                자릿수(15.146)도 관측의 정밀도를 넘어선다 — 결함으로 이미 기록돼 있다.
                ⚠ **지우지 않는다.** 산출식과 경계 문장(「과거 관측이고 미래 성장을 뜻하지
                않는다」)이 이 안에 통째로 살아 있고, 펴면 그대로 다 보인다.
                근거에서 «온» 성장률은 위 표에 다른 행들과 나란히 선다. */}
            <GrowthBody growth={market.growth} rows={[]} />
            <AssumptionLedger market={market} />
            <CalcBody cards={bag.CALCULATION} />
          </details>
        </>
      ),
    },
    {
      subject: 'PRICE',
      // ⚠ 승격된 가격 사실을 안 그리면 **판단의 근거를 검산할 자리가 없다** —
      //    「6,513원의 1.37배」의 그 6,513원이 바로 이 목록 안에 있다.
      count: priceCited.length + bag.PRICE.filter(승격).length,
      openable: Boolean(market.price) || bag.PRICE.some(승격) || Boolean(result.judgment),
      // ⚠ **판단을 접힌 안에만 두지 않는다.** 옛 화면은 판단 카드를 목차 «위»에 세웠고
      //    그 이유가 「표를 다 읽고 나서야 판단을 만나면 늦다」였다 — 그 이유는 지금도 옳다.
      //    목차 모양은 목표 보고서를 따르되, **결론 한 줄은 접힌 채로도 보이게** 요약줄에 올린다.
      detail: result.judgment?.conclusion ?? undefined,
      body: () => (
        <>
          {/* 2절 — 값을 보여 주는 것과 **「그래서 어디에 서 있나」를 말해 주는 것**은 다른 일이다.
              ★ 눈금자가 «맨 위»다 — 목표 보고서가 그렇고, 「1.37배」를 글로 읽기 전에
                자 위에서 보는 것이 이 절에서 사업가가 실제로 사는 것이다. */}
          <PriceScale judgment={result.judgment} rows={bag.PRICE} />
          <PriceStance judgment={result.judgment} price={market.price} />
          <JudgmentCard judgment={result.judgment} bare />
          <PriceBody price={market.price} cited={priceCited} />
          <TableAwareBody rows={bag.PRICE.filter(승격)} empty="" />
        </>
      ),
    },
    {
      subject: 'COMPETITOR',
      count: bag.COMPETITOR.length,
      // ⚠ **승격 카드를 `CompetitorBody` 에 넣지 않는다.** 그 부품은 `subject` 를 **회사
      //    이름**으로 보고 카드를 세우는데, 절 사실의 `subject` 는 「2025년 당기 매출액
      //    (오뚜기제유)」 같은 **계량 서술**이다 — 그대로 넣으면 41장짜리 **가짜 회사 목록**이
      //    선다. 승격분은 표로 그리고 발행사로 묶는다.
      body: () => (
        <>
          <CompetitorBody rows={bag.COMPETITOR.filter((r) => !승격(r))}
            gaps={competitorGaps(notFound)} />
          <IssuerTables rows={bag.COMPETITOR.filter(승격)} />
        </>
      ),
    },
    // ── 판 ㊸ — 절 체인이 채우는 세 과목 ────────────────────────
    // ⚠ 표를 **찢지 않는다.** 구성비 표는 합이 100인데 절반만 보이면 1위가 뒤바뀐다 —
    //    실측으로 채널 절 합이 47%였고 숨은 특약점 29.65%가 1위 대형마트 31.05%와 대등했다.
    {
      subject: 'CHANNEL',
      count: bag.CHANNEL.length,
      body: () => <TableAwareBody rows={bag.CHANNEL} empty="채널별 비중을 못 구했어요." />,
    },
    {
      subject: 'DEMAND',
      count: bag.DEMAND.length,
      body: () => <EvidenceTable rows={bag.DEMAND} quote />,
    },
    {
      subject: 'UNIT_ECONOMICS',
      count: bag.UNIT_ECONOMICS.length,
      body: () => <TableAwareBody rows={bag.UNIT_ECONOMICS} empty="원가·수익성 사실을 못 구했어요." />,
    },
    {
      subject: 'REGULATION',
      count: bag.REGULATION.length,
      body: () => <TableAwareBody rows={bag.REGULATION} empty="지켜야 할 기준치를 못 구했어요." />,
    },
    // ── 판 ㊺ — 목표 보고서의 8·9절. **성적표 과목이 아니라 판정 배지가 없다.** ──
    {
      subject: 'GAPS',
      // ★ **처방이 없으면 옛 「찾지 못한 것」으로 물러선다** (판 ㊺ 감사에서 잡힌 자리).
      //   목차에서 `NOT_FOUND` 절을 걷어내면서 `market.notFound` 가 화면 어디에도 안 가게
      //   됐는데, 처방까지 `null` 이면 **「무엇을 못 구했나」가 통째로 사라진다.**
      //   건수 나열이 사업가의 물건이 아닌 것은 맞지만, **아무것도 없는 것보다는 낫다.**
      count: result.prescriptions?.length ?? notFound.reduce((sum, b) => sum + b.count, 0),
      openable: Boolean(result.prescriptions?.length) || notFound.length > 0,
      // 「못 구했다」로 끝내면 사업가는 거기서 멈춘다. **어디서 구하는지**까지 적는다.
      detail: result.prescriptions?.length
        ? `${result.prescriptions.length}가지 — 어디서 구하는지까지 적었어요`
        : notFound.length
          ? '이번 실행은 «어디서 구하나»까지 못 적었어요 — 못 구한 목록만 있어요'
          : '이번 실행은 처방을 못 만들었어요 — 없다는 뜻이 아니에요',
      // ★ 판 ㊻ — **8·9절이 «두 벌»이었다.** (2026-08-16 사용자 지시)
      //   기계가 원장에서 뽑은 처방 표와 AI 가 쓴 「못 구한 것」 글이 같은 절에 나란히
      //   서서 **같은 말을 두 번** 했다. 글이 왔으면 기계 표는 접이식 안에서도 빼고,
      //   글이 없을 때만 기계 표가 그 자리를 지킨다.
      //   ⚠ 처방 자체를 지운 것이 아니다 — 봉투에 그대로 오고, 글이 없으면 그대로 뜬다.
      body: () => (hasProse(result.report, 'GAPS')
        ? null
        : result.prescriptions?.length
          ? <PrescriptionCard rows={result.prescriptions} bare />
          : <NotFoundBody blocks={notFound} />),
    },
    {
      subject: 'SYNTHESIS',
      count: result.synthesis?.length ?? 0,
      openable: Boolean(result.synthesis?.length),
      detail: result.synthesis?.length
        ? '사실이 이 사업안을 미는지 흔드는지'
        : '이번 실행은 9절을 못 만들었어요 — 없다는 뜻이 아니에요',
      // 8절과 같은 이유로 뺀다 — 「미는 것 / 흔드는 것」을 기계와 글이 각각 한 번씩 썼다.
      body: () => (hasProse(result.report, 'SYNTHESIS')
        ? null
        : <SynthesisCard rows={result.synthesis} bare />),
    },
  ];

  return (
    <>
      {/* ★ 판 ㊻ — **머리의 결론 타일 셋을 내렸다** (2026-08-16 사용자 지시).
          왜 내렸나. 셋 다 사업가에게 답을 주지 못했다 —
          ① 「전체 시장(TAM) 미확보 · 근거 없음」 — 「모른다」를 큰 글씨로 세운 칸
          ② 「연 성장률 15.146 %/년 · 확정」 — 두 해 관측을 직선으로 이어 **화면이 만든**
             수인데 도장은 「확정」이었다. **거짓 도장이다**
          ③ 「시장 가격대 2,400~5,900원」 — 같은 값이 두 줄로 반복됐다
          ⚠ 값 자체는 안 지운다 — 성장률과 산출식·경계는 1절의 「이 숫자를 어떻게
            셌는지」에, 가격대는 2절 눈금자에 그대로 있다. 답은 이제 절 안의 글이 한다. */}

      {/* 「이 숫자의 기준」 — 어떤 모집단을 재고 있는지. **경계 문장이라 접지 않는다.** */}
      {market.coverageCaveat ? (
        <Card className="mr-basis">
          <p><b>이 숫자의 기준</b> — <Emphasis text={market.coverageCaveat} /></p>
        </Card>
      ) : null}

      {/* 판 ㊻ — **「이 조사가 다 돌지 못했어요」 상자를 뺐다**(2026-08-16 사용자 지시).
          담고 있던 것은 「요약 문장이 검사를 통과하지 못해 버렸어요」·「검사 미통과 3회 —
          fail-closed」처럼 **엔진의 내부 사정**이었다. 사업가가 그걸로 정할 것이 없다.
          ⚠ **잃는 것을 적어 둔다** — 어떤 단계가 격하됐는지(`degradations`)를 화면에서
            알 길이 지금은 없다. 봉투에는 그대로 오고, 되살릴 자리는 검산 페이지다. */}

      {/* 판 ㊻ — **「서랍 20건까지만 실었다」 안내를 뺐다**(사용자 지시). 실패가 아니라
          정상 동작의 고지이고, 같은 말이 절마다 「이 절의 근거 N건 중 M건을 실었고
          나머지는 …」로 **다시 한 번** 나온다 — 절 안의 그 줄이 더 가깝고 구체적이다.
          ⚠ 접힌 근거가 원장에 그대로 있다는 사실은 그 줄이 계속 말한다. */}

      {/* ⚠ 판 ㊺ — 가격 판단·처방·9절이 **목차 «안»으로** 들어갔다(2·8·9절).
          목표 보고서가 그렇게 쓰고, 밖에 두면 목차가 아홉인데 카드가 셋 더 서서
          「이게 몇 절인가」를 사업가가 셀 수 없다. */}
      <Card className="mr-subs">
        {sections.map((section, index) => {
          const openable = section.openable ?? section.count > 0;
          const isOpen = openable && open === section.subject;
          return (
            <Subject
              key={section.subject}
              n={index + 1}
              subject={section.subject}
              row={score[section.subject]}
              detail={section.detail}
              count={section.count}
              openable={openable}
              open={isOpen}
              focused={activeId === section.subject}
              onToggle={() => setOpen((current) => (current === section.subject ? null : section.subject))}
              lead={headFacts(bag[section.subject])}
            >
              {isOpen ? (
                <>
                  {/* ★ 판 ㊻ — **엔진이 쓴 절 글을 절 «안»에 넣는다.**
                      봉투의 `report.sections[]` 는 이미 절마다 「무엇이 얼마나 크고 어디로
                      가는가」를 표와 문장으로 써 놓았는데, 화면이 그것을 안 쓰고 근거 표만
                      그렸다. 그래서 사업가가 보는 것은 **값의 목록**이지 답이 아니었다.
                      ⚠ 글이 «위», 근거 표가 «아래»다 — 표를 다 읽고 나서야 답을 만나면 늦다.
                      ⚠ 글이 안 온 절은 아무것도 안 그린다. 빈 자리를 문장으로 메우지 않는다. */}
                  <ReportProse report={result.report} subject={section.subject} />
                  {/* ★ 판 ㊻ — **절 안에는 보고서 글만 편다.** (2026-08-16 사용자 지시)
                      근거 표·등급 배지·경계 상자·「합이 100%가 아니다」·가정 원장은
                      **검산하려는 사람의 물건**이라 접어 둔다. 절을 여는 사람은 먼저
                      「이 절의 답이 무엇인가」를 묻지 「출처가 무엇인가」를 묻지 않는다.
                      ⚠ **지우지 않는다.** 이 접이식이 이 서비스의 값어치다 —
                      목표 보고서에 «없는» 것이 바로 이 검산 자리다.
                      ⚠ 글이 안 온 절에서는 접지 «않는다» — 접으면 빈 절이 된다. */}
                  {/* ★ 판 ㊻ — **글이 온 절에서는 절 안에 글만 둔다** (2026-08-16 사용자 지시).
                      근거 표·등급 배지·경계 상자·가정 원장은 접이식 안에 있어도 절을
                      두 배로 늘렸다. 값과 출처는 **글 안의 표가 행마다 이미 들고 있다.**
                      ⚠ **잃는 것을 적어 둔다** — 「인용 대조를 통과한 값」·등급·
                        「합이 100%가 아니다」를 이 화면에서 확인할 길이 지금은 없다.
                        되살릴 자리는 절 «안»이 아니라 화면 맨 아래 검산 페이지 한 장이다. */}
                  {hasProse(result.report, section.subject) ? null : section.body()}
                </>
              ) : null}
            </Subject>
          );
        })}
      </Card>

      {/* 요약은 예산이 모자라면 오지 않는다. 왔을 때만 그린다 — 건너뛴 사유는 실행 기록에 있다. */}
      {result.summary?.length ? (
        <Card title="핵심 요약">
          <ul className="market-summary">
            {result.summary.map((line) => (
              <li key={line.sentence}>
                {line.cell ? <span className="market-summary__cell">{line.cell}</span> : null}
                {line.sentence}
                {line.cardIds.map((id) => <code key={id}>{id}</code>)}
              </li>
            ))}
          </ul>
        </Card>
      ) : null}
    </>
  );
}


/**
 * 2절 — <b>가격 판단.</b> 기계가 계산한 문장이고 모델이 쓴 것이 아니다.
 *
 * <p>⚠ <b>결론을 빼지 마라.</b> 계산식만 남으면 「1.37배」에서 끝나고 사업가가 사는 것인
 * 「그래서 어느 쪽으로 팔라」가 사라진다. 비교쌍이 안 갖춰져 <b>못 쓴 갈래</b>도 같이
 * 세운다 — 침묵을 「해당 없음」으로 읽히게 두지 않는다.
 */
/**
 * ★ 판 ㊺ — <b>「내 가격이 조사된 범위 안인가 밖인가」를 한 줄로 말한다.</b>
 *
 * <p>실측(2026-08-15 화면): 시장 가격대가 <b>2,400~5,900원</b>인데 컨셉 판매가는
 * <b>8,900원</b>이었다. 즉 우리 가격은 <b>조사된 어떤 물건보다도 비싼데</b> 화면은
 * 그 사실을 한 마디도 하지 않았다. 두 수가 나란히 떠 있을 뿐이었다.
 *
 * <p>⚠ <b>새 판정을 만드는 것이 아니다.</b> 두 수는 이미 봉투에 있다
 * (`judgment.price` · `market.price.min/max`). 화면이 <b>둘을 견주지 않았을 뿐</b>이다.
 * 그래서 서버를 안 건드린다.
 *
 * <p>⚠ 「밖」을 <b>나쁘다고 말하지 않는다.</b> 프리미엄은 전략이지 결함이 아니다 —
 * 말할 것은 「그러면 이유가 서야 한다」까지다.
 */
/**
 * <b>가격 눈금자</b> — 목표 보고서 §2 의 `.scale` 그대로.
 *
 * <p>「8,900원이 6,513원의 1.37배」를 <b>글로 읽는 것</b>과 <b>자 위에서 보는 것</b>은 다르다.
 * 목표 보고서가 이 절을 눈금자로 여는 이유이고, 사업가가 2절에서 실제로 사는 것이 이것이다.
 *
 * <p>⚠ <b>새 값을 만들지 않는다.</b> 컨셉가는 `judgment.price`, 비교 대상은 이 절의 근거 중
 * <b>원 단위 한 점짜리</b>만 쓴다(범위·비율은 자 위에 점을 못 찍는다).
 * <p>⚠ 값이 둘 미만이면 <b>그리지 않는다</b> — 점 하나짜리 자는 척도가 아니라 장식이다.
 */
export function PriceScale({ judgment, rows }) {
  const mine = judgment?.price;
  if (typeof mine !== 'number') return null;

  const 점 = [];
  for (const item of rows) {
    const 원 = typeof item.value === 'number' && /원/.test(String(item.unit ?? item.raw ?? ''));
    if (!원 || item.value <= 0) continue;
    점.push({ id: item.id, name: item.metric, value: item.value });
  }
  // 같은 값이 여럿이면 하나만 — 자 위에서 글자가 겹친다.
  const 고른 = [...new Map(점.map((p) => [Math.round(p.value), p])).values()]
    .sort((a, b) => a.value - b.value);
  if (고른.length < 2) return null;

  // ⚠ **비교가 되는 값만 올린다.** 실측: 「100원」(배달 할증)과 「20,000원」이 같이 올라
  //   3,900·5,500·6,000 이 왼쪽 끝에 뭉쳐 **라벨이 겹쳐 못 읽었다.** 한 끼 값과 비교가
  //   안 되는 수는 자 위에서 자리만 먹는다.
  const 곁 = 고른.filter((p) => p.value >= mine * 0.3 && p.value <= mine * 2.5);
  if (곁.length < 2) return null;

  // 자에 올릴 것은 **넷까지** — 목표 보고서도 내 것 빼면 넷이다.
  const 보일 = 곁.length <= 4 ? 곁
    : [곁[0], 곁[Math.round((곁.length - 1) / 3)],
       곁[Math.round(((곁.length - 1) * 2) / 3)], 곁[곁.length - 1]];

  // ★ **값이 아니라 «차례»로 자리를 준다.** 목표 보고서가 그렇다 —
  //   6,513원이 33%, 6,500원이 56%에 있다(값으로는 거의 같은 자리인데도).
  //   자의 일은 「얼마나 비싼가」가 아니라 **「누가 위고 누가 아래인가」**를 보이는 것이고,
  //   값으로 찍으면 붙은 값들이 겹쳐 그 일을 못 한다.
  const 줄 = [...보일.map((p) => ({ ...p, me: false })),
              { id: '_me', name: '', value: mine, me: true }]
    .sort((a, b) => a.value - b.value);
  const pct = (i) => `${(6 + (i / Math.max(1, 줄.length - 1)) * 88).toFixed(1)}%`;
  const won = (n) => `${Math.round(n).toLocaleString('ko-KR')}원`;

  return (
    <div className="mr-scale">
      <div className="mr-bar">
        {줄.map((p, i) => (
          <div key={p.id} className={`mr-tick${p.me ? ' is-me' : ''}`} style={{ left: pct(i) }}>
            {p.me ? null : <span className="mr-tick__n">{p.name}</span>}
            <b className="num">{p.me ? `내 컨셉 ${won(p.value)}` : won(p.value)}</b><i />
          </div>
        ))}
      </div>
    </div>
  );
}

function PriceStance({ judgment, price }) {
  const mine = judgment?.price;
  const lo = price?.min;
  const hi = price?.max;
  if (typeof mine !== 'number' || typeof lo !== 'number' || typeof hi !== 'number') return null;
  const won = (n) => `${Math.round(n).toLocaleString('ko-KR')}원`;
  const 밴드 = `${won(lo)}~${won(hi)}`;
  if (mine > hi) {
    return (
      <Alert tone="warning">
        컨셉가 <b className="num">{won(mine)}</b>은 조사된 범위(<span className="num">{밴드}</span>)
        <b> 위</b>예요 — 이 범위의 물건 대신 고를 이유가 서야 해요.
      </Alert>
    );
  }
  if (mine < lo) {
    return (
      <Alert tone="warning">
        컨셉가 <b className="num">{won(mine)}</b>은 조사된 범위(<span className="num">{밴드}</span>)
        <b> 아래</b>예요 — 원가가 남는지 6절에서 같이 보세요.
      </Alert>
    );
  }
  return (
    <Alert tone="info">
      컨셉가 <b className="num">{won(mine)}</b>은 조사된 범위(<span className="num">{밴드}</span>)
      <b> 안</b>이에요.
    </Alert>
  );
}

export function JudgmentCard({ judgment, bare = false }) {
  if (!judgment) return null;
  return (
    <Shell bare={bare} title="이 가격이 시장 어디에 서 있나" className="mr-judgment">
      {judgment.conclusion ? (
        <Alert tone="info">
          <Emphasis text={judgment.conclusion} />
          {/* ⚠ **결론 자리에 연도가 없으면 오늘 값처럼 읽힌다.** 「배달과 8% 근소」의
              자장면값이 2018년인데, 그 사실이 작은 근거 줄에만 있으면 사업가는 못 본다.
              새 판정이 아니라 **이미 있는 값을 결론 옆으로 옮기는 것**이다. */}
          <ConclusionYears lines={judgment.lines} />
        </Alert>
      ) : (
        <p className="bm-cell__none">
          비교쌍이 갖춰지지 않아 결론을 쓰지 않았어요. <b>지어내지 않습니다.</b>
        </p>
      )}
      <ul className="mr-judgment__lines">
        {judgment.lines.map((line) => (
          <li key={line.what}>
            <b>{line.what}</b>
            {line.sentence ? (
              <>
                <p><Emphasis text={line.sentence} /></p>
                {line.formula ? <p className="mr-judgment__calc num">계산: {line.formula}</p> : null}
              </>
            ) : (
              <p className="bm-cell__none">
                (안 씁니다) <Emphasis text={line.silentBecause ?? ''} />
              </p>
            )}
            {line.sources.map((s) => (
              <p key={`${s.raw}-${s.subject}`} className="mr-judgment__src">
                <span className="num">{s.raw}</span> «{s.subject}
                {s.period ? ` · ${s.period}` : ' · 연도 없음'}»
                {s.url ? <> <a href={s.url} target="_blank" rel="noreferrer">출처</a></> : null}
              </p>
            ))}
          </li>
        ))}
      </ul>
    </Shell>
  );
}

/** 8절 — <b>처방.</b> 셋째 열(「어디서」)이 이 표의 값어치다. */
export function PrescriptionCard({ rows, bare = false }) {
  if (!rows || rows.length === 0) return null;
  return (
    <Shell bare={bare} title="못 구한 것 — 어디서 구하나" className="mr-rx">
      <table className="mr-table">
        <thead>
          <tr><th>과목</th><th>무엇을 못 구했나</th><th>왜 필요한가</th><th>어디서 구하나</th></tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={`${row.section}-${row.what}`}>
              <td>
                <b>{SUBJECT_LABEL[row.section] ?? row.section}</b>
                <div className="mr-rx__kind">{row.kindLabel}</div>
              </td>
              <td><Emphasis text={row.what} /></td>
              {/* ⚠ 셋 중 이 칸만 `Emphasis` 를 안 거쳐 **별표가 글자로 찍혔다**(화면 실측
                  2026-08-15): 「**어디를 볼지 적는다**」. 3층 테스트는 문자열을 그대로
                  비교하므로 이 부류를 **구조적으로 못 잡는다** — 눈으로만 잡힌다. */}
              <td><Emphasis text={row.why} /></td>
              <td><Emphasis text={row.where} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </Shell>
  );
}

/**
 * 절 «안»에 들어가면 제목 카드가 겹친다 — 그때는 껍데기를 벗는다.
 *
 * <p>⚠ 카드를 통째로 없애지 않고 껍데기만 벗기는 이유: 이 부품들은 목차 밖에서도 쓰인다
 * (와이어프레임 · 옛 결과 화면). <b>한 벌로 두 자리를 섬긴다.</b>
 */
function Shell({ bare, title, className, children }) {
  return bare ? <div className={className}>{children}</div>
    : <Card title={title} className={className}>{children}</Card>;
}

/**
 * 9절 — <b>지지 / 흔듦.</b> 갈래와 근거는 기계가 정하고 모델은 문장만 쓴다.
 * 검사에서 버려진 문장은 서버에서 이미 빠진다 — <b>버린 것을 여기 올리면
 * 「검사를 했다」가 「검사를 통과했다」로 읽힌다.</b>
 */
export function SynthesisCard({ rows, bare = false }) {
  if (!rows || rows.length === 0) return null;
  const 지지 = rows.filter((row) => row.stance === '지지');
  const 흔듦 = rows.filter((row) => row.stance !== '지지');
  // ⚠ **빈 갈래를 지우지 않는다.** 지우면 「흔드는 사실이 0건이었다」와 「흔듦을 아예
  //    안 쟀다」가 화면에서 같아 보인다 — 성적표 수요 줄에서 방금 고친 것과 **같은 병**이고,
  //    9절은 사업가가 돈을 내는 자리라 더 나쁘다. 실측(2026-08-15): 이 실행의 9절은
  //    「미는 것 3 · 흔드는 것 0」인데 화면에는 미는 것만 서서 **한쪽 말만 들렸다.**
  const 빈무리 = (title, tone) => (
    <div className={`mr-synth__g mr-synth__g--${tone}`}>
      <h4>{title} <span className="num">0</span></h4>
      <p className="mr-synth__none">
        이번 조사에서 <b>{title}</b>에 해당하는 사실은 <b>한 건도 없었어요.</b>
        {' '}못 찾은 것이지 없다는 뜻은 아니에요 — 위 과목별 「미확보」를 같이 보세요.
      </p>
    </div>
  );
  const 무리 = (title, mine, tone) => (mine.length === 0 ? 빈무리(title, tone) : (
    <div className={`mr-synth__g mr-synth__g--${tone}`}>
      <h4>{title} <span className="num">{mine.length}</span></h4>
      <ul>
        {mine.map((row) => (
          <li key={row.key}>
            <Emphasis text={row.sentence} />
            {/* ⚠ **연도를 2절에만 찍고 9절에 안 찍으면** 같은 화면의 두 블록이
                같은 수를 다른 정직도로 말한다. 둘은 같은 뿌리를 공유한다. */}
            <p className="mr-synth__src">
              {row.sources.map((s) => (
                `${s.raw} «${s.subject}${s.period ? ` · ${s.period}` : ' · 연도 없음'}»`
              )).join(' · ')}
            </p>
          </li>
        ))}
      </ul>
    </div>
  ));
  return (
    <Shell bare={bare} title="이 사업안을 미는 것과 흔드는 것" className="mr-synth">
      {무리('미는 것', 지지, 'ok')}
      {무리('흔드는 것', 흔듦, 'warn')}
    </Shell>
  );
}

/**
 * 절 사실 표 — <b>같은 표의 행을 붙여 세우고 합계를 말한다.</b>
 *
 * <p>⚠ 이것이 `tableKey` 가 봉투에 실린 이유다. 구성비 표는 <b>합이 100인데 절반만 보이면
 * 1위가 뒤바뀐다</b>. 실측: 채널 절 합이 47%였고 숨은 특약점 29.65%가 1위 대형마트
 * 31.05%와 대등했다. <b>빈칸은 「못 구했다」고 말하지만 반쪽 표는 아무 말도 안 한다.</b>
 */
function TableAwareBody({ rows, empty }) {
  // `empty` 가 비었으면 **아무것도 안 그린다** — 「없음」 문구를 두 번 세우지 않는다.
  if (!rows || rows.length === 0) return empty ? <p className="bm-cell__none">{empty}</p> : null;
  // ★ 판 ㊻ — **표를 원장의 표대로 쪼개 세우던 것을 그만둔다.** (2026-08-16 사용자 지시)
  //
  // 여기는 `tableKey` 묶음마다 화면 표를 하나씩 세웠다. 그 이유는 「합이 100인 표가
  // 절반만 보이면 1위가 뒤바뀐다」였고 그 이유 자체는 지금도 옳다 — 그런데 대가가
  // 이렇게 나왔다(사용자 화면 실측): **경쟁사 절 하나가 표 14개.** 오뚜기 DART 공시
  // 한 건이 「부문별 매출」·「지역별 매출」·「가동률」·「연구개발비용」·「할랄 인증 취득
  // 품목 수」·「ISO 9001 인증 획득」까지 각자 표가 되어 섰고, 표마다 머리글·경계 두 줄·
  // 「이 표의 값은 모두 확정이에요」가 반복됐다. 「그 자리에 누가 있나」를 묻는 절에
  // **공장 평균가동률 99.00%**와 **박테리오 파지 2종**이 표로 앉는다.
  //
  // 그래서 **한 표로 합친다.** 줄 세우기는 서버가 정한 차례 그대로다.
  // ⚠ **버리는 것이 아니다** — `EvidenceTable` 이 8행까지만 펴고 나머지는
  //   「나머지 N건 더 보기」로 접는다. 펴면 인용문까지 그대로 다 있다.
  // ⚠ **「합이 100%가 아니다」 경고는 잃지 않는다.** 화면 표는 합쳤지만 그 검사는
  //   여전히 «원장의 표» 단위로 하고, 결과만 표 아래에 한 줄씩 세운다. 이 경고가
  //   태어난 사건(채널 절 합이 47%였고 숨은 특약점 29.65%가 1위와 대등했다)은
  //   표를 합친다고 사라지지 않는다.
  const 묶음 = new Map();
  for (const row of rows) {
    if (!row.tableKey) continue;
    if (!묶음.has(row.tableKey)) 묶음.set(row.tableKey, []);
    묶음.get(row.tableKey).push(row);
  }
  return (
    <>
      <EvidenceTable rows={rows} quote />
      {[...묶음.entries()].map(([key, group]) => (
        <PercentSum key={key} rows={group} />
      ))}
    </>
  );
}

/**
 * 결론이 선 <b>근거들의 연도</b>. 셈이 아니라 <b>옮기기</b>다 — 판정을 새로 하지 않는다.
 *
 * <p>왜 결론 옆인가: 「배달과 8% 차이로 근소하다」의 배달값이 2018년 자장면이다.
 * 지금 배달 한 끼가 12,000원대면 그 결론은 <b>「배달보다 확실히 싸다」로 뒤집힌다.</b>
 * 연도가 작은 근거 줄에만 있으면 사업가는 결론만 읽고 지나간다.
 */
function ConclusionYears({ lines }) {
  const years = [...new Set(lines.flatMap((line) => line.sources.map((s) => s.period)))];
  if (years.length === 0) return null;
  const 있 = years.filter(Boolean).sort();
  const 없 = years.some((y) => !y);
  const 문장 = `이 판단이 선 근거의 연도 — ${있.join(' · ') || '없음'}`
    + (없 ? ' · ⚠ **연도를 모르는 근거가 섞여 있다**' : '');
  return <p className="mr-judgment__years"><Emphasis text={문장} /></p>;
}

/** 승격 카드인가. **`placement` 는 절 체인만 붙인다** — 슬롯 카드엔 없다. */
const 승격 = (row) => Boolean(row.placement);

/**
 * 승격된 경쟁사 사실을 <b>발행사로 묶어</b> 그린다.
 *
 * <p>⚠ 묶지 않으면 「이 시장에 경쟁사가 41곳」처럼 읽힌다 — 실제로는 한 회사의 공시
 * 한 건에서 나온 계열사 매출 여러 줄이다. <b>회사 수와 사실 수는 다른 수다.</b>
 */
function IssuerTables({ rows }) {
  if (!rows || rows.length === 0) return null;
  const 묶음 = new Map();
  for (const row of rows) {
    const key = row.issuer ?? '(발행사 미상)';
    if (!묶음.has(key)) 묶음.set(key, []);
    묶음.get(key).push(row);
  }
  return (
    <>
      {[...묶음.entries()].map(([issuer, group]) => (
        <div key={issuer} className="mr-issuergroup">
          <h4>{issuer} <span className="num">{group.length}</span>건</h4>
          <TableAwareBody rows={group} empty="" />
        </div>
      ))}
    </>
  );
}

/**
 * 구성비 표의 합. <b>100%가 아니면 그렇다고 말한다</b> — 침묵하면 반쪽 표가 전체로 읽힌다.
 * ⚠ 백분율 행이 아니면 아무 말도 안 한다. 「배달 비용을 둘러싼 갈등 116.1%」처럼
 *   합이 뜻 없는 표에 합계를 찍으면 그것이 새 거짓말이 된다(판 ㊷ 실측).
 */
/**
 * <b>엔진이 쓴 절 글.</b> 봉투 `report.sections[]` 에서 이 절 것을 꺼내 그린다.
 *
 * <p>⚠ <b>이 글은 인용 대조를 «거치지 않았다».</b> 값·등급·경계가 붙은 것은 아래 근거
 * 표고, 이 글은 그 재료를 읽고 모델이 쓴 산문이다. 두 물건을 사용자가 <b>한눈에</b>
 * 갈라 볼 수 있어야 하므로 <b>이름표를 반드시 붙인다.</b> 지우지 말 것.
 *
 * <p>⚠ 여섯 가지 경우(BM 모드·재채점·예산 부족 …)에 글이 오지 않는다. 그때는
 * <b>아무것도 그리지 않는다</b> — 빈 상자를 세우면 「조사가 실패했다」로 읽힌다.
 */
/** 이 절에 엔진이 쓴 글이 왔는가. 근거 표를 접을지 말지가 여기에 달렸다. */
function hasProse(report, subject) {
  return Boolean(report?.sections?.find((section) => section.subject === subject)?.markdown);
}

function ReportProse({ report, subject }) {
  const found = report?.sections?.find((section) => section.subject === subject);
  if (!found?.markdown) return null;
  return (
    <div className="mr-prose">
      <Markdown text={found.markdown} />
      {/* ⚠ 판 ㊻ — **「아래 근거 표에서 확인하세요」라고 쓰지 않는다.** 그 표를 이 판에서
          절 밖으로 내렸다. 없는 곳을 가리키는 안내는 안내가 아니라 거짓말이다.
          ⚠ 「대조를 거치지 않았다」는 **빼지 않는다** — 이 글과 값의 차이가 그것뿐이다. */}
      <p className="mr-prose__by">
        ↑ 이 글은 <b>AI 가 조사 결과를 읽고 쓴 정리</b>예요 — <b>인용 대조를 거치지 않았어요.</b>
        {' '}표 안의 값과 출처는 조사 원장에서 온 것이고, 옮겨 적기 전에 출처를 눌러 원문을 확인하세요.
      </p>
    </div>
  );
}

function PercentSum({ rows }) {
  const pct = rows.filter((row) => row.unit === '%' && typeof row.value === 'number');
  if (pct.length < 3) return null;
  const sum = pct.reduce((total, row) => total + row.value, 0);
  const 온전 = sum >= 99 && sum <= 101;
  const 문장 = 온전
    ? `이 표의 ${pct.length}행 합계 ${sum.toFixed(1)}% — 표가 온전하다.`
    : `⚠ 이 표의 ${pct.length}행 합계는 ${sum.toFixed(1)}% 로 **100%가 아니다.** `
      + '보이지 않는 행이 있고, 그것이 1위일 수도 있다.';
  return (
    <p className={온전 ? 'mr-caveat mr-caveat--ok' : 'mr-caveat'}>
      <Emphasis text={문장} />
    </p>
  );
}

/** 가정 원장이 그릴 것이 있는가. `AssumptionLedger` 의 판단과 같은 조건이다. */
function hasLedger(market) {
  const figures = [market.tam, market.sam, market.growth];
  if (figures.some((figure) => figure && (figure.factors.length > 0 || figure.assumptions.length > 0))) return true;
  return Boolean(market.price?.baseNote) || !market.som;
}


/**
 * 찾지 못한 것 — **갈래로 묶는다.** 「없다」도 결과이고, 갈래마다 다음 행동이 다르다.
 * 더 찾으면 나올 것과 찾아도 없는 것을 한 무더기로 두면 둘 다 못 읽는다.
 */
function NotFoundBody({ blocks }) {
  if (!blocks || blocks.length === 0) {
    return <p className="bm-cell__none">찾지 못한 것이 기록되지 않았어요.</p>;
  }
  // 갈래 순서는 `NOT_FOUND_GROUP` 선언 순서다 — 모르는 키(group=null)는 맨 뒤에 드러낸다.
  const groups = [...Object.keys(NOT_FOUND_GROUP), null];

  return (
    <div className="mr-nf">
      {groups.map((group) => {
        const mine = blocks.filter((block) => block.group === group && block.count > 0);
        if (mine.length === 0) return null;
        const view = NOT_FOUND_GROUP[group];
        return (
          <div key={group ?? '(모르는 갈래)'} className="mr-nf__g">
            <div className="mr-nf__h">
              <Badge tone={view?.tone ?? 'danger'}>{view?.label ?? '분류하지 못한 항목'}</Badge>
              <span>{view?.note ?? '이 키를 화면이 몰라요 — 조용히 묻지 않고 드러내요'}</span>
            </div>
            {mine.map((block) => (
              <div key={block.key} className="mr-nf__b">
                <h4>{block.label}<small className="num">{block.count}건</small></h4>
                <ul>{block.entries.map((line) => <li key={line}>{line}</li>)}</ul>
              </div>
            ))}
          </div>
        );
      })}
    </div>
  );
}

/**
 * 성적표 한 과목 — <b>눌러서 근거를 편다.</b>
 *
 * <p>줄 하나가 「번호 · 제목 · 상태 · 한 줄 요약 · 근거 N건」이다. 접힌 채로도 7과목의
 * 상태가 한눈에 서고, 편 사람만 값·기간·등급·출처를 본다.
 *
 * <p>⚠ <b>근거가 0건이면 못 편다.</b> 열리는 척하고 빈 칸을 보여 주면 「조사가 부실한가」와
 * 「화면이 고장인가」가 구분되지 않는다 — 「보기」 자체를 감추고 `disabled` 로 말한다.
 */
function Subject({ n, subject, row, detail: given, count, openable, open, focused, onToggle, lead, children }) {
  const view = row ? (SCORE_STATE_VIEW[row.state] ?? { label: row.state, tone: 'neutral' }) : null;
  // ⚠ **옛 결과에는 이 과목 자체가 없다**(성적표가 7과목이던 시절). 그대로 두면 제목만
  //    있고 배지도 설명도 없는 줄이 셋 서서, 사업가는 「비었다」로 읽는다. 실제로는
  //    **그때는 재지도 않은 과목**이다 — 「0건」과 「안 쟀다」를 가르는 것이 이 줄의 일이다.
  //
  // ⚠ `given` 은 **성적표 과목이 아닌 절**(8절 처방 · 9절)이 쓴다. 그 절들은 서버 판정이
  //    없어 `row` 가 영원히 `undefined` 인데, 그대로 두면 「이 조사에는 없던 과목이에요」가
  //    **늘** 뜬다 — 참말이 아니다.
  const detail = given ?? row?.detail ?? (row ? '' : '이 조사에는 없던 과목이에요 — 다시 조사하면 채워져요');
  return (
    <div id={`sec-${subject}`} className={`mr-sub${focused ? ' is-on' : ''}`}>
      <button
        type="button"
        className="mr-sub__h"
        onClick={onToggle}
        disabled={!openable}
        aria-expanded={openable ? open : undefined}
      >
        <span className="mr-sub__n num">{n}</span>
        {/* 목차 제목은 목표 보고서 것을 쓰고, 없으면 성적표 라벨로 물러선다. */}
        <b>{SECTION_TITLE[subject] ?? SUBJECT_LABEL[subject] ?? subject}</b>
        {view ? <Badge tone={view.tone}>{view.label}</Badge> : null}
        <span className="mr-sub__d"><Emphasis text={detail} /></span>
        {openable ? (
          <span className="mr-sub__c">{open ? '접기' : `근거 ${count}건 ▾`}</span>
        ) : null}
      </button>
      {/* ★ **접기 «밖»에 세운다** — 클릭해야만 내용이 나오면 사업가는 「근거 99건」이라는
          장부만 보고 지나간다(2026-08-15 사용자 판정). 목표 보고서는 절을 열자마자
          큰 수 셋으로 시작한다. */}
      {/* ⚠ **접히든 펼치든 «항상» 보인다.** 처음엔 접혔을 때만 그렸는데, 그러면 절을 열자마자
          카드가 사라지고 경고 상자가 첫 화면을 채웠다 — 목표 보고서는 정확히 반대로,
          절을 «열면» 큰 수 셋이 맨 위에 선다(2026-08-15 사용자 대조). */}
      <KeyFigures rows={lead ?? []} />
      {open ? <div className="mr-sub__b">{children}</div> : null}
    </div>
  );
}

/**
 * <b>절의 첫인상 — 큰 숫자 셋.</b> 목표 보고서가 절마다 여는 방식이다
 * (`TARGET_REPORT.md` §1 「간편식 국내 판매액 · 6조 8천억 · 2025 전망 · 농경연」).
 *
 * <p>⚠ <b>표만 세우면 사업가는 아무것도 못 읽는다</b>(2026-08-15 화면 실측). 22행짜리
 * 표에서 무엇이 중요한지 고르는 일을 사용자에게 떠넘긴 것이 지금 화면이었다.
 * 여기서 <b>새 판정을 만들지 않는다</b> — 등급이 높은 것부터 셋을 앞으로 낼 뿐이다.
 */
function KeyFigures({ rows }) {
  const top = rows.slice(0, 3);
  if (top.length === 0) return null;
  return (
    <div className="mr-figs mr-figs--lead">
      {top.map((item) => (
        <div key={item.id}>
          <span>{factName(item)}</span>
          <b className="num">{item.raw || formatValue(item.value, item.unit)}</b>
          <small>
            {[item.period, item.issuer, hostOf(item.sourceUrl)].filter(Boolean).join(' · ')}
          </small>
        </div>
      ))}
    </div>
  );
}

/**
 * <b>경계를 표 «위»에 한 번만 세운다</b> (판 ㊺ · 목표 보고서 모양).
 *
 * <p>⚠ 행마다 두면 표가 안 읽힌다 — 실측(2026-08-15 화면): 채널 표에서 값 3줄에 경계가
 * 6줄 붙어 **표가 경계문에 파묻혔다.** 목표 보고서는 표 위에 상자 하나로 세운다.
 * <p>⚠ <b>지우는 것이 아니다.</b> 같은 문장이 여러 행에 걸리면 «한 번만» 그린다.
 */
function Caveats({ rows }) {
  // ⚠ **서랍(`밖`) 것은 끌어오지 않는다.** 실측(2026-08-15 화면): 1절 경계 상자에
  //   「당사 월별 평균시가총액 표의 한 행이다」가 넷 붙었는데, 전부 서랍 카드 것이었다 —
  //   절 머리 셋을 읽으려는 사람에게 **읽지도 않을 값의 주의문 넷**을 먼저 보여 준 셈이다.
  const 머리 = rows.filter((item) => item.placement && item.placement !== '밖');
  const 줄 = [...new Set((머리.length ? 머리 : rows).flatMap((item) => item.caveats ?? []))];
  if (줄.length === 0) return null;
  // ⚠ **「어떻게 읽나」가 「무엇을 읽나」보다 길면 안 된다.** 실측: 1절 경고가 8줄이라
  //    표가 화면 밖으로 밀렸다. 「상한으로만」·「대체 수단」 같은 **읽는 법**을 앞에 세우고,
  //    「어느 표의 한 행이다」류 출처 설명은 접는다 — **지우지 않는다.**
  const 앞 = 줄.filter((l) => !l.includes('표의 한 행이다'));
  const 뒤 = 줄.filter((l) => l.includes('표의 한 행이다'));
  return (
    <div className="mr-caveats">
      {(앞.length ? 앞 : 줄).map((line) => (
        <p key={line} className="mr-caveat"><Emphasis text={line} /></p>
      ))}
      {앞.length && 뒤.length ? (
        <details className="mr-caveats__more">
          <summary>이 값들이 어느 표에서 왔는지 ({뒤.length}건)</summary>
          {뒤.map((line) => (
            <p key={line} className="mr-caveat"><Emphasis text={line} /></p>
          ))}
        </details>
      ) : null}
    </div>
  );
}

/**
 * 한 표에 세우는 <b>줄 수 상한</b>. 목표 보고서(`TARGET_REPORT.md`) 1절 표가 6줄이다.
 *
 * <p>⚠ <b>왜 자르나.</b> 실측(2026-08-15 사용자 대조): 1절 표가 <b>80줄</b>이었고 그 안에
 * 「배달의민족 거래액 하위 20% 중개수수료」·「당사 월별 평균시가총액」·「글로벌 FPS 게임 시장」이
 * 섞여 있었다. 사업가는 그 표에서 「시장이 얼마나 큰가」를 못 읽는다 — <b>표가 길면 표가 아니다.</b>
 * <p>⚠ <b>버리지 않는다.</b> 나머지는 접어 두고 「더 보기」로 편다.
 */
const TABLE_ROWS = 6;

/** 표의 등급이 **하나로 같은가**. 같으면 행마다 배지를 찍지 않는다. */
function 한등급(rows) {
  return rows.length > 1 && rows.every((r) => r.grade && r.grade === rows[0].grade);
}

function EvidenceTable({ rows, quote = false, limit = TABLE_ROWS }) {
  // ★ 판 ㊻ — **서랍(`placement === '밖'`)은 절의 답이 아니다.** 앞으로 오면 안 된다.
  //
  // 실측(2026-08-15 화면): 4절 채널 표의 네 줄 중 셋이 서랍이었다 —
  // 「귀촌 전 거주지역 구성비」·「배송기간(제주↔내륙)」이 「채널 — 어디서 팔리나」의
  // 답으로 앉아 있었다. 절 머리가 8줄을 못 채우면 서랍이 그 자리를 메우고 있었다.
  //
  // ⚠ **버리지 않는다.** 갈래를 갈라 아래 접이식으로 내린다 — 「버리는 자리는 질문과 게재뿐」.
  const 머리 = rows.filter((row) => row.placement !== '밖');
  const 서랍 = rows.filter((row) => row.placement === '밖');
  const 앞 = 머리.slice(0, limit);
  const 뒤 = 머리.slice(limit);
  return (
    <>
    {/* ⚠ **머리글만 있는 빈 표를 세우지 않는다.** 절 사실이 전부 서랍인 절(6절 원가)에서
        「구분 · 규모 · 연도 · 출처」 네 칸짜리 **몸통 없는 표**가 떴다. */}
    {앞.length > 0 ? (
    <>
    <Caveats rows={앞} />
    {/* ★ 판 ㊻ — **등급이 전부 같으면 배지를 행마다 찍지 않는다.**
        실측: 1절 8줄이 전부 「추정」이었다 — 여덟 번 반복된 배지는 정보가 «0»이면서
        칸만 먹는다. 정보는 **다를 때만** 정보다. 대신 표 위에 한 번 적는다.
        ⚠ **지우는 것이 아니다** — 같은 말을 여덟 번에서 한 번으로 줄이는 것이다. */}
    {한등급(앞) ? (
      <p className="mr-tablenote">이 표의 값은 모두 <b>{앞[0].grade}</b>이에요</p>
    ) : null}
    <table className="mr-table">
      {/* ★ 판 ㊺ — **목표 보고서의 4열**(구분 · 규모 · 연도 · 출처)로 맞춘다.
          「값」을 왼쪽에 두면 사업가는 «무엇의» 수인지 모른 채 숫자부터 읽는다 —
          보고서는 언제나 **이름 다음에 수**다. 등급은 열을 차지하지 않고 값 옆에 붙는다. */}
      <thead>
        <tr><th>구분</th><th className="v">규모</th><th>연도</th><th>출처</th></tr>
      </thead>
      <tbody>
        {앞.map((item, i) => (
          <tr key={item.id} className={item.placement === 'OURS_SEGMENT' ? 'is-ours' : undefined}>
            <td>
              {/* 발행사 — **두 회사의 표가 하나로 읽히는 것을 막는다.** */}
              {item.issuer ? <b className="mr-issuer">경쟁사({item.issuer})</b> : null}
              {/* ★ 판 ㊻ — **같은 표에서 온 이어지는 줄은 `├` 로 딸려 있음을 보인다.**
                  목표 보고서가 「간편식 판매액」 아래에 `├ 즉석조리식품 45.4%` 를 놓는 그 모양이다.
                  ⚠ 들여쓰기는 **같은 `tableKey` 가 연달아 올 때만** — 원장이 한 표라고 말한
                  것만 딸린 줄로 그린다. 화면이 관계를 «지어내지» 않는다. */}
              {i > 0 && item.tableKey && item.tableKey === 앞[i - 1].tableKey
                ? <span className="mr-sub">├ </span> : null}
              {/* ⚠ 발췌가 `subject` 와 `metric` 에 **같은 말**을 넣는 일이 잦다 —
                  그대로 이으면 「대형마트 매출 비율 · 대형마트 매출 비율」이 된다. */}
              {factName(item)}
              {quote && item.quote ? <div className="mr-quote">“{item.quote}”</div> : null}
              {/* ⚠ 경계는 **지운 것이 아니라 표 위(`Caveats`)로 올렸다.** 행마다 두면
                  값 3줄에 경계 6줄이 붙어 표가 파묻힌다(2026-08-15 화면 실측). */}
            </td>
            {/* ⚠ **같은 값을 두 번 찍지 않는다.** 원문 표기는 「36,745억원 ↔ 3,674,500,000,000원」
                처럼 **환산값으로 되짚을 수 없을 때만** 쓸모가 있다. 실측에서는
                「142.5 % / 142.5%」·「4 % 미만 / 4% 미만」처럼 **같은 글자가 두 줄로** 섰다.
                ⚠ 원문이 있으면 **원문을 크게** 낸다 — 보고서는 「1조 1,666억」이라 쓰지
                「1,166,600,000,000원」이라 쓰지 않는다. */}
            <td className="v num">
              {item.raw || formatValue(item.value, item.unit)}
              {/* 등급이 표 안에서 갈릴 때만 행에 붙인다 — 다 같으면 표 위에 한 번 적었다. */}
              {한등급(앞) ? null : <GradeBadge grade={item.grade} />}
            </td>
            <td className="p num">{item.period ?? '—'}</td>
            <td className="s"><SourceLink item={item} /></td>
          </tr>
        ))}
      </tbody>
    </table>
    </>
    ) : null}
    {/* ⚠ **자른 것을 «숨기지» 않는다.** 몇 건이 더 있는지 적고, 펴면 다 보인다. */}
    {뒤.length > 0 ? (
      <details className="mr-more">
        <summary>나머지 {뒤.length}건 더 보기</summary>
        <EvidenceTable rows={뒤} quote={quote} limit={뒤.length} />
      </details>
    ) : null}
    {/* ⚠ 서랍은 **접되 이름을 붙인다.** 「나머지」와 한 접이식에 넣으면 절의 답을 못 찾은
        사실이 다시 숨는다 — 사업가가 이 절을 다시 조사할지 정하는 근거다. */}
    {서랍.length > 0 ? (
      <details className="mr-more mr-more--drawer">
        <summary>이 절의 답은 아니지만 조사가 가져온 것 {서랍.length}건</summary>
        <EvidenceTable rows={서랍.map((row) => ({ ...row, placement: null }))}
          quote={quote} limit={서랍.length} />
      </details>
    ) : null}
    </>
  );
}

function GrowthBody({ growth, rows }) {
  if (!growth) return <p className="bm-cell__none">성장률을 산출하지 않았어요.</p>;
  return (
    <>
      <div className="mr-figs">
        <div>
          <span>연 성장률</span>
          <b className="num">{formatValue(growth.value, growth.unit)}</b>
          <small>{growth.formula ?? ''}</small>
        </div>
      </div>
      {rows.length > 0 ? <EvidenceTable rows={rows} /> : null}
      {/* 한 줄로 이어 붙이지 않는다 — 두 문장은 서로 다른 것을 말한다.
          자세한 항별 판정은 「이 숫자를 읽는 조건」의 가정 원장에 있다. */}
      {growth.assumptions.length > 0 ? (
        <div className="mr-note">
          {growth.assumptions.map((line) => <div key={line}><Emphasis text={line} /></div>)}
        </div>
      ) : null}
    </>
  );
}

/**
 * 경쟁사 — 회사별 카드. **못 찾은 슬롯도 같은 카드에 세운다.**
 * 관측된 지표만 그리면 「이 회사는 이게 전부다」로 읽힌다.
 */
function CompetitorBody({ rows, gaps }) {
  const names = [...new Set([...rows.map((item) => item.subject), ...gaps.map(([name]) => name)])];
  if (names.length === 0) return <p className="bm-cell__none">경쟁사 관측이 없어요.</p>;

  return (
    <>
      <div className="mr-comps">
        {names.map((name) => {
          const mine = rows.filter((item) => item.subject === name);
          const missing = gaps
            .filter(([subject]) => subject === name)
            .map(([, metric]) => metric)
            .filter((metric) => !mine.some((item) => item.metric === metric));
          return (
            <div key={name} className="mr-comp">
              <h4>{name}</h4>
              {mine.map((item) => (
                <div key={item.id}>
                  <span>{item.metric}</span>
                  <b className="num">{formatValue(item.value, item.unit)}</b>
                </div>
              ))}
              {missing.map((metric) => (
                <div key={metric}><span>{metric}</span><span className="none">찾지 못함</span></div>
              ))}
              {mine.length > 0 ? (
                <div className="mr-comp__src"><SourceLink item={mine[0]} /></div>
              ) : null}
              {mine.flatMap((item) => item.caveats).map((line) => (
                <div key={line} className="mr-caveat"><Emphasis text={line} /></div>
              ))}
            </div>
          );
        })}
      </div>
      <div className="mr-note">
        수집 대상은 가입 매장 수·매출액·요금 같은 숫자예요.{' '}
        <strong>기능·차별점 비교는 조사 항목에 없어요.</strong>
      </div>
    </>
  );
}

/** 가격은 밴드로 읽어야 한다. 대표값 하나만 남으면 확정 단가로 읽힌다. */
function PriceBody({ price, cited }) {
  if (!price) return <p className="bm-cell__none">표시가격 관측이 없어요.</p>;
  const hosts = new Set(cited.map((item) => hostOf(item.sourceUrl)).filter(Boolean));

  return (
    <>
      <div className="mr-figs">
        <div><span>최저</span><b className="num">{formatValue(price.min, price.currency)}</b></div>
        <div><span>대표값 (잠정)</span><b className="num">{formatValue(price.base, price.currency)}</b></div>
        <div><span>최고</span><b className="num">{formatValue(price.max, price.currency)}</b></div>
      </div>
      {cited.length > 0 ? <EvidenceTable rows={cited} /> : null}
      {/* 건수와 독립성은 다르다. 한 도메인에서 3건은 3중 확인이 아니다. */}
      {hosts.size === 1 && cited.length > 1 ? (
        <Alert tone="warning">
          {cited.length}건이지만 출처 도메인은 <strong>{[...hosts][0]} 하나</strong>예요
          {' — '}{cited.length}중 확인이 아니라 <strong>1중 확인</strong>이에요.
        </Alert>
      ) : null}
      {price.caveats.map((line) => (
        <div key={line} className="mr-caveat"><Emphasis text={line} /></div>
      ))}
    </>
  );
}

/**
 * 계산 카드 — 입력과 **그 계산이 쓴 재료 카드**를 같이 그린다.
 *
 * ⚠ 예전에는 `index < materialIds.length` 로 입력 줄마다 「뒷받침 근거 없음」 배지를
 * 달았다. 그것은 **입력 순서와 재료 순서가 같다고 가정**한 것인데 그런 보장은 없고,
 * 실제로 엉뚱한 줄에 배지가 붙었다. 대응 관계가 데이터에 없으면 **없다고 그린다** —
 * 틀린 배지는 없는 배지보다 나쁘다. 항별 관측/가정 판정은 「이 숫자를 읽는 조건」의
 * 가정 원장이 한다(그쪽은 서버가 항마다 판정을 실어 보낸다).
 */
function CalcBody({ cards }) {
  if (cards.length === 0) return <p className="bm-cell__none">계산 카드가 없어요.</p>;
  return (
    <>
      {cards.map((card) => {
        const inputs = Object.entries(card.inputs ?? {});
        return (
          <div key={card.id}>
            <div className="mr-figs">
              <div>
                <span>{card.metric}</span>
                <b className="num">{formatValue(card.value, card.unit)}</b>
                <small>{card.formula ?? ''}</small>
              </div>
            </div>
            <table className="mr-table">
              <tbody>
                {inputs.map(([name, value]) => (
                  <tr key={name}>
                    <td className="v num">
                      {typeof value === 'number' ? value.toLocaleString('ko-KR') : String(value)}
                    </td>
                    <td>{name}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="mr-note">
              <b>쓴 재료</b>{' '}
              {card.materialIds.length > 0
                ? card.materialIds.map((id) => (
                  <Badge key={id} tone="success">{id}</Badge>
                ))
                : <Badge tone="warning">관측 재료 없음 — 전부 가정으로 채운 계산이에요</Badge>}
              {card.assumptions.map((line) => (
                <div key={line}><Emphasis text={line} /></div>
              ))}
            </div>
          </div>
        );
      })}
    </>
  );
}
