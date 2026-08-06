import { useEffect, useMemo, useReducer } from 'react';
import { demoSamples } from '../data/demoExperienceData.js';

const automaticNext = { uploading: 'uploadReview', structuring: 'structureReview', reviewing: 'reviewApproval', personas: 'personaSelection', integrating: 'completed' };
const automaticIncrement = { uploading: 12, structuring: 11, reviewing: 10, personas: 13, integrating: 16 };
const initialState = { state: 'idle', sampleId: 'pet', uploadProgress: 0, phaseProgress: 0, selectedRisks: ['privacy', 'price', 'retention'], selectedPersonas: [], showGaps: false };

function reducer(state, action) {
  if (action.type === 'SELECT_SAMPLE') return { ...initialState, state: 'fileSelected', sampleId: action.sampleId };
  if (action.type === 'START') return { ...state, state: action.state, uploadProgress: 0, phaseProgress: 0 };
  if (action.type === 'TICK') {
    const increment = automaticIncrement[state.state];
    if (!increment) return state;
    const progress = Math.min(100, (state.state === 'uploading' ? state.uploadProgress : state.phaseProgress) + increment);
    if (progress === 100) return { ...state, state: automaticNext[state.state], uploadProgress: state.state === 'uploading' ? 100 : state.uploadProgress, phaseProgress: 100 };
    return state.state === 'uploading' ? { ...state, uploadProgress: progress } : { ...state, phaseProgress: progress };
  }
  if (action.type === 'TOGGLE_RISK') return { ...state, selectedRisks: state.selectedRisks.includes(action.id) ? state.selectedRisks.filter((item) => item !== action.id) : [...state.selectedRisks, action.id] };
  if (action.type === 'TOGGLE_PERSONA') {
    if (state.selectedPersonas.includes(action.id)) return { ...state, selectedPersonas: state.selectedPersonas.filter((item) => item !== action.id) };
    if (state.selectedPersonas.length >= 2) return state;
    return { ...state, selectedPersonas: [...state.selectedPersonas, action.id] };
  }
  if (action.type === 'TOGGLE_GAPS') return { ...state, showGaps: !state.showGaps };
  if (action.type === 'RESET') return initialState;
  return state;
}

export default function useDemoExperience(reducedMotion) {
  const [state, dispatch] = useReducer(reducer, initialState);
  const automatic = Object.hasOwn(automaticNext, state.state);
  useEffect(() => {
    if (!automatic || reducedMotion) return undefined;
    const timer = window.setInterval(() => dispatch({ type: 'TICK' }), 360);
    return () => window.clearInterval(timer);
  }, [automatic, reducedMotion, state.state]);
  const sample = useMemo(() => demoSamples.find((item) => item.id === state.sampleId), [state.sampleId]);
  return { ...state, sample, dispatch, automatic, advance: () => dispatch({ type: 'TICK' }) };
}
