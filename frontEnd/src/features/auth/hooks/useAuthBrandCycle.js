import { useEffect, useState } from 'react';

export default function useAuthBrandCycle({ enabled = true, paused = false, reducedMotion, sceneCount }) {
  const [sceneIndex, setSceneIndex] = useState(0);

  useEffect(() => {
    if (!enabled || paused || reducedMotion) return undefined;
    const interval = window.setInterval(() => {
      if (!document.hidden) setSceneIndex((current) => (current + 1) % sceneCount);
    }, 7500);
    return () => window.clearInterval(interval);
  }, [enabled, paused, reducedMotion, sceneCount, sceneIndex]);

  return { sceneIndex, setSceneIndex };
}
