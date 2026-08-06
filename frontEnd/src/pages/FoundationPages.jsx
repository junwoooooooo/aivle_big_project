import { Link } from 'react-router-dom';
import { Alert, ErrorState, PageHeader } from '../shared/ui/index.js';
import './pages.css';

export function AuthPlaceholderPage({ mode }) {
  const copy = {
    login: ['로그인', '인증 화면으로 이동합니다.'],
    signup: ['회원가입', '계정을 만들어 시작합니다.'],
    reset: ['비밀번호 재설정', '비밀번호 재설정 흐름을 시작합니다.'],
  }[mode];
  return <div className="narrow-page">
    <PageHeader title={copy[0]} description={copy[1]} />
    <Alert title="인증 화면 준비 중">임시 토큰이나 샘플 계정은 제공하지 않습니다.</Alert>
    {mode !== 'login' && <Link to="/auth/login">로그인으로 돌아가기</Link>}
  </div>;
}

export function NotFoundPage() {
  return <ErrorState title="페이지를 찾을 수 없습니다" description="주소를 확인하거나 프로젝트 목록에서 다시 시작해 주세요." />;
}
