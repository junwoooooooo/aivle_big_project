import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import Emphasis from './emphasis.jsx';

/**
 * `**` 를 쓰는 쪽은 모델이 아니라 **규칙 파일**이다
 * (`research2/rules/assumptions.v1.json` 의 `basis`). 규칙의 문구는 정본이라
 * 화면 편의로 고치지 않고 화면이 읽는다.
 */
describe('Emphasis', () => {
  it('별표를 강조로 바꾸고 별표를 화면에 남기지 않는다', () => {
    render(<p data-testid="p"><Emphasis text="**가정이다 — 관측이 아니다.** 상한뿐이다" /></p>);
    const node = screen.getByTestId('p');
    expect(node.textContent).toBe('가정이다 — 관측이 아니다. 상한뿐이다');
    expect(node.textContent).not.toContain('*');
    expect(node.querySelector('strong').textContent).toBe('가정이다 — 관측이 아니다.');
  });

  it('한 문장에 강조가 여럿이어도 전부 바꾼다', () => {
    render(<p data-testid="p"><Emphasis text="**1~4명 111,347** / 계 **115,310**" /></p>);
    const node = screen.getByTestId('p');
    expect(node.querySelectorAll('strong')).toHaveLength(2);
    expect(node.textContent).not.toContain('*');
  });

  it('강조가 없으면 문자열을 그대로 낸다', () => {
    render(<p data-testid="p"><Emphasis text="침투율 0.1 은 가정이다" /></p>);
    expect(screen.getByTestId('p').textContent).toBe('침투율 0.1 은 가정이다');
    expect(screen.getByTestId('p').querySelector('strong')).toBeNull();
  });

  it('짝이 안 맞는 별표는 건드리지 않는다 — 문장을 삼키지 않는다', () => {
    render(<p data-testid="p"><Emphasis text="상한 **0.966 은 관측이다" /></p>);
    expect(screen.getByTestId('p').textContent).toBe('상한 **0.966 은 관측이다');
  });

  it('HTML 을 만들지 않는다 — 태그가 와도 글자로 남는다', () => {
    render(<p data-testid="p"><Emphasis text="<img src=x onerror=1> **경고**" /></p>);
    const node = screen.getByTestId('p');
    expect(node.querySelector('img')).toBeNull();
    expect(node.textContent).toContain('<img src=x onerror=1>');
  });

  it('빈 값이면 아무것도 그리지 않는다', () => {
    render(<p data-testid="p"><Emphasis text={null} /></p>);
    expect(screen.getByTestId('p').textContent).toBe('');
  });
});
