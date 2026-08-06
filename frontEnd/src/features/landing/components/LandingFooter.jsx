import { useEffect, useRef, useState } from 'react';

import { navItems } from '../data/landingData.js';

const policies = {
  '이용 안내': '정식 이용 안내 문서는 서비스 공개 전에 확정될 예정입니다.',
  개인정보처리방침: '로컬 실행 환경에서도 개인정보와 실제 API Key를 화면에 노출하지 마세요.',
  'AI 결과 이용 안내': 'AI 결과는 검토를 위한 참고 정보이며 법률·재무·투자 자문을 대체하지 않습니다.',
};

export default function LandingFooter({ onNavigate }) {
  const [policy, setPolicy] = useState(null);
  const closeRef = useRef();

  useEffect(() => {
    if (policy) closeRef.current?.focus();
  }, [policy]);

  useEffect(() => {
    const onKeyDown = (event) => { if (event.key === 'Escape') setPolicy(null); };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  return (
    <footer className="landing-footer">
      <div className="landing-container">
        <div className="landing-footer__brand"><strong>Venture Verify</strong><p>AI 기반 사업 아이디어 검토·의사결정 지원 플랫폼</p></div>
        <div className="landing-footer__groups">
          <nav aria-labelledby="footer-service-heading"><h3 id="footer-service-heading">서비스 둘러보기</h3>{navItems.map(([id, label]) => <button type="button" onClick={() => onNavigate(id)} key={id}>{label}</button>)}</nav>
          <nav aria-labelledby="footer-policy-heading"><h3 id="footer-policy-heading">정책 및 안내</h3>{Object.keys(policies).map((label) => <button type="button" onClick={() => setPolicy(label)} key={label}>{label}</button>)}</nav>
        </div>
        <small>본 서비스의 AI 분석 결과는 법률·재무·투자 자문을 대체하지 않습니다. · © 2026 Venture Verify</small>
      </div>
      {policy && <div className="policy-dialog-backdrop" role="presentation" onMouseDown={() => setPolicy(null)}><section className="policy-dialog" role="dialog" aria-modal="true" aria-labelledby="policy-title" onMouseDown={(event) => event.stopPropagation()}><button ref={closeRef} className="policy-dialog__close" type="button" aria-label="안내 닫기" onClick={() => setPolicy(null)}>×</button><p className="landing-eyebrow">정책 및 안내</p><h2 id="policy-title">{policy}</h2><p>{policies[policy]}</p><button type="button" className="landing-button landing-button--small" onClick={() => setPolicy(null)}>확인</button></section></div>}
    </footer>
  );
}
