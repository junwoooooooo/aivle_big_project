import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { BoundarySummary } from './ConversationalIdeaWorkspace.jsx';

const rule = (ruleType, title) => ({ ruleId: `${ruleType}-1`, ruleType, title,
  normalizedRequirement: `${title}의 실행 가능한 요구사항`, evidenceIds: ['EVD-1'] });

describe('Regulatory Boundary summary', () => {
  it('starts only from a confirmed brief and preserves compact workspace typography', () => {
    const onStart = vi.fn();
    const { rerender, container } = render(<BoundarySummary boundary={null} confirmedBrief={false} busy={false} onStart={onStart} />);
    expect(screen.getByRole('button', { name: '규제 경계 생성' })).toBeDisabled();
    rerender(<BoundarySummary boundary={null} confirmedBrief busy={false} onStart={onStart} />);
    fireEvent.click(screen.getByRole('button', { name: '규제 경계 생성' }));
    expect(onStart).toHaveBeenCalledOnce();
    expect(container.querySelector('.idea-boundary')).toBeTruthy();
  });

  it('renders READY implementation groups and source warnings without legal-report content', () => {
    render(<BoundarySummary confirmedBrief busy={false} onStart={vi.fn()} boundary={{ version: {
      status: 'READY', rules: [rule('ALLOWED_PATTERN', '파트너 수행'), rule('PROHIBITED_ACTIVITY', '직접 수거 금지'),
        rule('REQUIRED_CONTROL', '접근 통제'), rule('REQUIRED_PARTNER', '허가 사업자'),
        rule('REQUIRED_DISCLOSURE', '처리 목적 고지')], sourceWarnings: ['일부 지역은 추가 확인 필요'],
    } }} />);
    expect(screen.getByText('허용 가능한 구현 방향')).toBeInTheDocument();
    expect(screen.getByText('피해야 할 역할·활동')).toBeInTheDocument();
    expect(screen.getByText('필수 통제')).toBeInTheDocument();
    expect(screen.getByText('일부 지역은 추가 확인 필요')).toBeInTheDocument();
    expect(screen.queryByText(/technicalCode|providerBody|법률 보고서/i)).not.toBeInTheDocument();
  });

  it('renders NEEDS_INPUT questions and related Brief fields', () => {
    render(<BoundarySummary confirmedBrief busy={false} onStart={vi.fn()} boundary={{ version: {
      status: 'NEEDS_INPUT', rules: [], questions: [{ questionId: 'Q-1', fieldKey: 'targetRegion',
        question: '운영 지역은 어디입니까?', reason: '적용 법령 범위를 정해야 합니다.' }],
    } }} />);
    expect(screen.getByText('추가 확인이 필요합니다')).toBeInTheDocument();
    expect(screen.getByText('운영 지역은 어디입니까?')).toBeInTheDocument();
    expect(screen.getByText(/targetRegion/)).toBeInTheDocument();
  });

  it('renders BLOCKED user action options and FAILED safe retry state', () => {
    const { rerender } = render(<BoundarySummary confirmedBrief busy={false} onStart={vi.fn()} boundary={{ version: {
      status: 'BLOCKED', rules: [], conflicts: [{ conflictId: 'C-1', affectedFieldKey: 'fixedConstraints',
        reason: '직접 수행 고정 조건이 허가 요건과 충돌합니다.', userActionOptions: ['허가 파트너 수행으로 변경'] }],
    } }} />);
    expect(screen.getByText('고정 조건과 규제 경계가 충돌합니다')).toBeInTheDocument();
    expect(screen.getByText('허가 파트너 수행으로 변경')).toBeInTheDocument();
    expect(screen.getByText(/Brief 수정으로 돌아가/)).toBeInTheDocument();
    rerender(<BoundarySummary confirmedBrief busy={false} onStart={vi.fn()} boundary={{ run: {
      status: 'FAILED', retryable: true, errorCode: 'SECRET_TECHNICAL_CODE' } }} />);
    expect(screen.getByRole('alert')).toHaveTextContent('잠시 후 다시 시도');
    expect(screen.queryByText('SECRET_TECHNICAL_CODE')).not.toBeInTheDocument();
  });
});
