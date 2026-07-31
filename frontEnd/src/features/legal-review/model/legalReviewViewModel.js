export const LEGAL_CATEGORY_LABELS = {
  BUSINESS_REGISTRATION: '사업자 등록',
  LICENSE_AND_PERMIT: '인허가',
  PRIVACY_AND_DATA: '개인정보·데이터',
  TERMS_AND_CONTRACT: '약관·계약',
  INTELLECTUAL_PROPERTY: '지식재산권',
  CONSUMER_PROTECTION: '소비자 보호',
  ADVERTISING_AND_MARKETING: '광고·마케팅',
  LABOR_AND_EMPLOYMENT: '노무·고용',
  INDUSTRY_SPECIFIC: '산업별 규제',
  TAX_AND_FINANCIAL: '세무·재무 규제',
};

export const RISK_LABELS = {
  LOW: '낮음', MEDIUM: '보통', HIGH: '높음', CRITICAL: '매우 높음', UNKNOWN: '확인 필요',
};

export const APPLICABILITY_LABELS = {
  APPLICABLE: '적용 가능성 높음',
  POSSIBLY_APPLICABLE: '적용 가능성 있음',
  NOT_APPLICABLE: '현재 정보상 비적용',
  INSUFFICIENT_INFORMATION: '정보 부족',
};

export function parseStringList(value) {
  if (Array.isArray(value)) return value;
  if (!value) return [];
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

const RISK_ORDER = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'];

// 아래 파서들은 ai/legal/aggregator.py가 만드는 문자열 형식에 의존한다.
//  - recommendedAction: "액션 (시기) / 액션2 (시기)"  (aggregator.py의 matched_actions 조립부)
//  - rationale: "...\n계획 근거: “인용1” / “인용2”"   (aggregator.py의 _rationale_text)
// aggregator의 형식을 바꾸면 이 파서·테스트를 함께 고칠 것.
const NO_ACTION_SENTENCES = [
  '현재 계획 기준으로는 별도 조치가 필요하지 않습니다',
  '관할 기관 또는 자격 있는 전문가에게 적용 여부와 대응 방법을 확인하세요',
];
// '계획 인용: '은 구 형식 — reasoning 도입 전 리뷰의 rationale에 남아 있다.
const PLAN_QUOTE_MARKERS = ['계획 근거: ', '계획 인용: '];
const CONDITIONAL_TIMING = '계획 실행 시';

export function parseRecommendedActions(text) {
  if (!text) return [];
  if (NO_ACTION_SENTENCES.some((sentence) => text.startsWith(sentence))) return [];
  return text.split(' / ').map((part) => {
    const matched = part.trim().match(/^(.+?)\s*\(([^()]+)\)$/);
    return matched
      ? { action: matched[1], timing: matched[2] }
      : { action: part.trim(), timing: null };
  }).filter((item) => item.action);
}

export function extractPlanQuotes(rationale) {
  if (!rationale) return { body: '', quotes: [] };
  const marker = PLAN_QUOTE_MARKERS.find((candidate) => rationale.includes(candidate));
  if (!marker) return { body: rationale, quotes: [] };
  const index = rationale.indexOf(marker);
  const quotes = rationale.slice(index + marker.length)
    .split(' / ').map((quote) => quote.trim().replace(/^[“"]|[”"]$/g, '')).filter(Boolean);
  return { body: rationale.slice(0, index).replace(/\n+$/, ''), quotes };
}

/** 같은 액션이 여러 범주 finding에 반복 매칭되므로 액션 문자열로 dedup한다. */
export function collectActions(findings) {
  const byAction = new Map();
  (findings ?? []).forEach((finding) => {
    parseRecommendedActions(finding?.recommendedAction).forEach(({ action, timing }) => {
      const entry = byAction.get(action) ?? {
        action, timing, categories: [], maxRiskLevel: 'UNKNOWN',
      };
      entry.timing = entry.timing ?? timing;
      if (finding.category && !entry.categories.includes(finding.category)) {
        entry.categories.push(finding.category);
      }
      if (RISK_ORDER.indexOf(finding.riskLevel ?? 'UNKNOWN')
        < RISK_ORDER.indexOf(entry.maxRiskLevel)) {
        entry.maxRiskLevel = finding.riskLevel;
      }
      byAction.set(action, entry);
    });
  });
  const all = [...byAction.values()];
  return {
    now: all.filter((item) => item.timing !== CONDITIONAL_TIMING),
    conditional: all.filter((item) => item.timing === CONDITIONAL_TIMING),
  };
}

export const EVIDENCE_ROLE_LABELS = {
  REQUIREMENT: '직접 의무',
  SANCTION: '위반 시 제재',
  SCOPE: '적용 범위',
};

/**
 * 근거 항목 하나를 표준 형태로 편다.
 * 구조화 객체(현행)와 "법령명 제N조(제목) — …" 문자열(구 리뷰) 양쪽을 받는다.
 */
export function parseEvidence(entry) {
  if (entry && typeof entry === 'object') {
    return {
      law: entry.lawName ?? '',
      article: entry.article ?? null,
      title: entry.title ?? null,
      role: entry.role ?? null,
      plainSummary: entry.plainSummary ?? null,
      whyRelevant: entry.whyRelevant ?? null,
      excerpt: entry.excerpt ?? null,
      effectiveDate: entry.effectiveDate ?? null,
      lawUrl: entry.lawUrl ?? null,
    };
  }
  const text = String(entry ?? '');
  const matched = text.match(/^(.+?)\s(제\d+조(?:의\d+)?)(?:\(([^)]*)\))?/);
  const base = {
    role: null, plainSummary: null, whyRelevant: null,
    excerpt: null, effectiveDate: null, lawUrl: null,
  };
  if (!matched) return { ...base, law: text, article: null, title: null };
  return { ...base, law: matched[1], article: matched[2], title: matched[3] ?? null };
}

/** finding 하나의 근거 목록 — 항상 표준 형태 배열로 돌려준다. */
export function evidenceList(finding) {
  return parseStringList(finding?.evidenceJson).map(parseEvidence);
}

/** 논리 사슬. reasoning 도입 전 리뷰는 null이며 화면이 이 단계를 건너뛴다. */
export function parseReasoning(value) {
  if (!value) return null;
  let parsed = value;
  if (typeof value === 'string') {
    try {
      parsed = JSON.parse(value);
    } catch {
      return null;
    }
  }
  if (!parsed || typeof parsed !== 'object') return null;
  const path = parsed.regulatoryPath ?? {};
  const consequence = parsed.consequence ?? {};
  const conclusion = parsed.conclusion ?? {};
  const chain = {
    quotes: parsed.planBasis?.quotes ?? [],
    sectionLabels: parsed.planBasis?.sectionLabels ?? [],
    topic: path.topic ?? null,
    status: path.status ?? null,
    pathReason: path.reason ?? null,
    obligations: parsed.obligations ?? [],
    sanctionArticles: consequence.sanctionArticles ?? [],
    consequenceText: consequence.text ?? null,
    action: conclusion.action ?? null,
    timing: conclusion.timing ?? null,
  };
  const hasContent = chain.quotes.length > 0 || chain.topic || chain.obligations.length > 0
    || chain.consequenceText || chain.action;
  return hasContent ? chain : null;
}

const VERDICT_GROUPS = [
  { key: 'HIGH', label: '높음', levels: ['CRITICAL', 'HIGH'] },
  { key: 'MEDIUM', label: '보통', levels: ['MEDIUM'] },
  { key: 'LOW', label: '낮음', levels: ['LOW'] },
  { key: 'UNKNOWN', label: '확인 필요', levels: ['UNKNOWN'] },
];

/**
 * 10개 범주를 하나의 종합 판정으로 접는다. 순수 함수 — AI 호출도 서버 계산도 없다.
 * 10개 범주 전부가 어느 그룹엔가 들어가므로 "10범주 커버리지" 보증은 화면에서 유지된다.
 */
export function buildOverallVerdict(findings) {
  const items = findings ?? [];
  const groups = VERDICT_GROUPS
    .map((group) => ({
      ...group,
      findings: items.filter((item) => group.levels.includes(item?.riskLevel ?? 'UNKNOWN')),
    }))
    .filter((group) => group.findings.length > 0);
  const worst = RISK_ORDER.find((level) => items.some((item) => item?.riskLevel === level))
    ?? 'UNKNOWN';
  return {
    total: items.length,
    worstRiskLevel: worst,
    groups,
    professionalReviewCount: items.filter((item) => item?.requiresProfessionalReview).length,
    actionCount: collectActions(items).now.length,
  };
}

/** 별첨용 — 범주별 → 법령별 조문 집계. */
export function lawDigest(findings) {
  return (findings ?? []).map((finding) => {
    const laws = new Map();
    evidenceList(finding).forEach(({ law, article }) => {
      if (!law) return;
      const articles = laws.get(law) ?? [];
      if (article && !articles.includes(article)) articles.push(article);
      laws.set(law, articles);
    });
    return {
      category: finding?.category,
      laws: [...laws.entries()].map(([law, articles]) => ({ law, articles })),
    };
  }).filter((item) => item.laws.length > 0);
}


export function riskDistribution(findings) {
  const counts = new Map();
  (findings ?? []).forEach((finding) => {
    const level = RISK_ORDER.includes(finding?.riskLevel) ? finding.riskLevel : 'UNKNOWN';
    counts.set(level, (counts.get(level) ?? 0) + 1);
  });
  return RISK_ORDER
    .filter((level) => counts.has(level))
    .map((level) => ({ riskLevel: level, label: RISK_LABELS[level], count: counts.get(level) }));
}

export function findingAnchorId(category) {
  return `legal-cat-${String(category ?? '').toLowerCase()}`;
}

// ---------------------------------------------------------------- 피드백 루프

export const PLAN_ORIGIN_LABELS = {
  UPLOAD: '문서 업로드',
  REVISION_ACCEPT: '수정안 반영',
  ANSWER: '질문 답변',
  USER_EDIT: '직접 수정',
};

export const CYCLE_STATUS_LABELS = {
  DRAFT: '검토 전',
  REVIEWING: '검토 중',
  NEEDS_ACTION: '조치 필요',
  CONVERGED: '수렴 — 발행 가능',
  PUBLISHED: '발행됨',
};

/** diff 배너 문구: "해결 1 · 신규 0 · 유지 5" */
export function buildDiffBanner(diff) {
  if (!diff) return null;
  return `해결 ${diff.resolved ?? 0} · 신규 ${diff.added ?? 0} · 유지 ${diff.maintained ?? 0}`;
}

/**
 * 수정 요청을 화면 상태별로 나눈다.
 * RESOLVED(resolvedInVersion 존재)는 삭제하지 않고 이력으로 보여준다 (§4-4).
 */
export function splitRevisionRequests(requests) {
  const pending = [];
  const resolved = [];
  const dismissed = [];
  (requests ?? []).forEach((request) => {
    if (request.resolvedInVersion != null) resolved.push(request);
    else if (request.status === 'DISMISSED') dismissed.push(request);
    else pending.push(request);
  });
  return { pending, resolved, dismissed };
}

/** 재실행/승계 요약: "광고·마케팅 재검토 · 9개 범주는 이전 결과 유지" */
export function rerunSummary(rerunCategories, carriedCategories) {
  const rerun = rerunCategories ?? [];
  const carried = carriedCategories ?? [];
  if (rerun.length === 0 || carried.length === 0) return null;
  const rerunLabels = rerun.map((code) => LEGAL_CATEGORY_LABELS[code] ?? code).join(', ');
  return `${rerunLabels} 재검토 · ${carried.length}개 범주는 이전 결과 유지`;
}

