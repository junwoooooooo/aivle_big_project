import { introStreamLanes } from '../../data/introStreamItems.js';

export default function ValidationStream({ phase }) {
  return <div className={`validation-stream phase-${phase}`} aria-hidden="true">{introStreamLanes.map((lane, laneIndex) => <div className={`validation-stream__lane lane-${laneIndex + 1}`} key={`lane-${laneIndex}`}>{lane.map((item) => <div className={`validation-stream__item is-${item.type}${item.absorb ? ' is-absorbable' : ''}`} key={item.label}>{item.type === 'file' && <span className="validation-stream__file-icon">DOCX</span>}<span>{item.label}</span>{item.meta && <small>{item.meta}</small>}</div>)}</div>)}</div>;
}
