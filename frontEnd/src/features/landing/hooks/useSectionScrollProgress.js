import { useEffect } from 'react';

const selectors = '.landing-intro, .landing-workflow, .landing-features, .landing-trust, .landing-outcome, .landing-faq, .landing-demo, .landing-final-cta, .landing-footer';
const clamp = (value) => Math.min(1, Math.max(0, value));
const smoothstep = (start, end, value) => {
  const normalized = clamp((value - start) / (end - start));
  return normalized * normalized * (3 - (2 * normalized));
};

export default function useSectionScrollProgress({ enabled, reducedMotion }) {
  useEffect(() => {
    const sections = Array.from(document.querySelectorAll(selectors));
    if (!enabled || reducedMotion) {
      sections.forEach((section) => {
        section.style.setProperty('--section-enter', '1');
        section.style.setProperty('--section-exit', '0');
        section.style.setProperty('--section-progress', '.5');
        section.dataset.motionState = 'active';
        if (section.classList.contains('landing-footer')) section.dataset.motionSettled = 'true';
      });
      return undefined;
    }

    const values = new Map();
    let frame = 0;

    const write = (section, current) => {
      const isFooter = section.classList.contains('landing-footer');
      const enter = isFooter ? smoothstep(.02, .52, current) : smoothstep(.05, .38, current);
      // Footer has no scroll runway after it. Keep its settled state while it
      // occupies the viewport, then let the entry value drive the reverse exit.
      const exit = isFooter ? 0 : smoothstep(.66, .96, current);
      section.style.setProperty('--section-progress', current.toFixed(4));
      section.style.setProperty('--section-enter', enter.toFixed(4));
      section.style.setProperty('--section-exit', exit.toFixed(4));
      section.dataset.motionState = enter < .99 ? 'entering' : exit > .01 ? 'leaving' : 'active';
    };

    const update = () => {
      frame = 0;
      const viewport = window.innerHeight || 1;
      const documentEnd = window.scrollY + viewport >= document.documentElement.scrollHeight - 2;
      let needsAnotherFrame = false;

      sections.forEach((section) => {
        const rect = section.getBoundingClientRect();
        let target = clamp((viewport - rect.top) / (viewport + rect.height));
        if (section.classList.contains('landing-footer') && documentEnd) target = 1;
        const previous = values.get(section) ?? target;
      const current = Math.abs(target - previous) < .0005 ? target : previous + ((target - previous) * .085);
        values.set(section, current);
        write(section, current);
        const footerSettled = section.classList.contains('landing-footer') && (documentEnd || current >= .96);
        if (section.classList.contains('landing-footer')) section.dataset.motionSettled = footerSettled ? 'true' : 'false';
        if (Math.abs(target - current) >= .0005) needsAnotherFrame = true;
      });

      if (needsAnotherFrame) frame = window.requestAnimationFrame(update);
    };
    const request = () => {
      if (!frame) frame = window.requestAnimationFrame(update);
    };

    request();
    window.addEventListener('scroll', request, { passive: true });
    window.addEventListener('resize', request);
    return () => {
      window.removeEventListener('scroll', request);
      window.removeEventListener('resize', request);
      if (frame) window.cancelAnimationFrame(frame);
      values.clear();
    };
  }, [enabled, reducedMotion]);
}
