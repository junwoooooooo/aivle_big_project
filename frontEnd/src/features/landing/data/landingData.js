export const navItems = [
  ['intro', '서비스 소개'],
  ['workflow', '파이프라인'],
  ['features', '주요 기능'],
  ['faq', 'FAQ'],
  ['demo', '미리보기'],
];

export const heroSlides = [
  { title: '아이디어를 정리하고 확정하세요', description: '텍스트나 파일을 저장하고 사실, 가정, 제약과 추가 질문을 구분합니다.', kind: 'upload' },
  { title: '콘셉트를 생성하고 비교하세요', description: '법률 검토가 포함된 후보를 생성하고 최종 콘셉트를 선택합니다.', kind: 'structure' },
  { title: 'Market Seed로 외부 분석을 준비하세요', description: '시장, BM, 기술·운영, 재무 모듈은 불변 입력 스냅샷을 기준으로 실행됩니다.', kind: 'review' },
  { title: '마케팅 콘텐츠를 제작하세요', description: '확정 입력과 분석 결과를 바탕으로 콘텐츠를 생성하고 결과를 저장합니다.', kind: 'summary' },
];

export const workflowSteps = [
  { number: '01', title: '아이디어를 정리하고 확정합니다.', description: '핵심 문제, 대상 사용자, 아이디어 개요를 확인하고 현재 Idea Brief를 확정합니다.', kind: 'project' },
  { number: '02', title: '콘셉트 후보를 생성합니다.', description: '법률 검토가 포함된 후보를 생성하고 교체가 필요한 슬롯을 다시 실행합니다.', kind: 'structure' },
  { number: '03', title: '콘셉트를 비교하고 선택합니다.', description: '후보별 가설을 결정하고 최종 선택에서 불변 Market Seed를 확정합니다.', kind: 'review' },
  { number: '04', title: '시장분석을 준비합니다.', description: 'Market Seed를 외부 시장분석 모듈의 불변 입력으로 전달합니다.', kind: 'summary' },
  { number: '05', title: 'BM 분석을 준비합니다.', description: 'Market Seed를 외부 BM 분석 모듈에 전달하고 실행 상태를 추적합니다.', kind: 'project' },
  { number: '06', title: '기술·운영 입력을 확정합니다.', description: '필수 사실과 결정을 보완해 기술·운영 입력 스냅샷을 만듭니다.', kind: 'structure' },
  { number: '07', title: '재무 입력을 확정합니다.', description: '검증된 기술·운영 입력을 바탕으로 재무 분석용 스냅샷을 만듭니다.', kind: 'review' },
  { number: '08', title: '마케팅 콘텐츠를 제작합니다.', description: '확정된 소스 스냅샷으로 콘텐츠를 생성하고 현재 결과를 저장합니다.', kind: 'summary' },
];

export const featureItems = [
  ['Idea Brief', '아이디어 개요, 문제, 대상 사용자를 중심으로 사실과 가정을 분리합니다.', 'wide'],
  ['콘셉트 생성·법률 검토', '법률 검토가 포함된 후보를 생성하고 적격 후보를 비교합니다.', 'wide'],
  ['Market Seed', '선택과 가설 결정을 외부 모듈용 불변 입력으로 확정합니다.', ''],
  ['BM·기술·운영 분석', '외부 분석의 연결 여부, 현재 입력, 실행 상태를 명확히 표시합니다.', ''],
  ['재무 분석', '필수 입력을 사용자가 보완한 뒤 독립 스냅샷으로 확정합니다.', ''],
  ['마케팅 콘텐츠', '현재 소스 스냅샷을 기준으로 콘텐츠를 생성하고 저장합니다.', ''],
];

export const faqItems = [
  ['어떤 입력을 사용할 수 있나요?', '아이디어를 직접 입력하거나 지원되는 DOCX/TXT 파일을 업로드할 수 있습니다.'],
  ['AI가 사업 성공 가능성을 확정하나요?', '아닙니다. 사실, 가정, 위험과 후속 검증 과제를 구조화하는 의사결정 지원 도구입니다.'],
  ['법률 검토 결과는 공식 자문인가요?', '아닙니다. AI 사전 검토이며 sourceVerified=false로 표시됩니다. 실제 적용 여부는 전문가나 관계 기관에 추가 확인해야 합니다.'],
  ['외부 분석 결과가 원본을 바꾸나요?', '아닙니다. 외부 분석은 불변 스냅샷을 입력으로 사용하며 결과가 원본을 자동 변경하지 않습니다.'],
  ['결과는 새로고침 후에도 남나요?', '저장된 Idea Brief, Concept, 입력 스냅샷과 Marketing 콘텐츠는 프로젝트를 다시 열어 복원할 수 있습니다.'],
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
