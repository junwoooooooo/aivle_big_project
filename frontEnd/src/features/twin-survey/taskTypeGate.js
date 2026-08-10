/**
 * 판매 경계 게이트 — `ai/app/twin/task_type.py` 의 **거울**.
 *
 * ⚠ **정본은 서버다.** 이 파일은 사용자가 자극을 고치는 동안 즉시 이유를 보여주려고 있는
 * 것이지 최종 판정이 아니다. 두 규칙이 갈리면 서버가 이긴다 — 화면은 실행 버튼을 열어줬는데
 * 서버가 422 로 막는 상황이 나면, 고칠 곳은 이 파일이다.
 *
 * 규칙과 우선순위는 파이썬 쪽과 한 줄씩 대응한다. 파이썬 쪽을 고치면 여기도 고친다.
 */

export const DOMINANCE = 'DOMINANCE';
export const PRICE = 'PRICE';
export const ETHICAL_VALUE = 'ETHICAL_VALUE';
export const UNMEASURABLE = 'UNMEASURABLE';
export const IDENTICAL = 'IDENTICAL';

export const SERVICEABLE = Object.freeze([DOMINANCE, PRICE]);

/** 윤리·가치 어휘. 속성 이름·값 어느 쪽에 있어도 걸린다. */
export const ETHICAL_TERMS = Object.freeze([
  '인증', '지속가능', '친환경', '환경', 'ESG', 'esg', '공정무역', '유기농', '무농약',
  '탄소', '비건', '동물복지', '재활용', '업사이클', '윤리', '사회공헌', '그린',
  '천연', '무해', '청정',
]);

function ethicalAttributes(side) {
  return Object.entries(side?.attrs ?? {})
    .filter(([name, value]) => ETHICAL_TERMS.some((term) => `${name} ${value}`.includes(term)))
    .map(([name]) => name);
}

/**
 * 자극 한 쌍을 유형으로 가른다.
 * `pair` = `{ X: { attrs, priceKrw }, Y: { attrs, priceKrw } }`
 */
export function classifyPair(pair) {
  const x = pair?.X ?? {};
  const y = pair?.Y ?? {};
  const xAttrs = x.attrs ?? {};
  const yAttrs = y.attrs ?? {};

  const keys = [...new Set([...Object.keys(xAttrs), ...Object.keys(yAttrs)])].sort();
  const differing = keys.filter((key) => xAttrs[key] !== yAttrs[key]);
  const priceDiffers = (x.priceKrw ?? null) !== (y.priceKrw ?? null);

  if (differing.length === 0 && !priceDiffers) {
    return {
      taskType: IDENTICAL,
      serviceable: false,
      reason: '두 안이 동일하다 — 잴 차이가 없다.',
      differing,
    };
  }

  const ethical = [...new Set([...ethicalAttributes(x), ...ethicalAttributes(y)])]
    .filter((name) => differing.includes(name))
    .sort();
  if (ethical.length > 0) {
    return {
      taskType: ETHICAL_VALUE,
      serviceable: false,
      reason: `윤리·가치 속성이 대비의 축이다(${ethical.join(', ')}). `
        + '이 유형은 외적 타당성 시험에서 전부 불일치했고, 원인이 카드에 없는 정보라 '
        + '더 나은 프롬프트로도 고쳐지지 않는다. 예측을 제공하지 않는다.',
      differing,
    };
  }

  if (differing.length === 0 && priceDiffers) {
    return {
      taskType: DOMINANCE,
      serviceable: true,
      reason: '가격만 다른 단일 속성 대비다.',
      differing,
    };
  }

  if (differing.length === 1 && !priceDiffers) {
    return {
      taskType: DOMINANCE,
      serviceable: true,
      reason: `가격이 같고 «${differing[0]}» 하나만 다른 단일 속성 대비다.`,
      differing,
    };
  }

  if (differing.length === 1 && priceDiffers) {
    return {
      taskType: PRICE,
      serviceable: true,
      reason: `«${differing[0]}» 프리미엄이 가격 핸디캡을 이기는지 묻는 지불의사다.`,
      differing,
    };
  }

  return {
    taskType: UNMEASURABLE,
    serviceable: false,
    reason: `비가격 속성이 ${differing.length}개 동시에 다르다(${differing.join(', ')}). `
      + '다속성 경합은 측정 한계 이하라 방향을 말할 수 없다. '
      + '한 번에 한 속성만 바꿔서 다시 물어라.',
    differing,
  };
}

/** 쌍이 하나라도 막히면 실행할 수 없다. */
export function gateSurvey(pairs) {
  const verdicts = (pairs ?? []).map((pair) => ({ pair, ...classifyPair(pair) }));
  const blocked = verdicts.filter((verdict) => !verdict.serviceable);
  return { verdicts, blocked, canRun: verdicts.length > 0 && blocked.length === 0 };
}
