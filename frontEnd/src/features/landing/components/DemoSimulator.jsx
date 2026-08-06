import { useEffect, useRef, useState } from 'react';
import { phaseLabels } from '../data/demoExperienceData.js';
import useDemoExperience from '../hooks/useDemoExperience.js';
import DemoFilePicker from './demo/DemoFilePicker.jsx';
import DemoProgressPanel from './demo/DemoProgressPanel.jsx';
import DemoFinalResult from './demo/DemoFinalResult.jsx';
import { PersonaSelection, RiskSelection, StructureReview, UploadReview } from './demo/DemoReviews.jsx';

function DemoStepper({ state }) { const index = state === 'completed' ? 5 : state === 'uploading' || state === 'uploadReview' ? 0 : state === 'structuring' || state === 'structureReview' ? 1 : state === 'reviewing' || state === 'reviewApproval' ? 2 : state === 'personas' || state === 'personaSelection' ? 3 : 4; return <ol className="demo-stepper" aria-label="가상 데모 진행 단계">{phaseLabels.map((label, step) => <li key={label} className={step <= index ? 'is-active' : ''}><span>{step + 1}</span><b>{label}</b></li>)}</ol>; }

export default function DemoSimulator({ reducedMotion }) {
  const demo = useDemoExperience(reducedMotion);
  const [dragging, setDragging] = useState(false);
  const resultRef = useRef();
  useEffect(() => {
    if (demo.state === 'completed') resultRef.current?.focus();
  }, [demo.state]);
  const select = (sampleId) => demo.dispatch({ type: 'SELECT_SAMPLE', sampleId });
  const onDrop = (event) => { event.preventDefault(); setDragging(false); select(event.dataTransfer.getData('sampleId') || 'pet'); };
  const reset = () => demo.dispatch({ type: 'RESET' });
  const progress = demo.state === 'uploading' ? demo.uploadProgress : demo.phaseProgress;
  const panel = demo.state === 'idle' || demo.state === 'fileSelected' ? <div className={`demo-dropzone${dragging ? ' is-dragging' : ''}`} onDragEnter={(event) => { event.preventDefault(); setDragging(true); }} onDragOver={(event) => event.preventDefault()} onDragLeave={() => setDragging(false)} onDrop={onDrop}><DemoFilePicker selectedId={demo.sampleId} onSelect={select} />{demo.state === 'fileSelected' && <button className="landing-button" type="button" onClick={() => demo.dispatch({ type: 'START', state: 'uploading' })}>이 파일로 데모 시작</button>}{dragging && <span className="demo-dropzone__hint">파일을 놓아 선택하세요</span>}</div> : demo.automatic ? <DemoProgressPanel state={demo.state} progress={progress} sample={demo.sample} /> : demo.state === 'uploadReview' ? <UploadReview sample={demo.sample} onStructure={() => demo.dispatch({ type: 'START', state: 'structuring' })} onReset={reset} /> : demo.state === 'structureReview' ? <StructureReview showGaps={demo.showGaps} onToggleGaps={() => demo.dispatch({ type: 'TOGGLE_GAPS' })} onReview={() => demo.dispatch({ type: 'START', state: 'reviewing' })} /> : demo.state === 'reviewApproval' ? <RiskSelection selected={demo.selectedRisks} onToggle={(id) => demo.dispatch({ type: 'TOGGLE_RISK', id })} onPersonas={() => demo.dispatch({ type: 'START', state: 'personas' })} /> : demo.state === 'personaSelection' ? <PersonaSelection selected={demo.selectedPersonas} onToggle={(id) => demo.dispatch({ type: 'TOGGLE_PERSONA', id })} onIntegrate={() => demo.dispatch({ type: 'START', state: 'integrating' })} /> : <DemoFinalResult sample={demo.sample} selectedRisks={demo.selectedRisks} selectedPersonas={demo.selectedPersonas} onReset={reset} />;
  return <div className="demo-simulator"><DemoStepper state={demo.state} />{demo.state === 'completed' ? <div ref={resultRef} tabIndex="-1">{panel}</div> : panel}{reducedMotion && demo.automatic && <button type="button" className="landing-text-button" onClick={demo.advance}>현재 처리 완료하기</button>}{demo.state !== 'idle' && demo.state !== 'completed' && <button type="button" className="demo-reset-link" onClick={reset}>데모 처음부터 다시 시작</button>}</div>;
}
