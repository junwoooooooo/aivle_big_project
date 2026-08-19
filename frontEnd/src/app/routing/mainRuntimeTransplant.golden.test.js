import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const read = (path) => readFileSync(path, 'utf8');
const router = read('src/app/routing/AppRouter.jsx');
const market = read('src/features/market/MarketResearchPage.jsx');
const bm = read('src/features/market/BmCanvasPage.jsx');
const refinement = read('src/features/market/ConceptRefinementPage.jsx');
const refinementSummary = read('src/features/market/RefinementSummary.jsx');
const interview = read('src/features/market-interview/MarketInterviewPage.jsx');

describe('MAIN Stage 2/4 runtime transplant golden', () => {
  it('routes only to the reachable MAIN donor presentations', () => {
    expect(router).toContain("from '../../features/market/ConceptRefinementPage.jsx'");
    expect(router).toContain("from '../../features/market-interview/MarketInterviewPage.jsx'");
    expect(router).not.toContain("from '../../features/business-validation/pages/ConceptRefinementPage.jsx'");
    expect(router).not.toContain("from '../../features/market-interview/pages/MarketInterviewPage.jsx'");
    expect(market).toContain("from './MarketResultBody.jsx'");
    expect(bm).toContain("from './BmResultBody.jsx'");
    expect(refinement).toContain("from './RefinementSummary.jsx'");
  });

  it('locks the MAIN market, BM and refinement copy', () => {
    for (const copy of ['시장 상황과 경쟁 환경을 확인하세요', '이 값으로 조사해요', '다음 — BM 분석'])
      expect(`${market}\n${read('src/features/market/ResearchBasisCard.jsx')}`).toContain(copy);
    for (const copy of ['사업이 고객에게 가치를 전달하고 수익을 만드는 방식을 확인하세요', '다음 — 컨셉 다듬기'])
      expect(bm).toContain(copy);
    for (const copy of ['조사 결과를 사업안에 어떻게 반영할지 고르세요',
      '고른 것만 컨셉에 들어갑니다. 넘긴 제안도 기록으로 남습니다.']) expect(refinement).toContain(copy);
    for (const copy of ['다듬어진 컨셉', '무엇이, 왜 바뀌었나요', '법률 자문은 아니에요'])
      expect(refinementSummary).toContain(copy);
  });

  it('locks the MAIN two-step interview and result copy', () => {
    for (const copy of ['보여줄 것 확인', '인터뷰 실행', '다시 조사하기', '이 조사가 센 것',
      '왜 안 산다고 하나요', '지금은 이렇게 해결해요', '바꿔 달라는 말', '끌리는 점',
      '언제 쓸 것 같은가요']) expect(interview).toContain(copy);
    expect(interview).not.toContain('RESEARCH MISSION');
    expect(interview).not.toContain('ProjectStageHeader');
  });
});
