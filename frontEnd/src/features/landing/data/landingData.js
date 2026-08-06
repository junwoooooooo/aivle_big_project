export const navItems = [
  ['intro', '서비스 소개'],
  ['workflow', 'Journey'],
  ['features', '주요 기능'],
  ['faq', 'FAQ'],
  ['demo', '미리보기'],
];

export const heroSlides = [
  { title: '아이디어를 입력하고 구조화하세요', description: '텍스트나 파일을 저장하고 사실, 가정, 제약과 추가 질문을 구분합니다.', kind: 'upload' },
  { title: '법률과 Concept를 단계적으로 검토하세요', description: 'AI 사전 검토 후 세 가지 사업 방향을 생성하고 비교합니다.', kind: 'structure' },
  { title: 'Persona Interview에서 Marketing 전략까지', description: '합성 Persona별 독립 Interview와 Marketing Asset을 저장합니다.', kind: 'review' },
  { title: '선택 근거를 Final Report로', description: '사용자의 선택과 AI 분석을 Section 기반 보고서로 복원합니다.', kind: 'summary' },
];

export const workflowSteps = [
  { number: '01', title: '아이디어를 저장하고 AI 해석을 확정합니다.', description: '텍스트 또는 DOCX/TXT 입력을 구조화하고 현재 Idea Version을 사용자가 확정합니다.', kind: 'project' },
  { number: '02', title: '법률 사전 검토와 Concept 비교를 진행합니다.', description: 'AI 사전 검토 조건을 확인하고 세 가지 Concept를 생성·평가해 최종 후보를 선택합니다.', kind: 'structure' },
  { number: '03', title: '합성 Persona와 독립 Interview를 실행합니다.', description: '세 Persona 관점의 질문과 답변을 저장하고 공통점, 차이점과 조사 필요를 종합합니다.', kind: 'review' },
  { number: '04', title: 'Marketing 메시지와 채널 가설을 비교합니다.', description: 'Persona별 적합성과 위험을 질적으로 비교하고 사용할 Asset을 직접 선택합니다.', kind: 'personas' },
  { number: '05', title: '근거와 선택을 Final Report로 정리합니다.', description: 'AI Decision 제안과 사용자 Decision을 구분해 저장하고 인쇄 또는 PDF로 내보냅니다.', kind: 'summary' },
];

export const featureItems = [
  ['아이디어 입력과 AI 해석', '원문 요약, 정규화 설명, 사실, 가정, 제약과 추가 질문을 분리합니다.', 'wide'],
  ['법률 사전 검토', '주요 이슈와 조건을 정리하되 공식 법률 자문이나 출처 검증 완료로 표현하지 않습니다.', 'wide'],
  ['Concept 생성과 비교', '세 후보를 평가하고 재무 가정을 직접 입력해 최종 Concept를 선택합니다.', ''],
  ['합성 Persona Interview', 'Persona별 독립 질문과 답변을 저장하고 공통점과 상충 의견을 종합합니다.', ''],
  ['Marketing Workspace', '메시지와 채널 가설을 Persona 관점에서 비교하고 사용할 Asset을 선택합니다.', ''],
  ['Final Report', '전체 근거, 위험, 조사 필요와 다음 행동을 보고서로 저장합니다.', ''],
];

export const faqItems = [
  ['어떤 입력을 사용할 수 있나요?', '아이디어를 직접 입력하거나 지원되는 DOCX/TXT 파일을 업로드할 수 있습니다.'],
  ['AI가 사업 성공 가능성을 확정하나요?', '아닙니다. 사실, 가정, 위험과 후속 검증 과제를 구조화하는 의사결정 지원 도구입니다.'],
  ['법률 검토 결과는 공식 자문인가요?', '아닙니다. AI 사전 검토이며 sourceVerified=false로 표시됩니다. 실제 적용 여부는 전문가나 관계 기관에 추가 확인해야 합니다.'],
  ['Persona는 실제 고객 데이터인가요?', '아닙니다. 선택한 Concept를 검토하기 위한 합성 사용자이며 실제 고객 조사나 수요 예측을 대체하지 않습니다.'],
  ['결과는 새로고침 후에도 남나요?', '저장된 Idea, Concept, Persona, Marketing과 Report 결과는 프로젝트를 다시 열어 복원할 수 있습니다.'],
  ['서비스를 먼저 살펴볼 수 있나요?', '아래 미리보기에서 기존 인터랙션을 체험할 수 있습니다. 실제 AI 실행은 로그인 후 프로젝트에서 진행합니다.'],
];

export const demoScenarios = [
  '반려동물 건강관리 구독 서비스',
  '소상공인 재고관리 SaaS',
  '맞춤형 교육 콘텐츠 플랫폼',
];

export const demoPhases = [
  ['uploading', '아이디어를 등록하고 있습니다', '프로젝트와 원문 정보를 안전하게 연결합니다.'],
  ['structuring', '핵심 항목을 구조화하고 있습니다', '문제, 고객, 가치 제안과 근거를 정리합니다.'],
  ['reviewing', '법률·사업 검토 항목을 만들고 있습니다', '위험 가능성, 가정과 추가 확인 질문을 구분합니다.'],
  ['personas', '검토할 Persona를 제안하고 있습니다', '서로 다른 역할과 상황의 합성 Persona를 구성합니다.'],
  ['integrating', '검토 결과를 통합하고 있습니다', '현재 상태와 다음 행동을 하나의 결과 화면으로 정리합니다.'],
];
