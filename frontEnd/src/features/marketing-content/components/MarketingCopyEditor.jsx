import { applyEditorAction } from '../model/marketingContentModel.js';

export default function MarketingCopyEditor({ value, source, onChange, onRevisionType }) {
  const set = (key, transform = (v) => v) => (event) => { onRevisionType('USER_EDITED'); onChange({ ...value, [key]: transform(event.target.value) }); };
  const apply = (action) => { const next=applyEditorAction(value,action,source); onRevisionType(next.revisionType); onChange(next.result); };
  return <section className="mk-panel mk-editor" aria-labelledby="mk-editor-title"><div className="mk-panel__heading"><div><p>결과 편집</p><h2 id="mk-editor-title">Copy Editor</h2></div></div>
    <div className="mk-editor__tools" aria-label="부분 다듬기"><button type="button" onClick={()=>apply('SHORTEN')}>짧은 문구로 다듬기</button><button type="button" onClick={()=>apply('LEGAL')}>필수 고지 적용</button></div>
    <div className="project-form-layout"><label className="mk-field"><span>제목</span><input value={value.title} maxLength="200" onChange={set('title')} /></label>
    <label className="mk-field"><span>본문</span><textarea value={value.body} rows="8" maxLength="20000" onChange={set('body')} /></label>
    <label className="mk-field"><span>CTA</span><input value={value.callToAction ?? ''} maxLength="500" onChange={set('callToAction',(v)=>v||null)} /></label>
    <label className="mk-field"><span>해시태그</span><input value={value.hashtags.join(', ')} onChange={set('hashtags',(v)=>v.split(',').map(x=>x.trim().replace(/^#/,'')).filter(Boolean).slice(0,30))} /></label>
    <label className="mk-field"><span>이미지 설명</span><textarea value={value.imageBrief ?? ''} rows="4" onChange={set('imageBrief',(v)=>v||null)} /></label>
    <label className="mk-field"><span>필수 고지</span><textarea value={value.legalReview.requiredDisclosuresApplied.join('\n')} rows="4" onChange={(event)=>{onRevisionType('LEGAL_NOTICE_APPLIED');onChange({...value,legalReview:{...value.legalReview,requiredDisclosuresApplied:event.target.value.split('\n').map(x=>x.trim()).filter(Boolean)}});}} /></label></div>
  </section>;
}
