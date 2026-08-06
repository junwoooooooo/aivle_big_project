import { demoSamples } from '../../data/demoExperienceData.js';

export default function DemoFilePicker({ selectedId, onSelect }) {
  return <section className="demo-picker"><h3>샘플 사업계획서를 업로드해 보세요</h3><p>가상 파일을 선택하거나 드래그해 서비스 흐름을 체험할 수 있습니다.</p><div className="demo-file-options">{demoSamples.map((sample) => <button type="button" draggable key={sample.id} className={sample.id === selectedId ? 'is-selected' : ''} onClick={() => onSelect(sample.id)} onDragStart={(event) => event.dataTransfer.setData('sampleId', sample.id)}><b>DOCX</b><span>{sample.fileName}<small>{sample.id === 'pet' ? '2.8 MB' : '가상 데모 파일'}</small></span></button>)}</div></section>;
}
