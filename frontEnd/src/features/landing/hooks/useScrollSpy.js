import { useEffect, useState } from 'react';

export default function useScrollSpy(ids) {
  const [activeId, setActiveId] = useState(null);
  useEffect(() => {
    const elements = ids.map((id) => document.getElementById(id)).filter(Boolean);
    if (!elements.length || !window.IntersectionObserver) return undefined;
    const observer = new IntersectionObserver((entries) => {
      const visible = entries.filter((entry) => entry.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
      if (visible) setActiveId(visible.target.id);
    }, { rootMargin: '-20% 0px -65% 0px', threshold: [0.01, 0.4] });
    elements.forEach((element) => observer.observe(element));
    return () => observer.disconnect();
  }, [ids]);
  return activeId;
}
