import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import MarketReportView from './MarketReportView.jsx';
import { normalizeMarketResult } from './marketResult.js';

function result(report) {
  return normalizeMarketResult({
    runId: 'r', conceptId: 'c', asOf: '2026-08-17', mode: 'FULL', stages: [],
    degradations: [], scorecard: [], market: null, canvas: null, bm: null, summary: null,
    notes: [], judgment: null, prescriptions: null, synthesis: null, report,
    evidence: [{ id: 'C-SEC-x', kind: '관측', metric: '채널·유통 조건', subject: '채널',
      period: null, value: null, unit: null, grade: '실무 신뢰', gradeReason: 'official',
      sourceUrl: 'https://example.com', sourceKind: 'official_page', retrievedAt: '2026-08-17',
      quote: '온라인 채널 입점 조건', caveats: [], formula: null, inputs: null,
      materialIds: [], assumptions: [], section: 'CHANNEL', placement: 'HEAD', issuer: 'example',
      tableKey: null, raw: null }],
  });
}

describe('MarketReportView', () => {
  it('separates AI prose from mechanically verified evidence and keeps verification details', () => {
    render(<MarketReportView fallback={<div>기존 검산 화면</div>} result={result({ writtenBy: 'model', unverifiedNumbers: 1,
      conceptLeaks: 0, lead: null, tail: null,
      sections: [{ subject: 'CHANNEL', markdown: 'AI가 쓴 채널 해설' }] })} />);
    expect(screen.getByText('AI가 작성한 해설입니다.')).toBeInTheDocument();
    expect(screen.getByText('기계적으로 검증된 근거')).toBeInTheDocument();
    expect(screen.getByText('근거로 검산하기')).toBeInTheDocument();
  });

  it('falls back to the existing result body when no report is available', () => {
    render(<MarketReportView fallback={<div>기존 검산 화면</div>} result={result(null)} />);
    expect(screen.getByText('기존 검산 화면')).toBeInTheDocument();
  });

  it('does not label the deterministic exact-quote report as AI prose', () => {
    render(<MarketReportView fallback={<div>기존 검산 화면</div>} result={result({
      writtenBy: 'deterministic-evidence-renderer-v1', unverifiedNumbers: 0, conceptLeaks: 0,
      lead: null, tail: null, sections: [{ subject: 'CHANNEL', markdown: '원문 인용' }],
    })} />);
    expect(screen.getByText('원문 대조를 통과한 근거 요약입니다.')).toBeInTheDocument();
    expect(screen.queryByText('AI가 작성한 해설입니다.')).not.toBeInTheDocument();
  });

  it('keeps base-evidence verification visible when section reread found no additional quote', () => {
    render(<MarketReportView fallback={<div>기존 시장규모 검증 근거</div>} result={result({
      writtenBy: 'deterministic-evidence-renderer-v1', unverifiedNumbers: 0, conceptLeaks: 0,
      lead: null, tail: null, sections: [{ subject: 'GAPS',
        markdown: '보고서용 절 재독에서 추가 원문을 확보하지 못했습니다. 기존 검증 근거는 근거로 검산하기에서 확인할 수 있습니다.' }],
    })} />);
    expect(screen.queryByText('시장규모 근거 없음')).not.toBeInTheDocument();
    expect(screen.getByText('기존 시장규모 검증 근거')).toBeInTheDocument();
  });
});
