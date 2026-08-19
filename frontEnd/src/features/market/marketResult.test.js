import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  CANVAS_LAYOUT, CELL_KIND, CELL_STATUS_VIEW, NOT_FOUND_GROUP, NOT_FOUND_VIEW, SCORE_STATE_VIEW,
  bucketEvidence, competitorGaps, evidenceSubjectIndex, formatValue, gradeView, hostOf,
  normalizeMarketResult, sectionEvidence,
} from './marketResult.js';

/**
 * **AI·백엔드와 같은 골든 픽스처**를 읽는다.
 *
 * 세 층이 같은 파일을 보므로, 한쪽이 스키마를 바꾸면 나머지 둘의 테스트가 즉시 빨개진다.
 * 사본을 만들면 그 성질이 사라진다 — 그래서 복사하지 않고 저장소 경로로 읽는다.
 */
function fixture(name) {
  const here = dirname(fileURLToPath(import.meta.url));
  const path = resolve(here, '../../../../ai/tests/fixtures/market_research', name);
  const raw = JSON.parse(readFileSync(path, 'utf-8'));
  Object.keys(raw).filter((key) => key.startsWith('_')).forEach((key) => delete raw[key]);
  return raw;
}

describe('normalizeMarketResult — FULL', () => {
  const result = normalizeMarketResult(fixture('full.json'));

  it('10과목이 라벨과 함께 온다', () => {
    // 판 ㊸ — 채널·원가·수익성·규제 셋이 늘었다. 절 체인이 채우는 과목이다.
    expect(result.scorecard).toHaveLength(10);
    expect(result.scorecard.map((item) => item.label)).toContain('시장 크기');
    expect(result.scorecard.map((item) => item.label)).toContain('채널');
    expect(result.scorecard.every((item) => item.state)).toBe(true);
  });

  it('절 배치는 **서버가 준 것**을 쓴다 — 화면이 다시 추론하지 않는다', () => {
    // 서버가 `section` 을 주면 그것이 답이다. 화면이 다시 풀면 두 화면이 같은 근거를
    // 다른 과목이라고 말한다 — 코드가 그 위험을 주석으로 적어 뒀던 자리다.
    const bag = sectionEvidence(result);
    expect(bag.COMPETITOR.map((item) => item.id)).toContain('sec-0001');
    expect(evidenceSubjectIndex(result).get('sec-0001')).toBe('COMPETITOR');
  });

  it('승격 근거는 표 묶음과 발행사와 원문 표기를 들고 온다', () => {
    const promoted = result.evidenceById.get('sec-0001');
    expect(promoted.section).toBe('COMPETITOR');
    expect(promoted.placement).toBe('COMPETITOR_FIRM');
    expect(promoted.issuer).toBe('예시프랜차이즈');
    // 표 묶음이 없으면 「합 100.0%」도 「⚠ 100%가 아니다」도 못 만든다.
    expect(promoted.tableKey).toBeTruthy();
    expect(promoted.raw).toBe('1,240개');
  });

  it('2·8·9절이 온다 — null 과 빈 배열은 다른 사건이다', () => {
    // ⚠ 결론을 빼면 「1.37배」에서 끝나고 「그래서 어느 쪽으로 팔라」가 사라진다.
    expect(result.judgment.conclusion).toBeTruthy();
    // 못 쓴 갈래도 온다 — 침묵을 「해당 없음」으로 읽히게 두지 않는다.
    expect(result.judgment.lines.some((line) => line.silentBecause)).toBe(true);
    expect(result.prescriptions.every((row) => row.where)).toBe(true);
    expect(result.synthesis.length).toBeGreaterThan(0);
  });

  it('근거마다 등급이 있고 id 로 찾을 수 있다', () => {
    expect(result.evidence.length).toBeGreaterThan(0);
    expect(result.evidence.every((item) => item.grade)).toBe(true);
    expect(result.evidenceById.get('C-F006')).toBeTruthy();
  });

  it('경계가 근거에 붙어 온다 — 값과 한 몸이다', () => {
    const withCaveat = result.evidence.filter((item) => item.caveats.length > 0);
    expect(withCaveat.length).toBeGreaterThan(0);
    expect(withCaveat.some((item) => item.caveats[0].includes('시장 매출 아님'))).toBe(true);
  });

  it('가격 대표값의 성격 문장이 살아 있다', () => {
    expect(result.market.price.baseKind).toBe('MEDIAN_PROVISIONAL');
    expect(result.market.price.baseNote).toContain('확정 단가가 아니다');
  });

  it('BM 쪽 칸은 비어 있다 — 모드가 섞이지 않는다', () => {
    expect(result.canvas).toBeNull();
    expect(result.bm).toBeNull();
  });

  it('「못 찾은 것」이 갈래와 항목으로 펴진다 — 원시 키가 화면에 안 나온다', () => {
    const blocks = result.market.notFound;
    expect(blocks.length).toBeGreaterThan(0);
    // 갈래를 못 찾은 덩이가 하나라도 있으면 화면이 그걸 «분류 실패» 로 드러내야 한다.
    expect(blocks.every((block) => block.group)).toBe(true);
    expect(new Set(blocks.map((b) => b.group)).size).toBeGreaterThanOrEqual(4);

    const empty = blocks.find((block) => block.key === 'empty_slots');
    expect(empty.entries.length).toBeGreaterThan(1);   // \n 으로 갈렸다
    expect(empty.entries[0]).toContain('두발 미용업');  // 슬롯 id 가 사람 말이 됐다
  });

  it('모르는 진단 키는 조용히 삼키지 않고 danger 로 드러낸다', () => {
    const raw = fixture('full.json');
    raw.market.notFound = [{ item: '아무도_모르는_진단', detail: 'x' }];
    const [block] = normalizeMarketResult(raw).market.notFound;
    expect(block.group).toBeNull();
    expect(block.tone).toBe('danger');
    expect(block.label).toBe('아무도_모르는_진단');
  });

  it('근거마다 «쓰인 곳» 을 알 수 있다 — 없으면 없다고 말할 수 있어야 한다', () => {
    // 전사 매출 12조는 어느 값에도 안 들어갔다. 그 «없음» 이 값보다 중요하다.
    expect(result.usedIn.get('C-F010') ?? []).toEqual([]);
    expect(result.usedIn.get('C-F006')).toContain('TAM');
  });

  it('summary 를 떨어뜨리지 않는다 — 봉투에 있으면 화면까지 온다', () => {
    const raw = fixture('full.json');
    raw.summary = [{ cell: 'CUSTOMER_SEGMENTS', sentence: '사업체는 115,310개다.', cardIds: ['C-F006'] }];
    expect(normalizeMarketResult(raw).summary).toHaveLength(1);
    expect(normalizeMarketResult(raw).summary[0].sentence).toContain('115,310');
  });
});

describe('가정 원장 — 계산식의 항이 값으로 온다', () => {
  const result = normalizeMarketResult(fixture('full.json'));

  it('TAM 의 항이 공식과 함께 판정을 달고 온다', () => {
    const { tam } = result.market;
    expect(tam.formula).toContain('세그먼트비중');
    expect(tam.factors.length).toBeGreaterThan(1);
    expect(new Set(tam.factors.map((f) => f.basis))).toEqual(new Set(['관측', '가정', '가설']));
  });

  it('관측 항은 출처를 들고 오고, 가정 항은 0곳이라고 말한다', () => {
    const factors = result.market.tam.factors;
    const observed = factors.find((f) => f.basis === '관측');
    expect(observed.sourceCount).toBeGreaterThan(0);
    expect(observed.sourceDomains.length).toBeGreaterThan(0);
    factors.filter((f) => f.basis !== '관측')
      .forEach((f) => expect(f.sourceCount).toBe(0));
  });

  it('⭐ 울타리와 반증 조건이 화면까지 온다 — 예전엔 문장 안에서 잘려 사라졌다', () => {
    const seg = result.market.tam.factors.find((f) => f.name === '세그먼트비중');
    expect(seg.bound).toContain('0.966');
    expect(seg.falsifiedIf).toBeTruthy();
    // 잘린 문장은 문장부호 없이 끝난다. 서술이 통째로 와야 한다.
    expect(seg.note.length).toBeGreaterThan(20);
  });

  it('항으로 표현되는 문장은 assumptions 에 다시 오지 않는다 — 두 벌이 뜨면 안 된다', () => {
    expect(result.market.tam.assumptions).toEqual([]);
    // 성장률의 문장은 항이 아니라 «읽는 법»이라 남는다.
    expect(result.market.growth.assumptions.length).toBeGreaterThan(0);
    expect(result.market.growth.factors.every((f) => f.basis === '관측')).toBe(true);
  });

  it('요인이 없는 옛 결과도 문장을 잃지 않는다 — 폴백이 있어야 한다', () => {
    const raw = fixture('full.json');
    delete raw.market.tam.factors;
    raw.market.tam.assumptions = ['세그먼트비중은 가정이다'];
    const old = normalizeMarketResult(raw);
    expect(old.market.tam.factors).toEqual([]);
    expect(old.market.tam.assumptions).toHaveLength(1);
  });
});

describe('「못 찾은 것」 갈래표', () => {
  it('모든 진단 키가 갈래를 갖는다', () => {
    Object.entries(NOT_FOUND_VIEW).forEach(([key, [group, label]]) => {
      expect(NOT_FOUND_GROUP[group], `${key} 의 갈래 ${group}`).toBeTruthy();
      expect(label).toBeTruthy();
    });
  });

  it('픽스처가 내는 키는 전부 분류돼 있다 — 서버 표와 갈리면 여기서 잡힌다', () => {
    const keys = fixture('full.json').market.notFound.map((block) => block.item);
    keys.forEach((key) => expect(NOT_FOUND_VIEW[key], `분류되지 않은 키: ${key}`).toBeTruthy());
  });
});

describe('normalizeMarketResult — BM', () => {
  const result = normalizeMarketResult(fixture('bm.json'));

  it('9칸이 표준 배치 순서로 온다', () => {
    expect(result.canvas).toHaveLength(9);
    expect(result.canvas.map((cell) => cell.cell)).toEqual(CANVAS_LAYOUT.map((s) => s.cell));
    expect(result.canvas.every((cell) => cell.absent === false)).toBe(true);
  });

  it('⭐ 인용한 근거의 경계가 칸에 실려 있다', () => {
    const segments = result.canvas.find((cell) => cell.cell === 'CUSTOMER_SEGMENTS');
    expect(segments.evidenceIds).toContain('C-F011');
    const fromEvidence = result.evidenceById.get('C-F011').caveats;
    fromEvidence.forEach((caveat) => expect(segments.caveats).toContain(caveat));
  });

  it('판정과 신뢰도가 온다', () => {
    // 게이트가 내린 뒤의 값이다 — 모델은 CONDITIONAL 을 냈지만 CHANNELS 자료가 0건이다.
    expect(result.bm.decision).toBe('REVISION_REQUIRED');
    expect(result.bm.confidence).toBe('MEDIUM');
  });

  it('게이트 사유가 근거 id 를 달고 온다', () => {
    expect(result.bm.gateReasons.map((reason) => reason.code)).toEqual(['G1', 'G4']);
    const [channels] = result.bm.gateReasons;
    expect(channels.cell).toBe('CHANNELS');
    expect(channels.message).toContain('0건');
  });

  // 칸 하나가 아니라 캔버스 전체를 두고 걸리는 규칙(G4)은 cell 이 null 이다.
  // 화면이 그 분기를 안 그리면 「· undefined」 가 붙는다.
  it('캔버스 전체 규칙은 cell 이 null 이다', () => {
    const whole = result.bm.gateReasons.find((reason) => reason.code === 'G4');
    expect(whole.cell).toBeNull();
  });

  it('사유마다 갈래가 온다 — 옛 결과는 판별 불가로 읽는다', () => {
    expect(result.bm.gateReasons.every((reason) => reason.cause)).toBe(true);
    const raw = fixture('bm.json');
    raw.bm.gateReasons.forEach((reason) => { delete reason.cause; });
    expect(normalizeMarketResult(raw).bm.gateReasons.map((r) => r.cause))
      .toEqual(['UNMAPPED', 'UNMAPPED']);
  });

  it('게이트 사유가 없으면 빈 배열이다 — undefined 면 화면이 터진다', () => {
    const raw = fixture('bm.json');
    delete raw.bm.gateReasons;
    expect(normalizeMarketResult(raw).bm.gateReasons).toEqual([]);
  });

  it('칸의 근거가 id 가 아니라 근거 그대로 실려 온다', () => {
    const segments = result.canvas.find((cell) => cell.cell === 'CUSTOMER_SEGMENTS');
    expect(segments.evidence.map((item) => item.id)).toEqual(segments.evidenceIds);
    expect(segments.evidence.every((item) => item.grade)).toBe(true);
  });
});

describe('캔버스 배치와 칸의 성격', () => {
  it('배치표가 9칸을 빠짐없이 한 번씩 덮는다 — 빠지면 칸이 조용히 사라진다', () => {
    const cells = CANVAS_LAYOUT.map((slot) => slot.cell);
    expect(cells).toHaveLength(9);
    expect(new Set(cells).size).toBe(9);
    expect(CANVAS_LAYOUT.every((slot) => slot.label)).toBe(true);
    // 순서의 정본은 목업 `public/wireframe.html` 의 3×3 이다.
    expect(cells).toEqual([
      'KEY_PARTNERS', 'KEY_ACTIVITIES', 'VALUE_PROPOSITIONS',
      'CUSTOMER_RELATIONSHIPS', 'CUSTOMER_SEGMENTS', 'CHANNELS',
      'KEY_RESOURCES', 'COST_STRUCTURE', 'REVENUE_STREAMS',
    ]);
  });

  it('9칸이 전부 관측/계획으로 갈려 있다 — 안 갈리면 정상 결과가 미완성으로 읽힌다', () => {
    const kinds = CANVAS_LAYOUT.map((slot) => CELL_KIND[slot.cell]?.[0]);
    expect(kinds.filter((kind) => kind === '관측')).toHaveLength(4);
    expect(kinds.filter((kind) => kind === '계획')).toHaveLength(5);
    expect(CANVAS_LAYOUT.every((slot) => CELL_KIND[slot.cell]?.[1])).toBe(true);
  });

  it('정규화가 칸에 성격과 출처 문구를 실어 준다', () => {
    const canvas = normalizeMarketResult(fixture('bm.json')).canvas;
    const cost = canvas.find((cell) => cell.cell === 'COST_STRUCTURE');
    expect(cost.kind).toBe('계획');
    expect(cost.origin).toContain('입력 제약');
  });
});

describe('어휘 — 성적표와 캔버스 칸이 같은 낱말을 쓰지 않는다', () => {
  it('⭐ 「확인됨」은 성적표 쪽 하나뿐이다 — 겹치면 한 화면에서 두 뜻으로 뜬다', () => {
    expect(SCORE_STATE_VIEW.FILLED.label).toBe('확인됨');
    expect(Object.values(CELL_STATUS_VIEW).map((view) => view.label)).not.toContain('확인됨');
  });

  it('두 표의 라벨 집합이 겹치지 않는다', () => {
    const scores = new Set(Object.values(SCORE_STATE_VIEW).map((view) => view.label));
    const overlap = Object.values(CELL_STATUS_VIEW)
      .map((view) => view.label)
      .filter((label) => scores.has(label));
    expect(overlap).toEqual([]);
  });
});

describe('근거를 과목으로 가른다 — 봉투에 과목 필드가 없어 화면이 되짚는다', () => {
  const result = normalizeMarketResult(fixture('full.json'));
  const bag = bucketEvidence(result);

  it('한 근거는 정확히 한 과목에만 들어간다 — 어느 것도 버려지지 않는다', () => {
    const all = Object.values(bag).flat().map((item) => item.id);
    expect(all).toHaveLength(result.evidence.length);
    expect(new Set(all).size).toBe(result.evidence.length);
  });

  it('가격·모집단·계산이 제 과목으로 간다', () => {
    expect(bag.price.map((item) => item.id)).toEqual(
      expect.arrayContaining(result.market.price.evidenceIds),
    );
    expect(bag.size.map((item) => item.id)).toContain('C-F006');
    expect(bag.calc.every((item) => item.kind === '계산')).toBe(true);
  });

  it('경쟁사 지표는 어느 값에도 안 쓰였어도 경쟁사로 간다', () => {
    // 12조짜리 네이버 전사 매출. 「매출액」이라 경쟁사 칸에 서고, 그 곁에 경계가 따라붙는다.
    const naver = bag.comp.find((item) => item.id === 'C-F010');
    expect(naver).toBeTruthy();
    expect(naver.caveats[0]).toContain('시장 매출 아님');
  });
});

describe('못 찾은 경쟁사 슬롯', () => {
  it('경쟁사 지표인 줄만 (회사, 지표) 로 뽑는다', () => {
    const result = normalizeMarketResult(fixture('full.json'));
    const gaps = competitorGaps(result.market.notFound);
    expect(gaps).toContainEqual(['네이버 예약', '매출액']);
    // 「종사자 1인 사업체 비율」은 경쟁사 지표가 아니다 — 경쟁사 카드에 서면 안 된다.
    expect(gaps.every(([, metric]) => metric !== '종사자 1인 사업체 비율')).toBe(true);
  });

  it('덩이가 없거나 모양이 다르면 빈 목록이다 — 던지지 않는다', () => {
    expect(competitorGaps(null)).toEqual([]);
    expect(competitorGaps([{ key: 'empty_slots', entries: ['모양이 다른 줄'] }])).toEqual([]);
  });
});

describe('출처 도메인', () => {
  it('www 를 떼고, 링크가 아니면 null 이다', () => {
    expect(hostOf('https://www.kosis.kr/statHtml')).toBe('kosis.kr');
    expect(hostOf(null)).toBeNull();
    expect(hostOf('출처 없음')).toBeNull();
  });
});

describe('누락을 조용히 넘기지 않는다', () => {
  it('등급이 없으면 «등급 표기 없음» 으로 드러낸다', () => {
    expect(gradeView(undefined).label).toBe('등급 표기 없음');
    expect(gradeView(undefined).tone).toBe('danger');
    expect(gradeView('확정').label).toBe('확정');
  });

  it('값이 없으면 «미확보» 라고 쓴다 — 빈 자리로 두지 않는다', () => {
    expect(formatValue(null, '원')).toBe('미확보');
    expect(formatValue(undefined)).toBe('미확보');
    expect(formatValue(0, '개')).toBe('0 개');       // 0 은 값이다
    expect(formatValue(19800, '원')).toBe('19,800 원');
  });

  it('칸이 아예 안 오면 absent 로 구분한다 — 미확인과 다른 사건이다', () => {
    const raw = fixture('bm.json');
    raw.canvas.cells = raw.canvas.cells.filter((cell) => cell.canvasCell !== 'CHANNELS');
    const result = normalizeMarketResult(raw);
    const channels = result.canvas.find((cell) => cell.cell === 'CHANNELS');
    expect(channels.absent).toBe(true);
  });

  it('결과가 없으면 null 을 준다 — 빈 객체로 흉내내지 않는다', () => {
    expect(normalizeMarketResult(null)).toBeNull();
    expect(normalizeMarketResult(undefined)).toBeNull();
  });
});
