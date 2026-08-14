import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ScrollToTopButton } from './ScrollToTopButton.jsx';
import { scrollPageToTop, shouldResetRouteScroll } from './scroll.js';

afterEach(() => vi.restoreAllMocks());

describe('ScrollToTopButton', () => {
  it('긴 페이지에서 700px 이후 나타나 맨 위로 이동한다', () => {
    Object.defineProperty(window, 'scrollY', { configurable: true, value: 800 });
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 900 });
    Object.defineProperty(document.documentElement, 'scrollHeight', { configurable: true, value: 2200 });
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
    render(<ScrollToTopButton />);
    fireEvent.scroll(window);
    fireEvent.click(screen.getByRole('button', { name: '페이지 맨 위로 이동' }));
    expect(scrollTo).toHaveBeenCalledWith(expect.objectContaining({ top: 0, left: 0 }));
  });

  it('reduced motion에서는 auto behavior를 사용한다', () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }));
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
    scrollPageToTop();
    expect(scrollTo).toHaveBeenCalledWith(expect.objectContaining({ behavior: 'auto' }));
    vi.unstubAllGlobals();
  });

  it('route와 query 전환은 reset하고 background overlay 열기·닫기는 보존한다', () => {
    const concepts = { pathname: '/app/projects/41/concepts', search: '' };
    expect(shouldResetRouteScroll(concepts, { ...concepts, search: '?view=validation-prep' })).toBe(true);
    expect(shouldResetRouteScroll(concepts, { pathname: '/app/projects/41/market', search: '' })).toBe(true);
    const settings = { pathname: '/app/projects/41/settings', search: '', state: { backgroundLocation: concepts } };
    expect(shouldResetRouteScroll(concepts, settings)).toBe(false);
    expect(shouldResetRouteScroll(settings, concepts)).toBe(false);
  });
});
