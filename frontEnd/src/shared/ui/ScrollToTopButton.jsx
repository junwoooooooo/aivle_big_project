import { useEffect, useState } from 'react';

import { AppIcon } from './icons.jsx';
import { scrollPageToTop } from './scroll.js';

export function ScrollToTopButton({ threshold = 700 }) {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const update = () => setVisible(window.scrollY >= threshold
      && document.documentElement.scrollHeight > window.innerHeight);
    update();
    window.addEventListener('scroll', update, { passive: true });
    window.addEventListener('resize', update);
    return () => {
      window.removeEventListener('scroll', update);
      window.removeEventListener('resize', update);
    };
  }, [threshold]);

  if (!visible) return null;
  return <button type="button" className="scroll-to-top" aria-label="페이지 맨 위로 이동" onClick={() => scrollPageToTop()}><AppIcon name="chevronUp" size={20} /></button>;
}
