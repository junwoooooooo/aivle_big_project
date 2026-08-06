import { useEffect } from 'react';

const revealSelectors = [
  '.landing-intro',
  '.landing-workflow',
  '.landing-features',
  '.landing-trust',
  '.landing-outcome',
  '.landing-faq',
  '.landing-demo',
  '.landing-final-cta',
  '.landing-footer',
].join(', ');

export default function useLandingReveal({ enabled, reducedMotion }) {
  useEffect(() => {
    const nodes = Array.from(document.querySelectorAll(revealSelectors));
    if (!enabled) return undefined;
    if (reducedMotion || !window.IntersectionObserver) {
      nodes.forEach((node) => node.classList.add('is-revealed'));
      return undefined;
    }

    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('is-revealed');
        observer.unobserve(entry.target);
      });
    }, { rootMargin: '0px 0px -8% 0px', threshold: .16 });

    nodes.forEach((node) => observer.observe(node));
    return () => observer.disconnect();
  }, [enabled, reducedMotion]);
}
