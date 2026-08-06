export default function IntroRevealLayer({ phase }) {
  return <div className={`validation-reveal-layer phase-${phase}`} aria-hidden="true" />;
}
