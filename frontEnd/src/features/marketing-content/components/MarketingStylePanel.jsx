export default function MarketingStylePanel({ value, onChange }) {
  const set = (key) => (event) => onChange({ ...value, [key]: event.target.value });
  return <section className="mk-panel" aria-labelledby="mk-style-title"><div className="mk-panel__heading"><div><p>Preview 스타일</p><h2 id="mk-style-title">표현 방식</h2></div></div>
    <label className="mk-field"><span>테마</span><select value={value.theme} onChange={set('theme')}><option value="LIGHT">밝게</option><option value="DARK">어둡게</option><option value="ACCENT">강조</option></select></label>
    <label className="mk-field"><span>정렬</span><select value={value.align} onChange={set('align')}><option value="LEFT">왼쪽</option><option value="CENTER">가운데</option></select></label>
    <label className="mk-field"><span>강조색</span><input type="color" value={value.accent} onChange={set('accent')} /></label>
    <label className="mk-field"><span>글자 크기</span><input type="range" min="0.85" max="1.25" step="0.05" value={value.scale} onChange={set('scale')} /></label>
    <small>Preview 스타일은 콘텐츠 문구 revision과 별도로 현재 화면에만 적용됩니다.</small>
  </section>;
}
