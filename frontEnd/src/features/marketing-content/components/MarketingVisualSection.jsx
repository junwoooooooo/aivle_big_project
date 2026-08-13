import { useEffect, useMemo, useState } from 'react';
import { isUserVisibleJobEvent, jobEventMessage } from '../../../shared/async-events/index.js';
import useMarketingVisual from '../hooks/useMarketingVisual.js';
import { VISUAL_FORMATS, VISUAL_MOODS, validateVisualInput, visualDefaults, visualFailure } from '../model/marketingVisualModel.js';
import { FileDropzone } from '../../../shared/ui/index.js';

export default function MarketingVisualSection({ projectId, detail, revision, source, draft }) {
  const contentId = detail?.content?.contentId;
  const revisionId = revision?.revisionId;
  const visual = useMarketingVisual(projectId, contentId);
  const [form, setForm] = useState(() => visualDefaults(source, draft));
  const [file, setFile] = useState(null);
  const [filePreview, setFilePreview] = useState(null);
  const [notice, setNotice] = useState('');

  // 콘텐츠가 바뀌면 이전 콘텐츠의 편집값이 남지 않도록 서버 기준값으로 재설정한다.
  // eslint-disable-next-line react-hooks/set-state-in-effect, react-hooks/exhaustive-deps
  useEffect(() => { setForm(visualDefaults(source, draft)); }, [contentId, revisionId]);
  useEffect(() => {
    // 파일 선택과 수명 주기를 맞춘 브라우저 객체 URL이다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (!file) { setFilePreview(null); return undefined; }
    const url = URL.createObjectURL(file);
    setFilePreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);
  const result = visual.run?.result;
  const events = useMemo(() => (visual.events.events ?? []).filter(isUserVisibleJobEvent), [visual.events.events]);
  const latestEvent = events.at(-1);
  const failureCode = latestEvent?.technicalCode ?? visual.run?.errorCode ?? visual.error?.code;
  const set = (key, value) => setForm((current) => ({ ...current, [key]: value }));

  async function generate() {
    const invalid = validateVisualInput(form, file, contentId, revisionId);
    if (invalid) { setNotice(invalid); return; }
    setNotice('');
    try { await visual.create({ form, file, revisionId }); }
    catch (error) { setNotice(error.message); }
  }

  return <section className="mk-visual" aria-labelledby="mk-visual-title">
    <header className="mk-visual__header"><div><p>광고 이미지</p><h2 id="mk-visual-title">AI 광고 배너 생성</h2>
      <span>현재 마케팅 콘텐츠와 광고 표현 기준을 바탕으로 배너 문구와 이미지를 만듭니다.</span></div>
      <strong>기획 자료 1개</strong></header>
    <div className="mk-visual__source" aria-label="선택 상품과 기획 자료 요약"><div><span>선택 상품 / 기획 자료</span>
      <strong>{source?.conceptName ?? detail?.content?.title ?? '마케팅 기획 자료'}</strong>
      <p>{source?.valueProposition ?? '현재 기획 자료의 핵심 가치가 연결됩니다.'}</p></div>
      <dl><div><dt>대상 고객</dt><dd>{source?.targetSegment ?? '저장된 값 없음'}</dd></div>
        <div><dt>콘텐츠</dt><dd>{detail?.content?.contentType ?? '선택 필요'} · {detail?.content?.channel ?? '채널 미지정'}</dd></div>
        <div><dt>주요 특징</dt><dd>{source?.keyFeatures?.join(' · ') || '저장된 값 없음'}</dd></div>
        <div><dt>수정 이력</dt><dd>{revision ? `#${revision.revisionNumber}` : '선택 필요'}</dd></div></dl></div>
    <div className="mk-visual__grid"><div className="mk-visual__form project-form-layout">
      <label>프로모션 이름<input value={form.promotionName} maxLength={100} onChange={(event) => set('promotionName', event.target.value)} /></label>
      <label>메인 배너 문구<input value={form.mainBanner} maxLength={80} onChange={(event) => set('mainBanner', event.target.value)} /></label>
      <label>보조 문구<textarea value={form.supportingCopy} maxLength={150} onChange={(event) => set('supportingCopy', event.target.value)} /></label>
      <div className="mk-visual__row"><label>광고 분위기<select value={form.mood} onChange={(event) => set('mood', event.target.value)}>
        {VISUAL_MOODS.map((mood) => <option key={mood}>{mood}</option>)}</select></label>
        <label>배너 형식<select value={form.bannerFormat} onChange={(event) => set('bannerFormat', event.target.value)}>
          {VISUAL_FORMATS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label></div>
      <label>강조 키워드<input value={form.emphasisKeywords} placeholder="쉼표로 최대 10개" onChange={(event) => set('emphasisKeywords', event.target.value)} /></label>
      <FileDropzone id="mk-visual-image" label="이미지 선택" description="상품 이미지를 끌어 놓거나 선택하세요" acceptLabel="PNG, JPG 또는 WEBP · 최대 10MB" accept="image/png,image/jpeg,image/webp" files={file ? [file] : []} onFilesChange={(files) => { setFile(files[0] ?? null); setNotice(''); }} />
      {filePreview && <img className="mk-visual__selected-preview" src={filePreview} alt="업로드 파일 미리보기" />}
      <div className="mk-visual__legal"><strong>광고 표현 기준</strong>
        <p>허용 주장: {source?.allowedClaims?.join(' · ') || '등록 없음'}</p>
        <p>금지 주장: {source?.prohibitedClaims?.join(' · ') || '등록 없음'}</p>
        <p>필수 고지: {source?.requiredDisclosures?.join(' · ') || '등록 없음'}</p>
        <p>필수 통제: {source?.requiredControls?.join(' · ') || '등록 없음'}</p></div>
      {notice && <div className="mk-alert" role="alert">{notice}</div>}
      <button className="mk-primary" type="button" disabled={visual.busy || !contentId} onClick={() => void generate()}>
        {visual.busy ? '마케팅 이미지 생성 중…' : '광고 배너 만들기'}</button>
    </div><div className="mk-visual__result">
      {visual.busy && <div className="mk-visual__processing" aria-live="polite" aria-busy="true"><strong>마케팅 이미지 생성</strong>
        <p>{latestEvent ? jobEventMessage(latestEvent) : '입력을 확인하고 있습니다.'}</p>
        <ol>{events.map((event) => <li key={event.sequence} data-active={event === latestEvent}>{jobEventMessage(event)}</li>)}</ol>
        <button type="button" onClick={() => void visual.cancel()}>생성 취소</button></div>}
      {visual.run?.state === 'FAILED' && <div className="mk-visual__failure" role="alert"><strong>이미지 생성 실패</strong>
        <p>{visualFailure(failureCode)}</p>{visual.run.retryable && <button type="button" onClick={() => void visual.retry()}>다시 시도</button>}</div>}
      {!visual.busy && !result && visual.run?.state !== 'FAILED' && <div className="mk-visual__empty"><strong>배너 결과가 아직 없습니다.</strong>
        <p>{!contentId || !revisionId ? '먼저 마케팅 콘텐츠와 수정 이력을 선택해 주세요.'
          : '참고 이미지와 화면 입력을 확인한 뒤 광고 배너 만들기를 실행하세요.'}</p></div>}
      {result && <><div className="mk-visual__preview">{visual.previewUrl
        ? <img src={visual.previewUrl} alt="생성된 광고 배너" /> : <span>생성된 이미지 미리보기를 불러오는 중입니다.</span>}</div>
        <div className="mk-visual__copy"><span>{result.generatedCopy?.badge}</span><h3>{result.generatedCopy?.headline}</h3>
          <p>{result.generatedCopy?.subheadline}</p>{result.callToAction && <strong className="mk-visual__cta">{result.callToAction}</strong>}</div>
        <dl className="mk-visual__facts"><div><dt>사용한 자료</dt><dd>{source?.conceptName ?? '마케팅 기획 자료'}</dd></div>
          <div><dt>프로모션</dt><dd>{result.visual?.promotionName}</dd></div>
          <div><dt>분위기</dt><dd>{result.visual?.mood}</dd></div><div><dt>형식</dt><dd>{result.visual?.bannerFormat}</dd></div></dl>
        <details><summary>기술 정보</summary><p>이미지 모델 {result.banner?.model} · {result.banner?.size}</p></details>
        <div className="mk-visual__disclosures"><strong>필수 고지와 통제</strong>
          <p>{result.legalReview?.requiredDisclosuresApplied?.join(' · ') || '필수 고지 없음'}</p>
          <p>{result.legalReview?.requiredControlsApplied?.join(' · ') || '필수 통제 없음'}</p></div>
        <div className="mk-visual__actions"><button type="button" onClick={() => void generate()}>다시 만들기</button>
          <button className="mk-primary" type="button" onClick={() => void visual.download()}>광고 배너 저장 / 다운로드</button></div></>}
    </div></div>
  </section>;
}
