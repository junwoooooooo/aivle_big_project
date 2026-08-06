import HeroProductWindow from '../HeroProductWindow.jsx';

export default function IntroProductWindow({ phase }) {
  const assembled = phase === 'assembling' || phase === 'collapsing';
  return <div className={`intro-product-window phase-${phase}${assembled ? ' is-assembled' : ''}`} aria-hidden="true"><HeroProductWindow mode="intro" scene={0} /></div>;
}
