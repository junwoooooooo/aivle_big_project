import { Link, useParams } from 'react-router-dom';

import {
  Alert,
  Card,
  EmptyState,
  ErrorState,
  PageHeader,
  StatusBadge,
} from '../shared/ui/index.js';
import { useProjectContext } from '../features/projects/ProjectContext.jsx';
import './pages.css';

export function HomePage() {
  return (
    <div className="home-page">
      <section className="home-hero">
        <p className="home-hero__eyebrow">AI 기반 사업 검증</p>
        <h1>아이디어를 근거 있는 다음 단계로 연결하세요</h1>
        <p>
          문서 구조화부터 시장·사업성 검토까지, 현재 상태와 필요한 보완점을
          한 흐름에서 확인할 수 있도록 준비하고 있습니다.
        </p>
        <div className="home-hero__actions">
          <Link className="primary-link" to="/auth/login">로그인하고 시작하기</Link>
          <Link to="/auth/signup">계정 만들기</Link>
        </div>
      </section>
      <section aria-labelledby="principles-title" className="foundation-grid">
        <h2 id="principles-title">검증 과정을 명확하게</h2>
        <Card><h3>현재 상태</h3><p>진행 단계와 필요한 입력을 먼저 보여줍니다.</p></Card>
        <Card><h3>근거</h3><p>분석 결과가 어떤 자료에서 왔는지 추적합니다.</p></Card>
        <Card><h3>다음 행동</h3><p>보완하거나 확인할 수 있는 경로를 제공합니다.</p></Card>
      </section>
    </div>
  );
}

export function AuthPlaceholderPage({ mode }) {
  const copy = {
    login: ['로그인', 'Phase 5에서 인증 API와 연결합니다.'],
    signup: ['회원가입', 'Phase 5에서 회원가입 API와 연결합니다.'],
    reset: ['비밀번호 재설정', '백엔드 계약 확정 후 제공할 예정입니다.'],
  }[mode];
  return (
    <div className="narrow-page">
      <PageHeader title={copy[0]} description={copy[1]} />
      <Alert title="기능 연결 준비 중">가짜 계정이나 임시 토큰은 사용하지 않습니다.</Alert>
      {mode !== 'login' && <Link to="/auth/login">로그인으로 돌아가기</Link>}
    </div>
  );
}

export function DashboardPage() {
  return (
    <>
      <PageHeader
        title="대시보드"
        description="진행할 작업과 최근 프로젝트를 확인하는 화면입니다."
        actions={<Link className="primary-link" to="/projects/new">새 프로젝트</Link>}
      />
      <Card className="placeholder-card">
        <h2>프로젝트에서 검증을 시작하세요</h2>
        <p>실제 프로젝트 목록과 최근 수정 상태를 확인할 수 있습니다.</p>
        <Link to="/projects">프로젝트 목록 보기</Link>
      </Card>
    </>
  );
}

export function ProjectsPage() {
  return (
    <>
      <PageHeader title="프로젝트" description="사업 검증 프로젝트 목록입니다." />
      <EmptyState
        title="프로젝트 목록 연결 준비 중"
        description="현재는 API 결과를 가장하지 않습니다."
        action={<Link to="/projects/new">새 프로젝트</Link>}
      />
    </>
  );
}

export function NewProjectPage() {
  return (
    <>
      <PageHeader
        title="새 프로젝트"
        description="프로젝트 생성 폼은 Phase 5에서 실제 API 계약과 함께 구현합니다."
      />
      <Alert title="준비 중">프로젝트 이름과 설명의 서버 검증 규칙을 연결할 예정입니다.</Alert>
    </>
  );
}

const PROJECT_COPY = {
  overview: ['프로젝트 개요', '현재 상태와 다음 권장 행동을 확인합니다.'],
  documents: ['문서', '사업계획 문서 업로드와 처리 상태를 관리합니다.'],
  'structured-plan': ['구조화된 사업계획서', '추출된 항목과 보완이 필요한 내용을 확인합니다.'],
  'missing-fields': ['누락 항목 보완', '필요한 정보를 입력하고 다시 검토합니다.'],
  'legal-review': ['법률 검토', '관련 위험과 확인할 근거를 정리합니다.'],
  market: ['시장 분석', '시장 근거와 검토 결과를 확인합니다.'],
  'business-model': ['사업 모델 분석', '수익 구조와 가정을 검토합니다.'],
  'technology-operation': ['기술·운영 분석', '실행 가능성과 운영 위험을 검토합니다.'],
  financial: ['재무 분석', '재무 가정과 주요 지표를 검토합니다.'],
  personas: ['페르소나', 'AI 패널 구성의 기반을 검토합니다.'],
  'panel-survey': ['AI 패널 조사', 'AI 패널 조사 진행과 응답을 확인합니다.'],
  'panel-discussion': ['AI 패널 토론', 'AI 패널 토론 진행과 논점을 확인합니다.'],
  'market-validation': ['시장 검증', '조사와 토론 결과를 종합합니다.'],
  reports: ['보고서', '프로젝트 보고서 목록을 확인합니다.'],
  'report-detail': ['보고서 상세', '버전과 근거가 연결된 보고서를 확인합니다.'],
  marketing: ['마케팅', '검증 결과를 바탕으로 후속 자료를 준비합니다.'],
  settings: ['프로젝트 설정', '프로젝트 정보와 접근 설정을 관리합니다.'],
};

export function ProjectPlaceholderPage({ page }) {
  const { projectId, reportId } = useParams();
  const [title, description] = PROJECT_COPY[page];
  return (
    <>
      <PageHeader
        eyebrow={`프로젝트 ${projectId}${reportId ? ` · 보고서 ${reportId}` : ''}`}
        title={title}
        description={description}
      />
      <Card className="placeholder-card">
        <StatusBadge status="QUEUED" />
        <h2>기능 연결 준비 중</h2>
        <p>
          이 화면은 최종 정보구조와 직접 진입 경로를 검증하기 위한 자리표시자입니다.
          실제 분석 결과는 표시하지 않습니다.
        </p>
      </Card>
    </>
  );
}

export function LegalReviewHandoffPage() {
  const { project } = useProjectContext();
  const ready = project.stage === 'LEGAL_REVIEW';
  return (
    <>
      <PageHeader
        eyebrow={project.stageLabel}
        title="법률·규제 검토"
        description="확정된 구조화 사업계획을 다음 분석 단계의 입력으로 사용합니다."
      />
      {ready ? (
        <Card className="placeholder-card">
          <StatusBadge status="QUEUED" />
          <h2>법률·규제 검토를 시작할 준비가 되었습니다</h2>
          <p>
            구조화된 사업계획은 확정되었습니다. 실제 법률 분석 실행과 결과 화면은
            Phase 8에서 서버 계약과 함께 연결합니다.
          </p>
          <Alert title="AI 사전 검토 안내">
            이 단계의 결과는 법률 자문을 대체하지 않으며, 실제 분석이 시작되기 전에는
            임의의 판정이나 근거를 표시하지 않습니다.
          </Alert>
        </Card>
      ) : (
        <Card className="placeholder-card">
          <StatusBadge status="NEEDS_INPUT" />
          <h2>구조화된 사업계획을 먼저 확정해 주세요</h2>
          <p>필수 보완 항목을 해결하고 사업계획을 확정하면 이 단계를 진행할 수 있습니다.</p>
          <Link className="primary-link" to="../structure">구조화 결과 확인</Link>
        </Card>
      )}
    </>
  );
}

export function SimplePlaceholderPage({ title, description }) {
  return (
    <>
      <PageHeader title={title} description={description} />
      <EmptyState title="연결 준비 중" description="후속 Phase에서 실제 기능을 연결합니다." />
    </>
  );
}

export function NotFoundPage() {
  return (
    <ErrorState
      title="페이지를 찾을 수 없습니다"
      description="주소를 확인하거나 프로젝트 목록에서 다시 시작해 주세요."
    />
  );
}
