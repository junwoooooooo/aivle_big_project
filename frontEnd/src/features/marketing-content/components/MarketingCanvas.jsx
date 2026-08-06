export default function MarketingCanvas({ result, style }) {
  if (!result) return <section className="mk-canvas mk-canvas--empty"><p>생성된 콘텐츠가 이곳에 표시됩니다.</p></section>;
  return <section className="mk-canvas" aria-label="마케팅 콘텐츠 미리보기" style={{ '--mk-accent': style.accent, '--mk-scale': style.scale }}>
    <article data-align={style.align} data-theme={style.theme}>
      <span>{result.contentType.replaceAll('_',' ')}</span><h2>{result.title || 'Headline'}</h2><p>{result.body || '본문을 입력하세요.'}</p>
      {result.callToAction && <strong>{result.callToAction}</strong>}
      {result.hashtags?.length > 0 && <ul aria-label="해시태그">{result.hashtags.map((tag)=><li key={tag}>#{tag.replace(/^#/,'')}</li>)}</ul>}
      {result.imageBrief && <aside><b>이미지 설명</b>{result.imageBrief}</aside>}
      {result.legalReview?.requiredDisclosuresApplied?.length > 0 && <footer>{result.legalReview.requiredDisclosuresApplied.join(' · ')}</footer>}
    </article>
  </section>;
}
