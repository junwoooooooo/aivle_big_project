/**
 * 시장 인터뷰 결과 정규화기.
 *
 * ⚠ **이 프로젝트에는 TypeScript 도 스키마 검증도 없다. 그래서 이 파일이 타입 시스템이다.**
 * 컴포넌트는 서버 JSON 을 직접 읽지 않고 여기를 거친다.
 *
 * 이 화면이 특히 위험한 이유: 「20명 중 7명」이 「35%」로, 다시 「시장의 35%」로 읽히기
 * 쉽다. 이 조사는 그것을 답하지 않는다. 그래서 두 가지를 여기서 강제한다 —
 *   ① **백분율을 만들지 않는다.** 뷰모델에 비율 칸이 없다. 막대 너비는 화면이 그 자리에서
 *      계산하고 값으로 쓰지 않는다
 *   ② `caveats` 가 비면 조용히 지나가지 않고 **큰 소리 나는 자리표시자**를 만든다
 */

/**
 * 여섯 축 — 화면의 절 순서와 같다. 서버(`app/interview/models.py` 의 `AXES`)의 거울이다.
 *
 * ⚠ **`empty` 는 「말한 사람이 없다」라고 쓰지 않는다.** 아홉 문항은 전원이 답을 쓰고
 * 오는데, 이 절이 비는 이유는 거의 언제나 **코딩이 그 답을 어느 이름표에도 못 붙인 것**
 * 이다. 2026-08-15 실측(n=40)에서 USAGE_SCENE 은 2명만 분류됐고 화면은 38명분의 답을
 * 두고 「쓸 장면을 말한 사람이 없다」고 단언했다. 없는 사실을 만드는 문장이었다.
 */
export const AXIS_VIEW = Object.freeze([
  { axis: 'LIKE', title: '끌리는 점', tone: 'lead',
    empty: '분류된 답이 없어요 — 답을 안 쓴 게 아니라 이름표에 안 붙었어요.' },
  { axis: 'CONCERN', title: '걸리는 점', tone: 'warn',
    empty: '분류된 답이 없어요 — 답을 안 쓴 게 아니라 이름표에 안 붙었어요.' },
  { axis: 'DIFFERENTIATION', title: '무엇이 다른가요', tone: 'neutral',
    empty: '분류된 답이 없어요 — 답을 안 쓴 게 아니라 이름표에 안 붙었어요.' },
  { axis: 'USAGE_SCENE', title: '언제 쓸 것 같은가요', tone: 'lead',
    empty: '분류된 답이 없어요 — 답을 안 쓴 게 아니라 이름표에 안 붙었어요.' },
  { axis: 'BARRIER', title: '안 사는 이유', tone: 'trail',
    empty: '분류된 답이 없어요 — 답을 안 쓴 게 아니라 이름표에 안 붙었어요.' },
  { axis: 'SUGGESTION', title: '바꿨으면 하는 것', tone: 'neutral',
    empty: '분류된 답이 없어요 — 답을 안 쓴 게 아니라 이름표에 안 붙었어요.' },
]);

/**
 * 한 축에서 **분류된 사람이 답한 사람의 이 비율에 못 미치면** 그 절에 경고를 단다.
 *
 * 실측(n=40)에서 LIKE 7 · USAGE_SCENE 2 · SUGGESTION 2 였다. 그 상태의 「1위 주제」는
 * 표본의 목소리가 아니라 **분류에 성공한 소수의 목소리**인데, 화면은 둘을 구분하지 않고
 * 「40명 중 x명」이라고만 적었다.
 */
export const COVERAGE_WARN_RATIO = 0.5;

/** 차별성 인식 3분류. **«비슷하다»가 다수인 것 자체가 핵심 경고다.** */
export const DIFFERENTIATION_VIEW = Object.freeze({
  different: { label: '다르다', tone: 'lead' },
  similar: { label: '비슷하다', tone: 'trail' },
  unclear: { label: '모르겠다', tone: 'warn' },
  unclassified: { label: '판정 못 함', tone: 'neutral' },
});

/**
 * 한 축에 한 번에 그릴 주제 수. 나머지는 접는다.
 *
 * ⚠ **잘라내는 것은 화면뿐이다** — 봉투와 계약은 36개를 그대로 담는다. 축이 6개로 늘어난
 * 뒤로 상한 없이 그리면 「나열식이라 정보가 없다」는 원래 문제로 되돌아간다.
 *
 * 2026-08-15 에 5 → 3. 다섯 개도 「나열」로 읽힌다는 사용자 판정이 있었다.
 */
export const THEMES_VISIBLE = 3;

/** 이해도 3분류. **«오해»가 나쁜 결과가 아니라 «설명을 고치라»는 신호다.** */
export const COMPREHENSION_VIEW = Object.freeze({
  accurate: { label: '제대로 이해', tone: 'lead' },
  partial: { label: '반만 이해', tone: 'warn' },
  misunderstood: { label: '다른 물건으로 이해', tone: 'trail' },
  unclassified: { label: '판정 못 함', tone: 'neutral' },
});

/** 인터뷰 카드가 보여줄 9문항. 순서는 가이드 순서 그대로다 — 섞으면 답이 안 읽힌다. */
export const ANSWER_VIEW = Object.freeze([
  { key: 'firstImpression', label: '첫인상' },
  { key: 'restatement', label: '본인 말로' },
  { key: 'like', label: '끌리는 점' },
  { key: 'concern', label: '걸리는 점' },
  { key: 'differentiation', label: '무엇이 다른가' },
  { key: 'relevance', label: '필요성' },
  { key: 'usageScene', label: '언제 쓸까' },
  { key: 'barrier', label: '안 산다면' },
  { key: 'suggestion', label: '바꾼다면' },
]);

const CAVEATS_MISSING = Object.freeze([
  '⚠ 경계 문구가 결과에 실려오지 않았어요. 이 결과를 인용하지 마세요 — '
  + '값만 떼어 나가는 것을 막는 장치가 빠진 상태예요.',
]);

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function asNumber(value) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function text(value) {
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

/** 「20명 중 7명」. **여기서 백분율을 만들지 않는다** — 그 순간 크기 주장이 된다. */
export function mentionText(count, answered) {
  if (!Number.isFinite(count)) return '언급 수 없음';
  return answered > 0 ? `${answered}명 중 ${count}명` : `${count}명`;
}

/** 가격은 원 단위 정수이거나 «미정»이다. 화면이 값을 상상해 채우지 않는다. */
export function priceText(priceKrw) {
  return Number.isFinite(priceKrw) ? `${priceKrw.toLocaleString()}원` : '아직 정하지 않음';
}

/**
 * 컨셉보드를 응답자가 볼 문장으로. **`ai/app/interview/models.py` 의
 * `ConceptBoard.render()` 를 옮긴 것이다** — 실행 전에 「이렇게 보인다」를 보이려면
 * 화면이 같은 규칙을 알아야 한다.
 *
 * ⚠ **베낀 것은 갈라진다.** 그래서 골든 픽스처의 `conceptBoard.rendered` 와 이 함수의
 * 출력이 **바이트 동일**한지 `marketInterviewResult.test.js` 가 검사한다. 파이썬 쪽을
 * 고치고 여기를 안 고치면 그 테스트가 즉시 빨개진다.
 */
export function renderBoard(board) {
  const lines = [`이름: ${(board?.conceptName ?? '').trim()}`];
  const push = (label, value) => {
    const body = text(value);
    if (body) lines.push(`${label}: ${body}`);
  };
  push('누구를 위한 것인가', board?.targetUsers);
  push('어떤 상황의 문제인가', board?.problemScenario);
  const features = asArray(board?.featureSet).map(text).filter(Boolean);
  if (features.length > 0) {
    lines.push('하는 일:');
    features.forEach((feature) => lines.push(`  - ${feature}`));
  }
  push('다른 것과 다른 점', board?.differentiators);
  lines.push(Number.isFinite(board?.priceKrw)
    ? `가격: ${board.priceKrw.toLocaleString()}원`
    : '가격: 아직 정해지지 않았습니다');
  return lines.join('\n');
}

function normalizeBoard(raw) {
  return {
    conceptName: text(raw?.conceptName) ?? '이름 없는 사업안',
    targetUsers: text(raw?.targetUsers),
    problemScenario: text(raw?.problemScenario),
    featureSet: asArray(raw?.featureSet).map(text).filter(Boolean),
    differentiators: text(raw?.differentiators),
    priceKrw: asNumber(raw?.priceKrw),
    // 응답자가 실제로 본 문장. 이것을 못 보이면 답을 해석할 수 없다.
    rendered: text(raw?.rendered),
  };
}

function normalizeTheme(raw) {
  return {
    axis: raw?.axis ?? null,
    label: text(raw?.label) ?? '이름표 없음',
    mentionCount: asNumber(raw?.mentionCount) ?? 0,
    // 「그 걸림돌이 없어지면 사겠다」고 **말한** 사람 수. 추측이 아니라 발언이다.
    resolvedCount: asNumber(raw?.resolvedCount),
    // ⚠ **버리지 않는다.** 봉투에 이미 실려 있고(계약 `THEME`), 계약이
    //    `mentionCount === respondentIds.length` 까지 강제한다. 이 명단이 있어야
    //    「이 축에서 몇 명이 분류됐나」를 화면이 덧셈으로 셀 수 있다 — 주제마다 세면
    //    한 사람이 여러 주제에 들어 중복되므로 **합집합**이어야 한다.
    respondentIds: asArray(raw?.respondentIds).map(text).filter(Boolean),
    quote: text(raw?.quote),
  };
}

/** 한 축에서 이름표가 하나라도 붙은 사람 수. 주제 간 중복을 없앤 **합집합**이다. */
function classifiedCount(rows) {
  const seen = new Set();
  rows.forEach((theme) => theme.respondentIds.forEach((id) => seen.add(id)));
  return seen.size;
}

function normalizeBucketed(raw) {
  return {
    axis: raw?.axis ?? null,
    label: text(raw?.label) ?? '이름표 없음',
    mentionCount: asNumber(raw?.mentionCount) ?? 0,
    breakdown: asArray(raw?.breakdown).map((dimension) => ({
      dimension: text(dimension?.dimension) ?? '축 없음',
      buckets: asArray(dimension?.buckets).map((bucket) => ({
        label: text(bucket?.label) ?? '이름표 없음',
        count: asNumber(bucket?.count) ?? 0,
      })),
    })),
  };
}

function normalizeInterview(raw, index) {
  const profile = raw?.profile ?? {};
  return {
    key: `${raw?.comprehension ?? 'unknown'}-${index}`,
    comprehension: COMPREHENSION_VIEW[raw?.comprehension] ? raw.comprehension : 'unclassified',
    profile: {
      age: asNumber(profile.age),
      gender: text(profile.gender),
      household: text(profile.household),
      region: text(profile.region),
      income: text(profile.income),
      job: text(profile.job),
    },
    answers: ANSWER_VIEW
      .map(({ key, label }) => ({ key, label, value: text(raw?.[key]) }))
      .filter((item) => item.value),
  };
}

/**
 * 「이 조사가 센 것」 — 화면 맨 위 세 줄.
 *
 * ⚠ **해석도 권고도 아니다. 전부 집계다.** 「가격을 내려라」 같은 문장은 만들지 않는다 —
 * 합성 응답자 표본에 판정을 붙이는 순간 없는 근거가 생긴다(이 모듈 전체의 규율).
 *
 * 그런데 **병치는 집계다.** 「안 사는 이유 1위는 가격인데 제안 1위는 가격 인하가 아니다」는
 * 세기만 한 것이고, 그 나란히 놓기가 권고 없이 방향을 준다. 이 함수가 하는 일이 그것이다.
 *
 * 밖의 실무 근거: 정성 보고서가 작동하지 않는 첫 번째 이유가 「findings 를 데이터가 드러낸
 * 것이 아니라 **질문지 순서로** 조직하는 것」이다(flowres.io). 우리 화면이 그 모양이었다.
 */
function buildHeadline(themes, alternatives, contrast, answered) {
  const byCount = (a, b) => b.mentionCount - a.mentionCount;
  const barrier = themes.filter((t) => t.axis === 'BARRIER').sort(byCount)[0];
  if (!barrier) return null;

  // 타겟 분모를 함께 적는다. **이것이 없으면 「20명 중 19명」이 타겟 수 행세를 한다** —
  // 타겟이 모자라면 비타겟으로 채우는 표집이라(`targeting` 참조) 분모가 섞여 있다.
  const scope = contrast.find((row) => row.axis === barrier.axis && row.label === barrier.label);

  // 그 장벽을 말한 사람들과 **가장 많이 겹치는** 제안. 명단 교집합이라 LLM 0회다.
  const members = new Set(barrier.respondentIds);
  let best = null;
  themes.filter((t) => t.axis === 'SUGGESTION').forEach((suggestion) => {
    let overlap = 0;
    suggestion.respondentIds.forEach((id) => { if (members.has(id)) overlap += 1; });
    if (best === null || overlap > best.overlap) best = { suggestion, overlap };
  });

  // ⚠ **바닥 규칙.** 이번 표본은 13/14 라 강하지만 이 규칙은 다른 사업안에도 돈다.
  //    겹침이 3명인데도 「가장 많이 요청한 것은 X」라고 똑같이 확신 있게 찍히면,
  //    그것이 이 저장소가 반복해서 겪은 「조용히 틀린 결론」이다.
  const linked = best !== null && best.overlap * 2 >= best.suggestion.mentionCount;

  const alternative = alternatives[0] ?? null;

  return {
    barrier: {
      label: barrier.label,
      count: barrier.mentionCount,
      resolved: barrier.resolvedCount,
      // 「해결돼도 사겠다고 하지 않은」 사람. 아래 「아직 못 물어본 것」이 이 수 위에 선다.
      unresolved: barrier.resolvedCount == null ? null : barrier.mentionCount - barrier.resolvedCount,
      targetCount: scope ? scope.targetCount : null,
    },
    suggestion: best === null ? null : {
      label: best.suggestion.label,
      count: best.suggestion.mentionCount,
      overlap: best.overlap,
      // false 면 화면이 **연결 문장을 쓰지 않고** 「제안 1위는 X(n명)다」로만 적는다.
      linked,
    },
    alternative: alternative === null ? null
      : { label: alternative.label, count: alternative.mentionCount },
    answered,
  };
}

/**
 * 「아직 못 물어본 것」 — 표준 정성 보고서의 «다음 검증» 칸.
 *
 * ⚠ **엔진 사정(포화·분류 커버리지·이름표 미대조)을 여기 넣지 않는다.** 그것은 신뢰도
 * 서랍에 이미 있고, 여기 또 적으면 이 판이 고치려는 「같은 이야기 반복」을 새로 만든다.
 *
 * 여기 들어가는 것은 **집계가 자동으로 드러내는 «사업 질문»** 이다.
 * 「어쩌라고」의 반대말은 「이걸 해라」(권고 — 우리가 못 쓴다)가 아니라
 * **「다음에 이걸 확인해라」**이고, 후자는 집계라 규율을 안 어긴다.
 */
function buildOpenQuestions(headline) {
  if (!headline) return [];
  const rows = [];
  const { barrier, suggestion, alternative } = headline;

  // ⚠ **이름표 뒤에 조사를 붙이지 않는다.** 이름표는 AI 가 쓴 자유 문장이라 끝 글자를
  //   알 수 없고, 「…한다**으로**」 같은 틀린 조사가 그대로 화면에 나간다.
  //   그래서 전부 「…」 뒤에 «—» 를 두고 다음 절을 시작한다.
  if (barrier.unresolved > 0) {
    rows.push(`${barrier.unresolved}명은 해결돼도 사겠다고 하지 않았어요 — 왜인지는 안 물었어요.`);
  }
  if (suggestion !== null && suggestion.linked) {
    rows.push(`「${suggestion.label}」 — 이것만 해결해도 사겠는지는 안 물었어요.`);
  }
  if (alternative !== null) {
    rows.push(`「${alternative.label}」 — 이걸 왜 그만두게 될지는 안 물었어요.`);
  }
  return rows;
}

/** 카드 머리 두 줄. 못 읽은 칸은 그냥 빠진다 — 「알 수 없음」으로 채우지 않는다. */
export function profileLines(profile) {
  const head = [
    profile?.age === null || profile?.age === undefined ? null : `${profile.age}세`,
    profile?.gender, profile?.household, profile?.region,
  ].filter(Boolean).join(' · ');
  const sub = [profile?.income, profile?.job].filter(Boolean).join(' · ');
  return { head, sub };
}

export function normalizeMarketInterview(raw) {
  if (!raw || typeof raw !== 'object') return null;
  raw = adaptDeepEngineResult(raw);

  const answered = asNumber(raw.telemetry?.answered) ?? 0;
  const themes = asArray(raw.themes).map(normalizeTheme);
  const caveats = asArray(raw.caveats).filter((note) => typeof note === 'string' && note.trim());
  const comprehension = raw.comprehension ?? {};
  const shortCells = Object.entries(raw.sampling?.shortCells ?? {})
    .map(([cell, detail]) => ({ cell, ...detail }));

  // ── 「이 조사가 센 것」. 아래 뷰모델보다 **먼저** 만든다 — 세 재료가 다 필요하다.
  const alternatives = asArray(raw.alternatives).map((item) => ({
    label: text(item?.label) ?? '이름표 없음',
    mentionCount: asNumber(item?.mentionCount) ?? 0,
  }));
  const contrast = asArray(raw.contrast).map((row) => ({
    axis: row?.axis ?? null,
    label: text(row?.label) ?? '이름표 없음',
    targetCount: asNumber(row?.targetCount) ?? 0,
    nonTargetCount: asNumber(row?.nonTargetCount) ?? 0,
  }));
  const headline = buildHeadline(themes, alternatives, contrast, answered);

  return {
    board: normalizeBoard(raw.conceptBoard),
    sampleSize: asNumber(raw.sampleSize) ?? 0,
    // 화면 맨 위 세 줄과 「아직 못 물어본 것」. 둘 다 집계이고 LLM 호출은 0회다.
    headline,
    openQuestions: buildOpenQuestions(headline),
    // ⚠ **분모는 sampleSize 가 아니라 answered 다.** 뽑은 사람과 답한 사람은 다르고,
    //    형식 위반·타임아웃으로 빠진 사람을 분모에 넣으면 언급 수가 조용히 작아 보인다.
    answered,
    sampling: {
      requested: asNumber(raw.sampling?.requested) ?? 0,
      drawn: asNumber(raw.sampling?.drawn) ?? 0,
      shortCells,
      hasShortCells: shortCells.length > 0,
    },
    comprehension: {
      accurate: asNumber(comprehension.accurate) ?? 0,
      partial: asNumber(comprehension.partial) ?? 0,
      misunderstood: asNumber(comprehension.misunderstood) ?? 0,
      unclassified: asNumber(comprehension.unclassified) ?? 0,
      misreadPoints: asArray(comprehension.misreadPoints).map(text).filter(Boolean),
    },
    differentiation: {
      different: asNumber(raw.differentiation?.different) ?? 0,
      similar: asNumber(raw.differentiation?.similar) ?? 0,
      unclear: asNumber(raw.differentiation?.unclear) ?? 0,
      unclassified: asNumber(raw.differentiation?.unclassified) ?? 0,
    },
    targeting: {
      criteriaText: text(raw.targeting?.criteriaText) ?? '조건을 읽지 못했다',
      // ⚠ **`targetRequested` 를 버리면 안 된다.** 아래 `targetShort` 가 이것 위에 선다.
      //    2026-08-15 실측 판에서 이 칸이 버려져 있어 「타겟이 모자라다」를 화면이 물어볼
      //    방법조차 없었다.
      targetRequested: asNumber(raw.targeting?.targetRequested) ?? 0,
      targetDrawn: asNumber(raw.targeting?.targetDrawn) ?? 0,
      nonTargetDrawn: asNumber(raw.targeting?.nonTargetDrawn) ?? 0,
      // ⚠ **`shortfall` 은 경고에 쓸 수 없는 죽은 칸이다.** 서버가 `표본크기 - 뽑은수` 로
      //    계산하는데(`targeting.py`), 타겟이 모자라면 **비타겟으로 채워 넣으므로** 값이
      //    언제나 0 이 된다. 실측 판도 타겟 0명 / 비타겟 40명인데 shortfall 은 0이었다.
      //    아래 `targetShort` 가 진짜 판정이고, 이 칸은 계약이라 옮기기만 한다.
      shortfall: asNumber(raw.targeting?.shortfall) ?? 0,
      targetShort: (asNumber(raw.targeting?.targetDrawn) ?? 0)
        < (asNumber(raw.targeting?.targetRequested) ?? 0),
      // 조건이 하나도 안 걸린 조사인가. 「타겟 0명」 경고를 낼지 가르는 자리다 —
      // 「누구나」로 돌린 조사에 「타겟이 없다」고 말하면 그건 경고가 아니라 소음이다.
      targeted: (asNumber(raw.targeting?.targetRequested) ?? 0) > 0,
    },
    sections: AXIS_VIEW.map((view) => {
      const rows = themes.filter((theme) => theme.axis === view.axis);
      const classified = classifiedCount(rows);
      // 상한은 화면에만 건다. 접힌 것도 개수를 밝혀 「다 보여줬다」로 읽히지 않게 한다.
      return { ...view, themes: rows.slice(0, THEMES_VISIBLE),
        hiddenThemes: rows.slice(THEMES_VISIBLE),
        // 이 축에서 이름표가 붙은 사람 수. 아래 두 칸이 「말 안 함」과 「분류 못 함」을 가른다.
        classified,
        thinCoverage: answered > 0 && classified < answered * COVERAGE_WARN_RATIO };
    }),
    alternatives,
    // 「지금은 이렇게 해결한다」의 분류 인원은 **셀 수 없다** — `alternatives` 계약이
    // `{label, mentionCount}` 뿐이라 명단이 없다(주제 축과 달리 `respondentIds` 가 없다).
    // 그래서 여기서는 「몇 명이 그 칸에 답을 썼나」까지만 센다. 그것만으로도 「말한 사람이
    // 없다」는 거짓 단언은 막힌다 — 실측 판에서 40명 전원이 답을 썼는데 0건으로 떴다.
    relevanceAnswered: asArray(raw.transcripts)
      .filter((row) => text(row?.relevance)).length,
    segments: asArray(raw.segments).map(normalizeBucketed),
    contrast,
    suggestionLinks: asArray(raw.suggestionLinks).map((row) => ({
      label: text(row?.label) ?? '이름표 없음',
      mentionCount: asNumber(row?.mentionCount) ?? 0,
      links: asArray(row?.links).map((link) => ({
        axis: link?.axis ?? null,
        label: text(link?.label) ?? '이름표 없음',
        overlapCount: asNumber(link?.overlapCount) ?? 0,
      })),
    })),
    interviews: asArray(raw.interviews)
      .map(normalizeInterview)
      .filter((card) => card.answers.length > 0),
    transcripts: asArray(raw.transcripts)
      .map((row, index) => ({ ...normalizeInterview(row, index),
        id: text(row?.id) ?? `R${index + 1}`, target: row?.target === true }))
      .filter((row) => row.answers.length > 0),
    // 포화 — 「전원이 같은 말을 했다」. 자극이 한 속성에 쏠렸거나 분산이 소실된 것이고,
    // 어느 쪽이든 그 축은 읽으면 안 된다. 조용히 지나가지 않게 화면 위로 올린다.
    saturatedThemes: asArray(raw.telemetry?.homogeneity?.saturatedThemes)
      .map(text).filter(Boolean),
    telemetry: raw.telemetry ?? {},
    notes: asArray(raw.notes),
    // 비어 있으면 자리표시자를 넣는다. 빈 배열로 두면 화면에 아무것도 안 나오고,
    // 그러면 경계 없는 결과가 그대로 읽힌다 — 이 장치가 없애려던 실패 그 자체다.
    caveats: caveats.length > 0 ? caveats : CAVEATS_MISSING,
    caveatsMissing: caveats.length === 0,
  };
}

/** FULL deep-engine envelope → MAIN read model. Deterministic only; no new meaning is generated. */
function adaptDeepEngineResult(raw) {
  if (!Number.isInteger(raw.usableInterviewCount)) return raw;
  const traces = new Map(asArray(raw.codingTrace).map((row) => [row.participantId, row]));
  const participants = new Map(asArray(raw.participants).map((row) => [row.participantId, row]));
  const answers = new Map(asArray(raw.interviews).map((row) => [row.participantId, row]));
  const fields = ANSWER_VIEW.map((item) => item.key);
  const transcript = (id) => {
    const participant = participants.get(id) ?? {};
    const interview = answers.get(id) ?? {};
    const result = { id, target: participant.group === 'TARGET', comprehension: traces.get(id)?.comprehension,
      profile: { job: participant.profile ?? null } };
    asArray(interview.questions).forEach((item, index) => { if (fields[index]) result[fields[index]] = item?.answer; });
    return result;
  };
  const ids = asArray(raw.transcriptProvenance).map((row) => row.participantId).filter(Boolean);
  const representatives = [];
  ['misunderstood', 'partial', 'accurate', 'unclassified'].forEach((bucket) => {
    ids.forEach((id) => {
      if (representatives.length < 5 && traces.get(id)?.comprehension === bucket) representatives.push(id);
    });
  });
  const alternatives = new Map();
  asArray(raw.codingTrace).forEach((row) => {
    const label = text(row?.alternativeLabel); if (!label) return;
    if (!alternatives.has(label)) alternatives.set(label, new Set());
    alternatives.get(label).add(row.participantId);
  });
  return {
    ...raw,
    sampleSize: raw.targeting?.requestedSampleSize,
    telemetry: { answered: raw.usableInterviewCount, failures: asArray(raw.respondentFailures).length,
      homogeneity: { saturatedThemes: raw.saturation?.saturatedThemes ?? [] } },
    sampling: { requested: raw.targeting?.requestedSampleSize, drawn: raw.targeting?.drawnSampleSize,
      shortCells: {} },
    targeting: { ...raw.targeting, targetRequested: raw.targeting?.targetRequested,
      targetDrawn: raw.targeting?.targetCount, nonTargetDrawn: raw.targeting?.nonTargetCount,
      shortfall: Math.max(0, (raw.targeting?.requestedSampleSize ?? 0) - (raw.targeting?.drawnSampleSize ?? 0)) },
    themes: asArray(raw.themes).map((theme) => ({ ...theme, label: theme.title,
      respondentIds: theme.participantIds })),
    alternatives: [...alternatives.entries()].map(([label, members]) => ({ label, mentionCount: members.size })),
    contrast: asArray(raw.themes).map((theme) => ({ axis: theme.axis, label: theme.title,
      targetCount: theme.targetCount, nonTargetCount: theme.nonTargetCount })),
    transcripts: ids.map(transcript),
    interviews: representatives.map(transcript),
    caveats: asArray(raw.limitations),
    notes: asArray(raw.followUpQuestions),
  };
}
