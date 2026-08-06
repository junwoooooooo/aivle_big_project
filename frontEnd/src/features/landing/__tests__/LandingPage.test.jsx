import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import LandingPage from '../LandingPage.jsx';
import DemoSimulator from '../components/DemoSimulator.jsx';
import HeroSection from '../components/HeroSection.jsx';
import { resetLandingIntroForTests } from '../hooks/useLandingIntro.js';

function renderLanding() { return render(<MemoryRouter><LandingPage /></MemoryRouter>); }

async function finishAutomaticPhase() {
  await act(async () => { vi.advanceTimersByTime(4800); });
}

describe('LandingPage', () => {
  beforeEach(() => resetLandingIntroForTests());
  afterEach(() => vi.useRealTimers());

  it('renders its primary content, anchors, and auth links', () => {
    renderLanding();
    expect(screen.getByRole('heading', { level: 1, name: /아이디어에서, 실행 판단을 위한 보고서까지/ })).toBeInTheDocument();
    ['intro', 'workflow', 'features', 'faq', 'demo'].forEach((id) => expect(document.getElementById(id)).toBeInTheDocument());
    expect(screen.getAllByRole('link', { name: '로그인' })[0]).toHaveAttribute('href', '/auth/login');
    expect(screen.getAllByRole('link', { name: /무료로 시작하기/ })[0]).toHaveAttribute('href', '/auth/signup');
  });

  it('keeps one hero product window while scene content and active menu change', () => {
    renderLanding();
    const frame = document.querySelector('.hero-story .hero-app-window');
    expect(frame).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /2번째 장면/ }));
    expect(document.querySelectorAll('.hero-story .hero-app-window')).toHaveLength(1);
    expect(frame).toHaveTextContent('구조화된 사업계획');
    expect(frame.querySelector('.is-active')).toHaveTextContent('구조화');
    fireEvent.click(screen.getByRole('button', { name: /3번째 장면/ }));
    expect(frame).toHaveTextContent('현재 검토 항목');
    expect(frame.querySelector('.is-active')).toHaveTextContent('법률 검토');
    fireEvent.click(screen.getByRole('button', { name: /4번째 장면/ }));
    expect(frame).toHaveTextContent('프로젝트 검증 요약');
    expect(frame.querySelector('.is-active')).toHaveTextContent('보고서');
  });

  it('provides the hero top anchor and reduced-motion settled state', () => {
    render(<MemoryRouter><HeroSection reducedMotion onNavigate={vi.fn()} /></MemoryRouter>);
    expect(document.getElementById('top')).toHaveClass('landing-hero', 'is-entered');
  });

  it('uses explicit header action classes and semantic footer group headings', () => {
    renderLanding();
    expect(document.querySelector('.landing-header__login-action')).toHaveAttribute('href', '/auth/login');
    expect(document.querySelector('.landing-header__primary-action')).toHaveAttribute('href', '/auth/signup');
    expect(screen.getByRole('heading', { level: 3, name: '서비스 둘러보기' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 3, name: '정책 및 안내' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '서비스 둘러보기' })).not.toBeInTheDocument();
  });

  it('renders the enhanced demo CTA and only runs its attention state without reduced motion', async () => {
    vi.useFakeTimers();
    render(<MemoryRouter><HeroSection introState="completed" reducedMotion={false} onNavigate={vi.fn()} /></MemoryRouter>);
    const cta = screen.getByRole('button', { name: 'Journey 미리보기' });
    expect(cta).toHaveClass('landing-demo-cta');
    expect(cta.querySelector('[aria-hidden="true"]')).toBeInTheDocument();
    await act(async () => { vi.advanceTimersByTime(30); });
    await act(async () => { vi.advanceTimersByTime(1000); });
    expect(cta).toHaveClass('is-attention');
    const { unmount } = render(<MemoryRouter><HeroSection introState="completed" reducedMotion onNavigate={vi.fn()} /></MemoryRouter>);
    await act(async () => { vi.advanceTimersByTime(1100); });
    expect(document.querySelectorAll('.landing-demo-cta.is-attention')).toHaveLength(1);
    unmount();
  });

  it('shows the brand boot sequence, blocks background interaction, and reveals the hero afterward', async () => {
    vi.useFakeTimers();
    renderLanding();
    const intro = document.querySelector('.landing-validation-intro');
    expect(intro).toHaveTextContent('Venture Verify');
    expect(intro.querySelectorAll('.validation-stream__lane')).toHaveLength(3);
    expect(intro).toHaveTextContent('사업계획서_최종.docx');
    expect(intro).toHaveClass('phase-entering');
    expect(document.querySelector('.landing-page__content')).toHaveAttribute('inert');
    expect(document.getElementById('top')).not.toHaveClass('is-entered');
    await act(async () => { vi.advanceTimersByTime(400); });
    expect(intro).toHaveClass('phase-streaming');
    await act(async () => { vi.advanceTimersByTime(700); });
    expect(intro).toHaveClass('phase-classifying');
    expect(intro).toHaveTextContent('확인된 근거');
    expect(intro).toHaveTextContent('가상 예시 데이터');
    await act(async () => { vi.advanceTimersByTime(600); });
    expect(intro).toHaveClass('phase-assembling');
    expect(intro.querySelector('.intro-product-window .hero-app-window')).toBeInTheDocument();
    await act(async () => { vi.advanceTimersByTime(600); });
    expect(document.querySelector('.landing-validation-intro')).toHaveClass('phase-collapsing');
    expect(intro.querySelector('.validation-collapse-core')).toBeInTheDocument();
    await act(async () => { vi.advanceTimersByTime(500); });
    expect(document.querySelector('.landing-validation-intro')).toHaveClass('phase-unfolding');
    expect(intro.querySelector('.validation-reveal-layer')).toBeInTheDocument();
    await act(async () => { vi.advanceTimersByTime(500); });
    expect(document.querySelector('.landing-validation-intro')).toHaveClass('phase-settling');
    expect(document.querySelector('.landing-page__content')).not.toHaveAttribute('inert');
    await act(async () => { vi.advanceTimersByTime(30); });
    expect(document.getElementById('top')).toHaveClass('is-entered');
    await act(async () => { vi.advanceTimersByTime(300); });
    expect(document.querySelector('.landing-validation-intro')).not.toBeInTheDocument();
  });

  it('lets a visitor skip the boot intro and does not restart it for a top-anchor click', async () => {
    vi.useFakeTimers();
    renderLanding();
    fireEvent.click(screen.getByRole('button', { name: '건너뛰기' }));
    await act(async () => { vi.advanceTimersByTime(250); });
    expect(document.querySelector('.landing-validation-intro')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('link', { name: 'Venture Verify' }));
    expect(document.querySelector('.landing-validation-intro')).not.toBeInTheDocument();
  });

  it('plays on reload and skips the boot intro for internal route state and browser history restoration', () => {
    const { unmount } = render(<MemoryRouter initialEntries={[{ pathname: '/', state: { skipLandingIntro: true, source: 'auth' } }]}><LandingPage /></MemoryRouter>);
    expect(document.querySelector('.landing-validation-intro')).not.toBeInTheDocument();
    unmount();
    const original = performance.getEntriesByType;
    resetLandingIntroForTests();
    performance.getEntriesByType = vi.fn(() => [{ type: 'reload' }]);
    const reloaded = renderLanding();
    expect(document.querySelector('.landing-validation-intro')).toBeInTheDocument();
    reloaded.unmount();
    resetLandingIntroForTests();
    performance.getEntriesByType = vi.fn(() => [{ type: 'back_forward' }]);
    renderLanding();
    expect(document.querySelector('.landing-validation-intro')).not.toBeInTheDocument();
    performance.getEntriesByType = original;
  });

  it('shortens the boot intro when reduced motion is requested', async () => {
    vi.useFakeTimers();
    const original = window.matchMedia;
    window.matchMedia = vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
    renderLanding();
    await act(async () => { vi.advanceTimersByTime(600); });
    expect(document.querySelector('.landing-validation-intro')).not.toBeInTheDocument();
    window.matchMedia = original;
  });

  it('cleans boot intro timers when the landing page unmounts', () => {
    vi.useFakeTimers();
    const clearTimeout = vi.spyOn(window, 'clearTimeout');
    const { unmount } = renderLanding();
    unmount();
    expect(clearTimeout).toHaveBeenCalled();
  });

  it('opens and closes an FAQ answer', () => {
    renderLanding();
    const button = screen.getByRole('button', { name: '어떤 입력을 사용할 수 있나요?' });
    expect(button).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(button); expect(button).toHaveAttribute('aria-expanded', 'true');
    fireEvent.click(button); expect(button).toHaveAttribute('aria-expanded', 'false');
  });

  it('exposes and closes the mobile navigation with Escape', () => {
    renderLanding();
    const menu = document.querySelector('.landing-menu-button');
    expect(menu).toHaveAttribute('aria-controls', 'landing-navigation');
    fireEvent.click(menu); expect(menu).toHaveAttribute('aria-expanded', 'true');
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(menu).toHaveAttribute('aria-expanded', 'false');
  });

  it('updates the header state from an IntersectionObserver entry', () => {
    const original = window.IntersectionObserver;
    const observers = [];
    class Observer {
      constructor(callback) { this.callback = callback; this.targets = []; observers.push(this); }
      observe(target) { this.targets.push(target); }
      disconnect() {}
    }
    window.IntersectionObserver = Observer;
    renderLanding();
    const observer = observers.find((item) => item.targets.some((target) => target.id === 'intro'));
    act(() => observer.callback([{ target: document.getElementById('demo'), isIntersecting: true, intersectionRatio: 1 }]));
    expect(screen.getAllByRole('button', { name: '미리보기' })[0]).toHaveAttribute('aria-current', 'true');
    window.IntersectionObserver = original;
  });

  it('changes the workflow slide from keyboard input', () => {
    renderLanding();
    const workflow = document.querySelector('.workflow-desktop');
    fireEvent.keyDown(workflow, { key: 'ArrowDown' });
    expect(screen.getByRole('button', { name: '02' })).toHaveAttribute('aria-current', 'step');
  });

  it('uses a fixed morph stage with only the current and incoming workflow slides', () => {
    renderLanding();
    expect(document.querySelector('.workflow-copy-track')).not.toBeInTheDocument();
    expect(document.querySelector('.workflow-preview-frame')).toBeInTheDocument();
    expect(document.querySelectorAll('.workflow-copy-stack .workflow-slide')).toHaveLength(1);
  });

  it('opens and closes the policy notice dialog', () => {
    renderLanding();
    fireEvent.click(screen.getByRole('button', { name: 'AI 결과 이용 안내' }));
    expect(screen.getByRole('dialog', { name: 'AI 결과 이용 안내' })).toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('requires approvals and selections before completing the interactive demo', async () => {
    vi.useFakeTimers(); renderLanding();
    const sample = screen.getByRole('button', { name: /반려동물_건강관리_구독서비스.docx/ });
    fireEvent.click(sample);
    expect(screen.getByRole('button', { name: '이 파일로 데모 시작' })).toBeInTheDocument();
    expect(screen.queryByText('파일 업로드 중')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '이 파일로 데모 시작' }));
    expect(screen.getByText('파일 업로드 중')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: '데모 처리 진행률' })).toHaveAttribute('aria-valuenow', '0');
    await finishAutomaticPhase();
    expect(screen.getByText('문서 업로드가 완료되었습니다')).toBeInTheDocument();
    await act(async () => { vi.advanceTimersByTime(3000); });
    expect(screen.getByText('문서 업로드가 완료되었습니다')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '문서 구조화 시작' }));
    await finishAutomaticPhase();
    expect(screen.getByText(/완료 항목 10개/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /보완 항목 확인/ }));
    expect(screen.getByText('가격 근거가 부족합니다.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '법률·사업성 검토 시작' }));
    await finishAutomaticPhase();
    expect(screen.getByText('사전 검토 결과')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('checkbox', { name: /제휴 상품 수익성/ }));
    fireEvent.click(screen.getByRole('button', { name: '선택 항목으로 고객 검증 설계' }));
    await finishAutomaticPhase();
    expect(screen.getByRole('button', { name: '선택한 고객군으로 결과 만들기' })).toBeDisabled();
    fireEvent.click(screen.getByRole('checkbox', { name: /디지털 건강관리 적극형/ }));
    fireEvent.click(screen.getByRole('button', { name: '선택한 고객군으로 결과 만들기' }));
    await finishAutomaticPhase();
    expect(screen.getByText('가상 사업 검증 결과')).toBeInTheDocument();
    expect(screen.getByText(/선택한 검증 항목:.*제휴 상품 수익성/)).toBeInTheDocument();
    expect(screen.getByText('디지털 건강관리 적극형')).toBeInTheDocument();
    expect(screen.getByText('다음 권장 행동')).toBeInTheDocument();
    expect(screen.getByText('추천 고객 질문')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '내 사업계획서로 시작하기' })).toHaveAttribute('href', '/auth/signup');
    fireEvent.click(screen.getByRole('button', { name: '다른 샘플 체험하기' }));
    expect(screen.getByRole('button', { name: /반려동물_건강관리_구독서비스.docx/ })).toBeInTheDocument();
  }, 10000);

  it('cleans the active demo timer when the simulator unmounts', () => {
    vi.useFakeTimers();
    const clearInterval = vi.spyOn(window, 'clearInterval');
    const { unmount } = render(<DemoSimulator reducedMotion={false} />);
    fireEvent.click(screen.getByRole('button', { name: /반려동물_건강관리_구독서비스.docx/ }));
    fireEvent.click(screen.getByRole('button', { name: '이 파일로 데모 시작' }));
    unmount();
    expect(clearInterval).toHaveBeenCalled();
  });
});
