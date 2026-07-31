import { describe, expect, it } from 'vitest';
import {
  APPLICABILITY_LABELS, buildOverallVerdict, collectActions, extractPlanQuotes, findingAnchorId,
  lawDigest, LEGAL_CATEGORY_LABELS, parseEvidence, parseReasoning, parseRecommendedActions,
  parseStringList, RISK_LABELS, riskDistribution,
} from './legalReviewViewModel.js';

describe('legal review view model', () => {
  it('provides labels for the complete category, risk and applicability contracts', () => {
    expect(Object.keys(LEGAL_CATEGORY_LABELS)).toHaveLength(10);
    expect(Object.keys(RISK_LABELS)).toHaveLength(5);
    expect(Object.keys(APPLICABILITY_LABELS)).toHaveLength(4);
  });

  it('parses evidence defensively without rendering malformed provider data', () => {
    expect(parseStringList('["근거"]')).toEqual(['근거']);
    expect(parseStringList('{bad json')).toEqual([]);
    expect(parseStringList(null)).toEqual([]);
  });

  it('counts risk distribution in severity order and folds unknown levels', () => {
    const findings = [
      { riskLevel: 'HIGH' }, { riskLevel: 'LOW' }, { riskLevel: 'HIGH' },
      { riskLevel: 'WEIRD' }, {},
    ];
    expect(riskDistribution(findings)).toEqual([
      { riskLevel: 'HIGH', label: '높음', count: 2 },
      { riskLevel: 'LOW', label: '낮음', count: 1 },
      { riskLevel: 'UNKNOWN', label: '확인 필요', count: 2 },
    ]);
    expect(riskDistribution([])).toEqual([]);
    expect(riskDistribution(null)).toEqual([]);
  });

  it('builds stable anchor ids from categories', () => {
    expect(findingAnchorId('PRIVACY_AND_DATA')).toBe('legal-cat-privacy_and_data');
    expect(findingAnchorId(null)).toBe('legal-cat-');
  });

  it('parses aggregator action strings and ignores no-action fallbacks', () => {
    expect(parseRecommendedActions('통신판매업 신고 (판매 개시 전) / 실증자료 확보 (즉시)'))
      .toEqual([
        { action: '통신판매업 신고', timing: '판매 개시 전' },
        { action: '실증자료 확보', timing: '즉시' },
      ]);
    expect(parseRecommendedActions('전문가와 상의하세요')).toEqual([
      { action: '전문가와 상의하세요', timing: null },
    ]);
    expect(parseRecommendedActions(
      '현재 계획 기준으로는 별도 조치가 필요하지 않습니다. 사업 내용이 바뀌면 다시 확인하세요.',
    )).toEqual([]);
    expect(parseRecommendedActions(
      '관할 기관 또는 자격 있는 전문가에게 적용 여부와 대응 방법을 확인하세요.',
    )).toEqual([]);
    expect(parseRecommendedActions(null)).toEqual([]);
  });

  it('splits plan quotes out of the rationale body', () => {
    const { body, quotes } = extractPlanQuotes('규제 경로: a(해당)\n계획 인용: 인용 하나 / 인용 둘');
    expect(body).toBe('규제 경로: a(해당)');
    expect(quotes).toEqual(['인용 하나', '인용 둘']);
    expect(extractPlanQuotes('인용 없음')).toEqual({ body: '인용 없음', quotes: [] });
    expect(extractPlanQuotes(null)).toEqual({ body: '', quotes: [] });
  });

  it('dedupes shared actions across findings and splits by timing', () => {
    const findings = [
      { category: 'A', riskLevel: 'HIGH', recommendedAction: '신고하기 (판매 개시 전) / 등록료 준비 (계획 실행 시)' },
      { category: 'B', riskLevel: 'MEDIUM', recommendedAction: '신고하기 (판매 개시 전)' },
    ];
    const { now, conditional } = collectActions(findings);
    expect(now).toEqual([{
      action: '신고하기', timing: '판매 개시 전', categories: ['A', 'B'], maxRiskLevel: 'HIGH',
    }]);
    expect(conditional.map((item) => item.action)).toEqual(['등록료 준비']);
  });

  it('parses law evidence strings into law and article parts', () => {
    expect(parseEvidence('개인정보 보호법 제30조(개인정보 처리방침의 수립 및 공개) — 시행 2025-10-02'))
      .toMatchObject({
        law: '개인정보 보호법', article: '제30조', title: '개인정보 처리방침의 수립 및 공개',
      });
    expect(parseEvidence('전자상거래 등에서의 소비자보호에 관한 법률 제21조의2 — 시행 2026-07-21').article)
      .toBe('제21조의2');
    expect(parseEvidence('형식이 다른 근거'))
      .toMatchObject({ law: '형식이 다른 근거', article: null, title: null });
    // 구 형식 문자열에는 설명이 없다 — 화면이 이 결측을 견뎌야 한다
    expect(parseEvidence('개인정보 보호법 제30조').plainSummary).toBeNull();
  });

  it('reads structured evidence objects as-is', () => {
    const parsed = parseEvidence({
      lawName: '개인정보 보호법', article: '제30조', title: '개인정보 처리방침의 수립 및 공개',
      role: 'REQUIREMENT', plainSummary: '처리방침을 만들어 공개해야 합니다.',
      whyRelevant: '구매 이력을 저장합니다.', excerpt: '제30조(개인정보 처리방침…) ①',
      effectiveDate: '2025-10-02', lawUrl: 'https://www.law.go.kr/법령/개인정보보호법',
    });
    expect(parsed.law).toBe('개인정보 보호법');
    expect(parsed.role).toBe('REQUIREMENT');
    expect(parsed.plainSummary).toBe('처리방침을 만들어 공개해야 합니다.');
    expect(parsed.lawUrl).toContain('law.go.kr');
  });

  it('parses the reasoning chain and skips empty ones', () => {
    const chain = parseReasoning(JSON.stringify({
      planBasis: { sectionLabels: ['BUSINESS_OVERVIEW'], quotes: ['자사몰 판매'] },
      regulatoryPath: { topic: '전자상거래·통신판매', status: '해당', reason: '비대면 판매' },
      obligations: [{ article: '제12조', lawName: '전자상거래법', summary: '통신판매업 신고' }],
      consequence: { sanctionArticles: ['제42조'], text: '벌칙 대상이 될 수 있습니다' },
      conclusion: { action: '통신판매업 신고', timing: '판매 개시 전' },
    }));
    expect(chain.topic).toBe('전자상거래·통신판매');
    expect(chain.obligations).toHaveLength(1);
    expect(chain.sanctionArticles).toEqual(['제42조']);
    expect(chain.timing).toBe('판매 개시 전');

    expect(parseReasoning(null)).toBeNull();
    expect(parseReasoning('{잘못된 json')).toBeNull();
    expect(parseReasoning('{}')).toBeNull();
  });

  it('folds ten categories into one verdict without dropping any', () => {
    const findings = [
      { category: 'A', riskLevel: 'HIGH', requiresProfessionalReview: true, recommendedAction: '신고 (판매 개시 전)' },
      { category: 'B', riskLevel: 'MEDIUM', requiresProfessionalReview: true, recommendedAction: '정비 (판매 개시 전)' },
      { category: 'C', riskLevel: 'UNKNOWN', requiresProfessionalReview: false, recommendedAction: null },
    ];
    const verdict = buildOverallVerdict(findings);
    expect(verdict.total).toBe(3);
    expect(verdict.worstRiskLevel).toBe('HIGH');
    expect(verdict.professionalReviewCount).toBe(2);
    expect(verdict.actionCount).toBe(2);
    // 모든 범주가 어느 그룹엔가 들어간다 — 커버리지 보증
    expect(verdict.groups.flatMap((group) => group.findings)).toHaveLength(3);
    expect(verdict.groups.map((group) => group.label)).toEqual(['높음', '보통', '확인 필요']);
  });

  it('aggregates evidence into a per-category law digest', () => {
    const digest = lawDigest([{
      category: 'PRIVACY_AND_DATA',
      evidenceJson: JSON.stringify([
        '개인정보 보호법 제15조(수집·이용) — 시행 2025-10-02',
        '개인정보 보호법 제30조(처리방침) — 시행 2025-10-02',
      ]),
    }, { category: 'EMPTY', evidenceJson: '[]' }]);
    expect(digest).toEqual([{
      category: 'PRIVACY_AND_DATA',
      laws: [{ law: '개인정보 보호법', articles: ['제15조', '제30조'] }],
    }]);
  });
});
