import { introClassifications } from '../../data/introStreamItems.js';

export default function ValidationClassification({ phase }) {
  return <div className={`validation-classification phase-${phase}`} aria-label="가상 예시 데이터 분류"><div className="validation-classification__cards">{introClassifications.map((item) => <article key={item.id}><span>{item.label}</span><b>{item.value}</b></article>)}</div><small>가상 예시 데이터</small></div>;
}
