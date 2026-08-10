import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  CANVAS_BANDS, CANVAS_LAYOUT, CELL_KIND, NOT_FOUND_GROUP, NOT_FOUND_VIEW,
  bucketEvidence, competitorGaps, formatValue, gradeView, hostOf, normalizeMarketResult,
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

  it('7과목이 라벨과 함께 온다', () => {
    expect(result.scorecard).toHaveLength(7);
    expect(result.scorecard.map((item) => item.label)).toContain('시장 크기');
    expect(result.scorecard.every((item) => item.state)).toBe(true);
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
    expect(result.bm.decision).toBe('CONDITIONAL');
    expect(result.bm.confidence).toBe('MEDIUM');
  });

  it('칸의 근거가 id 가 아니라 근거 그대로 실려 온다', () => {
    const segments = result.canvas.find((cell) => cell.cell === 'CUSTOMER_SEGMENTS');
    expect(segments.evidence.map((item) => item.id)).toEqual(segments.evidenceIds);
    expect(segments.evidence.every((item) => item.grade)).toBe(true);
  });
});

describe('캔버스 배치와 칸의 성격', () => {
  it('밴드가 9칸을 빠짐없이 한 번씩 덮는다 — 배치표와 갈리면 칸이 사라진다', () => {
    const banded = CANVAS_BANDS.flatMap(([, cells]) => cells);
    expect(banded).toHaveLength(9);
    expect(new Set(banded).size).toBe(9);
    expect(banded).toEqual(CANVAS_LAYOUT.map((slot) => slot.cell));
    expect(CANVAS_LAYOUT.every((slot) => slot.label)).toBe(true);
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
