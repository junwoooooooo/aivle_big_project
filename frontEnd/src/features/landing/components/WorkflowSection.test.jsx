import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import WorkflowSection from './WorkflowSection.jsx';
import { workflowSteps } from '../data/landingData.js';

describe('landing workflow', () => {
  it('canonical 6단계 제목과 순서를 유지한다', () => {
    expect(workflowSteps).toHaveLength(6);
    expect(workflowSteps.map(({ number }) => number)).toEqual(['01', '02', '03', '04', '05', '06']);
    expect(workflowSteps.map(({ title }) => title)).toEqual([
      '사업 기획', '사업 검증', '출시 준비', '가상 인터뷰', '마케팅 전략', '최종 보고서',
    ]);
  });

  it('6단계 progress를 표시하고 마지막 단계에서만 결과 CTA를 제공한다', () => {
    const onNavigate = vi.fn();
    render(<WorkflowSection onNavigate={onNavigate} />);

    const workflow = screen.getByLabelText('6단계 사업 검증 흐름');
    expect(workflow.querySelector('.workflow-slide--active .workflow-stage__eyebrow')).toHaveTextContent('01 / 06');
    expect(screen.queryByRole('button', { name: '샘플 결과 보기' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByLabelText('단계 선택').querySelector('[data-workflow-rail="5"]'));
    expect(workflow.querySelector('.workflow-slide--active .workflow-stage__eyebrow')).toHaveTextContent('06 / 06');
    fireEvent.click(screen.getByRole('button', { name: '샘플 결과 보기' }));
    expect(onNavigate).toHaveBeenCalledWith('demo');
  });
});
