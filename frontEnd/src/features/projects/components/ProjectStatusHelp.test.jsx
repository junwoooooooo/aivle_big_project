import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import ProjectStatusHelp from './ProjectStatusHelp.jsx';

describe('ProjectStatusHelp', () => {
  it('keeps one host mounted and resets its slide when the route context changes', () => {
    const { rerender } = render(<ProjectStatusHelp context="workspace" visible />);

    fireEvent.click(screen.getByRole('button', { name: '상태 안내' }));
    fireEvent.click(screen.getByRole('button', { name: '다음 안내' }));
    expect(screen.getByText('STATUS')).toBeInTheDocument();

    rerender(<ProjectStatusHelp context="plan" visible />);

    expect(screen.getByLabelText('프로젝트 안내')).toBeInTheDocument();
    expect(screen.getByText('AREA')).toBeInTheDocument();
    expect(screen.getByText('PLAN')).toBeInTheDocument();
    expect(screen.getByText('1 / 4')).toBeInTheDocument();
  });

  it('keeps its DOM mounted when the route is not eligible to show help', () => {
    const { rerender } = render(<ProjectStatusHelp context="overview" visible />);
    const help = screen.getByLabelText('프로젝트 안내');

    rerender(<ProjectStatusHelp context="overview" visible={false} />);

    expect(screen.getByLabelText('프로젝트 안내')).toBe(help);
    expect(help).toHaveClass('is-hidden');
  });

  it('uses compact chevron controls and preserves their disabled states', () => {
    render(<ProjectStatusHelp context="workspace" visible />);
    fireEvent.click(screen.getByRole('button', { name: '상태 안내' }));

    expect(screen.getByRole('button', { name: '이전 안내' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '다음 안내' })).toBeEnabled();
    expect(screen.queryByText('이전')).not.toBeInTheDocument();
    expect(screen.queryByText('다음')).not.toBeInTheDocument();
  });
});
