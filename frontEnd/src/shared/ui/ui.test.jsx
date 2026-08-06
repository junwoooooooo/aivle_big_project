import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import {
  Alert,
  Button,
  Dialog,
  EmptyState,
  ErrorState,
  FileInput,
  PasswordInput,
  StatusBadge,
  Tabs,
  TextInput,
} from './index.js';

describe('shared accessible components', () => {
  it('applies the button variant', () => {
    render(<Button variant="outline">취소</Button>);
    expect(screen.getByRole('button')).toHaveClass('ui-button--outline');
  });

  it('marks a loading button busy and disabled', () => {
    render(<Button loading>저장</Button>);
    expect(screen.getByRole('button', { name: /저장/ })).toBeDisabled();
    expect(screen.getByRole('button')).toHaveAttribute('aria-busy', 'true');
  });

  it('associates an input label and error', () => {
    render(<TextInput label="프로젝트 이름" error="필수 입력입니다" />);
    const input = screen.getByLabelText('프로젝트 이름');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('필수 입력입니다');
  });

  it('reveals and hides a password', () => {
    render(<PasswordInput label="비밀번호" />);
    const input = screen.getByLabelText('비밀번호');
    expect(input).toHaveAttribute('type', 'password');
    fireEvent.click(screen.getByRole('button', { name: '비밀번호 표시' }));
    expect(input).toHaveAttribute('type', 'text');
  });

  it('closes a dialog with Escape', () => {
    const onClose = vi.fn();
    render(<Dialog open onClose={onClose} title="확인"><button>계속</button></Dialog>);
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('traps focus between dialog controls', () => {
    render(
      <Dialog open onClose={() => {}} title="확인">
        <button>첫 번째</button><button>마지막</button>
      </Dialog>,
    );
    const last = screen.getByRole('button', { name: '마지막' });
    last.focus();
    fireEvent.keyDown(document, { key: 'Tab' });
    expect(screen.getByRole('button', { name: '닫기' })).toHaveFocus();
  });

  it('maps status to a human readable label', () => {
    render(<StatusBadge status="MISSING" />);
    expect(screen.getByText('보완 필요')).toBeInTheDocument();
  });

  it('does not rely on color for status', () => {
    render(<StatusBadge status="FAILED" />);
    expect(screen.getByText('분석 오류')).toBeVisible();
  });

  it('announces alert content', () => {
    render(<Alert title="안내">다시 확인해 주세요.</Alert>);
    expect(screen.getByRole('status')).toHaveAttribute('aria-live', 'polite');
  });

  it('renders an empty state with its next action', () => {
    render(<EmptyState title="자료가 없습니다" description="문서를 추가하세요" action={<button>추가</button>} />);
    expect(screen.getByRole('button', { name: '추가' })).toBeInTheDocument();
  });

  it('invokes error retry', () => {
    const retry = vi.fn();
    render(<ErrorState onRetry={retry} />);
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(retry).toHaveBeenCalledOnce();
  });

  it('uses a native, labeled file input with description', () => {
    render(<FileInput label="사업계획서" description="DOCX 파일을 선택하세요" />);
    const input = screen.getByLabelText('사업계획서');
    expect(input).toHaveAttribute('type', 'file');
    expect(input).toHaveAccessibleDescription('DOCX 파일을 선택하세요');
  });

  it('keeps the file input keyboard focusable', () => {
    render(<FileInput label="파일" />);
    screen.getByLabelText('파일').focus();
    expect(screen.getByLabelText('파일')).toHaveFocus();
  });

  it('supports arrow-key tab navigation', () => {
    const onChange = vi.fn();
    render(<Tabs label="분석" value="one" onChange={onChange} items={[
      { value: 'one', label: '첫 탭', content: '첫 내용' },
      { value: 'two', label: '둘째 탭', content: '둘째 내용' },
    ]} />);
    fireEvent.keyDown(screen.getByRole('tab', { name: '첫 탭' }), { key: 'ArrowRight' });
    expect(onChange).toHaveBeenCalledWith('two');
  });

  it('renders links inside cards without router leakage', () => {
    render(<MemoryRouter><EmptyState title="없음" description="설명" action={<a href="/next">다음</a>} /></MemoryRouter>);
    expect(screen.getByRole('link', { name: '다음' })).toBeInTheDocument();
  });
});
