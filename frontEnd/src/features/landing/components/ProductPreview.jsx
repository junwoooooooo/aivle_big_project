const rows = {
  upload: [['사업계획서_최종.docx', '2.8 MB'], ['업로드 100%', '문서 등록 완료']],
  project: [['사업명', '반려동물 건강관리 구독'], ['사업계획서_최종.docx', '업로드 완료']],
  structure: [['사업 개요', '완료'], ['목표 고객', '완료'], ['수익 모델', '보완 필요'], ['시장 근거', '검토 중']],
  review: [['법률·규제 검토', '진행 중'], ['시장성 분석', '대기'], ['비즈니스 모델 분석', '대기'], ['기술·운영 분석', '대기']],
  personas: [['20대 여성', '디지털 소비 적극형'], ['30대 남성', '구독 중심 실용형'], ['40대 여성', '신중한 가치 소비형']],
  summary: [['확인된 근거', '18개'], ['주요 위험', '4개'], ['추가 검증 과제', '6개'], ['추천 페르소나', '3개']],
};

export default function ProductPreview({ kind = 'summary', label = '예시 프로젝트 화면' }) {
  const items = rows[kind] || rows.summary;
  return <div className="product-preview" aria-label={label}>
    <div className="product-preview__bar"><span /><span /><span /><strong>{kind === 'project' ? '새 사업 검증 프로젝트' : kind === 'summary' ? '검증 요약' : '검토 현황'}</strong></div>
    <div className="product-preview__body">
      {items.map(([name, state]) => <div className="product-preview__row" key={name}><span>{name}</span><b className={state.includes('보완') ? 'is-warning' : ''}>{state}</b></div>)}
      {kind === 'structure' && <p className="product-preview__note">원문 근거 · 사용자 입력 · 확정</p>}
      {kind === 'review' && <p className="product-preview__note">전체 진행률 <strong>42%</strong></p>}
      {kind === 'personas' && <p className="product-preview__note">추천 근거 · 확인할 가설 · 조사 질문</p>}
      {kind === 'summary' && <button type="button" className="product-preview__cta">통합 결과 보기</button>}
    </div>
    <small>예시 프로젝트의 가상 데이터입니다.</small>
  </div>;
}
