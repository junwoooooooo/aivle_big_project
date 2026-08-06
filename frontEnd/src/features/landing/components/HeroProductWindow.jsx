const menus = ['개요', '문서', '구조화', '법률 검토', '사업성 분석', '페르소나', '보고서'];

function SceneContent({ mode, scene }) {
  if (scene === 0) return <div className="hero-app__upload"><h4>프로젝트 <b>신규 사업 검증</b></h4><div className="hero-app__file"><strong>사업계획서_최종.docx</strong><span>2.8 MB</span></div><p>업로드 {mode === 'intro' ? '준비' : '진행률'} <b>{mode === 'intro' ? '0%' : '100%'}</b></p><i className="hero-app__progress"><span /></i><small>{mode === 'intro' ? '문서 등록을 준비하고 있습니다' : '✓ 문서 등록 완료 · 다음 단계: 문서 구조화'}</small></div>;
  if (scene === 1) return <div className="hero-app__structure"><div><h4>구조화된 사업계획</h4>{[['사업 개요', '완료'], ['제품·서비스', '완료'], ['목표 고객', '완료'], ['수익 모델', '보완 필요'], ['시장 근거', '검토 중']].map(([name, state]) => <p key={name}><span>{name}</span><b className={state === '보완 필요' ? 'is-warning' : ''}>{state}</b></p>)}</div><aside><span>원문 근거 <b>13개</b></span><span>보완 필요 <b>2개</b></span><span>사용자 확인 <b>1개</b></span></aside><small>구조화 진행률 <b>78%</b></small></div>;
  if (scene === 2) return <div className="hero-app__review"><h4>검토 현황</h4><div className="hero-app__review-grid"><section>{[['법률·규제', '진행 중'], ['시장성', '대기'], ['비즈니스 모델', '대기'], ['기술·운영', '대기']].map(([name, state]) => <p key={name}><span>{name}</span><b>{state}</b></p>)}</section><section><strong>현재 검토 항목</strong><p>개인정보 처리 범위 <b>추가 확인 필요</b></p><p>결제·환불 정책 <b>근거 확인 중</b></p><p>서비스 제공 책임 <b>검토 대기</b></p></section></div><small>확인된 근거 8개 · 검토 중 3개 · 추가 질문 4개</small></div>;
  return <div className="hero-app__summary"><h4>프로젝트 검증 요약</h4><div>{[['구조화 항목', '12개'], ['주요 위험', '4개'], ['검증이 필요한 가정', '6개'], ['추천 페르소나', '3개']].map(([name, value]) => <span key={name}>{name}<b>{value}</b></span>)}</div><ol><li>수익 모델 가격 근거 보완</li><li>개인정보 처리 범위 확인</li><li>핵심 타깃 인터뷰 진행</li></ol><button type="button">통합 결과 보기</button></div>;
}

export default function HeroProductWindow({ mode = 'hero', scene }) {
  const activeMenu = ['문서', '구조화', '법률 검토', '보고서'][scene];
  const stage = ['문서 업로드', '문서 구조화', '법률·사업성 검토', '결과 통합'][scene];
  return <div className={`hero-app-window hero-app-window--${mode}`} aria-hidden="true"><header><span /><span /><span /><strong>Venture Verify</strong><em>프로젝트 상태 · 진행 중</em></header><div className="hero-app-window__body"><nav>{menus.map((menu) => <span key={menu} className={menu === activeMenu ? 'is-active' : ''}>{menu}</span>)}</nav><main><div className="hero-app-window__stage">{stage}</div><div className="hero-app-window__content" key={scene}><SceneContent mode={mode} scene={scene} /></div><small>예시 프로젝트의 가상 데이터입니다.</small></main></div></div>;
}
