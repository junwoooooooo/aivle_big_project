import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import MarketingExportDialog from './MarketingExportDialog.jsx';
import MarketingSourceSummary from './MarketingSourceSummary.jsx';

describe('marketing source and export UX', () => {
  it('shows structured validation sources and copy evidence', () => {
    render(
      <MarketingSourceSummary
        sourceSnapshotJson={JSON.stringify({
          sourceSnapshotVersion: 2,
          project: { title: '검증 프로젝트' },
          persona: { available: true, name: '얼리어답터' },
          legalReview: { available: true, summary: '성과 수치의 근거를 확인하세요.' },
          feasibility: { available: true, summary: '시장 진입 가능성 확인' },
          panelInterview: { status: 'INCLUDED', title: '가치 제안 인터뷰' },
          marketResponse: {
            status: 'INCLUDED',
            title: '메시지 비교',
            bestMessageText: '빠르고 간편하게 검증하세요',
          },
          userInput: { targetOffer: '검증 자동화' },
        })}
        legalNotice="성과 수치의 근거를 확인하세요."
        copyEvidence={['패널 인터뷰: 간편한 사용 요구']}
      />,
    );
    expect(screen.getByText('Snapshot v2')).toBeInTheDocument();
    expect(screen.getByText('카피 반영 근거')).toBeInTheDocument();
    expect(screen.getByText('패널 인터뷰: 간편한 사용 요구')).toBeInTheDocument();
  });

  it('blocks export while overflow warnings remain', () => {
    render(
      <MarketingExportDialog
        open
        filename="시안"
        content={{ content: { width: 1080, height: 1080 } }}
        warnings={['Headline이 4줄을 초과합니다.']}
        checking={false}
        exporting={false}
        onClose={vi.fn()}
        onExport={vi.fn()}
      />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Headline이 4줄을 초과합니다.');
    expect(screen.getByRole('button', { name: 'PNG 다운로드' })).toBeDisabled();
  });
});
