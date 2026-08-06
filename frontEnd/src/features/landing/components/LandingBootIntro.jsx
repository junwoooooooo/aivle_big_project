import { useEffect } from 'react';
import IntroBackground from './intro/IntroBackground.jsx';
import IntroCollapseCore from './intro/IntroCollapseCore.jsx';
import IntroProductWindow from './intro/IntroProductWindow.jsx';
import IntroRevealLayer from './intro/IntroRevealLayer.jsx';
import ValidationClassification from './intro/ValidationClassification.jsx';
import ValidationCore from './intro/ValidationCore.jsx';
import ValidationStream from './intro/ValidationStream.jsx';

export default function LandingBootIntro({ onSkip, reducedMotion, state }) {
  useEffect(() => {
    if (state === 'completed') return undefined;
    const onKeyDown = (event) => { if (event.key === 'Escape') onSkip(); };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onSkip, state]);
  if (state === 'completed') return null;
  return <section className={`landing-boot-intro landing-validation-intro phase-${state}${reducedMotion ? ' is-reduced-motion' : ''}`} aria-label="Venture Verify 브랜드 시작 화면"><IntroBackground /><IntroRevealLayer phase={state} /><button className="landing-boot-intro__skip" type="button" onClick={onSkip}>건너뛰기</button><div className="validation-intro__identity"><b>VENTURE VERIFY</b><span>아이디어를 검토 가능한 다음 단계로</span></div><ValidationStream phase={state} /><ValidationCore phase={state} /><ValidationClassification phase={state} /><IntroProductWindow phase={state} /><IntroCollapseCore phase={state} /><p className="visually-hidden">Venture Verify 브랜드 연출입니다. 실제 분석이나 파일 처리는 실행되지 않습니다.</p></section>;
}
