import { useEffect, useMemo, useState } from 'react';

const steps = {
  BODY_FIRST: 'TYPE_BODY_FIRST',
  BODY_SECOND: 'TYPE_BODY_SECOND',
  FADE: 'FADE_OUT',
  HOLD: 'HOLD_COMPLETE',
  IDLE: 'IDLE',
  PAUSE_BODY: 'PAUSE_BODY_FIRST',
  PAUSE_TITLE: 'PAUSE_TITLE_COMPLETE',
  PAUSE_TITLE_FIRST: 'PAUSE_TITLE_FIRST',
  TITLE_FIRST: 'TYPE_TITLE_FIRST',
  TITLE_SECOND: 'TYPE_TITLE_SECOND',
  WAIT: 'WAIT_FOR_AUTH_READY',
};

const typingMap = {
  [steps.TITLE_FIRST]: [0, steps.PAUSE_TITLE_FIRST, 88],
  [steps.TITLE_SECOND]: [1, steps.PAUSE_TITLE, 88],
  [steps.BODY_FIRST]: [2, steps.PAUSE_BODY, 58],
  [steps.BODY_SECOND]: [3, steps.HOLD, 58],
};
const stepDelay = { [steps.WAIT]: 380, [steps.PAUSE_TITLE_FIRST]: 450, [steps.PAUSE_TITLE]: 900, [steps.PAUSE_BODY]: 500, [steps.HOLD]: 7800, [steps.FADE]: 760, [steps.IDLE]: 4000 };
const nextStep = { [steps.WAIT]: steps.TITLE_FIRST, [steps.PAUSE_TITLE_FIRST]: steps.TITLE_SECOND, [steps.PAUSE_TITLE]: steps.BODY_FIRST, [steps.PAUSE_BODY]: steps.BODY_SECOND, [steps.HOLD]: steps.FADE, [steps.FADE]: steps.IDLE, [steps.IDLE]: steps.TITLE_FIRST };
const segment = (value) => typeof Intl !== 'undefined' && Intl.Segmenter ? Array.from(new Intl.Segmenter('ko', { granularity: 'grapheme' }).segment(value), ({ segment: item }) => item) : Array.from(value);

export default function useBrandCopyTyping(lines, { enabled = true, paused = false, reducedMotion = false } = {}) {
  // Callers commonly create the four-line array inline.  A stable key keeps a
  // character tick from restarting the state machine on every render.
  const lineKey = lines.join('\u0000');
  const graphemes = useMemo(() => lineKey.split('\u0000').map(segment), [lineKey]);
  const [state, setState] = useState(() => ({ indexes: reducedMotion ? graphemes.map((line) => line.length) : [0, 0, 0, 0], step: reducedMotion ? steps.HOLD : steps.WAIT }));
  const [hidden, setHidden] = useState(() => document.hidden);

  useEffect(() => { const listener = () => setHidden(document.hidden); document.addEventListener('visibilitychange', listener); return () => document.removeEventListener('visibilitychange', listener); }, []);
  useEffect(() => {
    if (!enabled || paused || hidden) return undefined;
    if (reducedMotion) { const timer = window.setTimeout(() => setState({ indexes: graphemes.map((line) => line.length), step: steps.HOLD }), 0); return () => window.clearTimeout(timer); }
    const typing = typingMap[state.step];
    if (typing) {
      const [lineIndex, completedStep, baseDelay] = typing;
      if (state.indexes[lineIndex] >= graphemes[lineIndex].length) { const timer = window.setTimeout(() => setState((current) => ({ ...current, step: completedStep })), 0); return () => window.clearTimeout(timer); }
      const currentCharacter = graphemes[lineIndex][state.indexes[lineIndex]];
      const delay = baseDelay + (currentCharacter === ',' ? 160 : currentCharacter === '.' ? 280 : 0);
      const timer = window.setTimeout(() => setState((current) => ({ ...current, indexes: current.indexes.map((value, index) => index === lineIndex ? value + 1 : value) })), delay);
      return () => window.clearTimeout(timer);
    }
    const timer = window.setTimeout(() => setState((current) => state.step === steps.FADE ? { indexes: [0, 0, 0, 0], step: nextStep[state.step] } : { ...current, step: nextStep[state.step] }), stepDelay[state.step]);
    return () => window.clearTimeout(timer);
  }, [enabled, graphemes, hidden, paused, reducedMotion, state]);
  const values = graphemes.map((line, index) => line.slice(0, state.indexes[index]).join(''));
  return { fading: state.step === steps.FADE, step: state.step, values };
}
