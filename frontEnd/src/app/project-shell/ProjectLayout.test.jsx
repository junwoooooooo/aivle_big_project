import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ProjectHelpControl } from './ProjectLayout.jsx';

describe('Project helper control', () => {
  it('opens useful canonical stage guidance instead of remaining dead', () => {
    render(<ProjectHelpControl current={{ label: '2. 사업안', nextAction: { label: '검증 가정 확인' } }} currentStatus={{ label: '입력 필요' }} />);
    fireEvent.click(screen.getByLabelText('도움말과 가이드 열기'));
    expect(screen.getByRole('dialog', { name: '현재 단계 도움말' })).toBeInTheDocument();
    expect(screen.getByText('현재 상태: 입력 필요')).toBeInTheDocument();
    expect(screen.getByText('다음에 할 일: 검증 가정 확인')).toBeInTheDocument();
    expect(screen.getByText(/오른쪽 작업 센터/)).toBeInTheDocument();
  });
});
