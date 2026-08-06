export default function IntroCollapseCore({ phase }) {
  return <div className={`validation-collapse-core phase-${phase}`} aria-hidden="true"><span>V</span><i /><small>검증 흐름 준비 완료</small></div>;
}
