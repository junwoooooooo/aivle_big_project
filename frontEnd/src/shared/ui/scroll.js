export function prefersReducedMotion() {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
}

export function scrollPageToTop({ smooth = true } = {}) {
  if (/jsdom/i.test(window.navigator?.userAgent ?? '') && !window.scrollTo?.mock) return;
  window.scrollTo?.({ top: 0, left: 0, behavior: smooth && !prefersReducedMotion() ? 'smooth' : 'auto' });
}

export function shouldResetRouteScroll(previous, next) {
  if (!previous || !next) return false;
  if (next.state?.backgroundLocation) return false;
  if (previous.state?.backgroundLocation
    && next.pathname === previous.state.backgroundLocation.pathname) return false;
  return previous.pathname !== next.pathname || previous.search !== next.search;
}
