# V21 출시 준비 사용자 검증 가이드

## 검증 상태

- 자동 기능 검증: V21 집중 범위 PASS
- 실제 인증 브라우저 시각 검토: **USER REVIEW PENDING**
- PostgreSQL migration·Docker image 검증: **HOLD — Docker CLI 필요**

## 준비

1. V21 frontend/backend/AI와 V27 migration이 적용된 검증 환경을 준비한다.
2. 새 프로젝트와 기존 TechOps/Finance 이력이 있는 프로젝트를 각각 준비한다.
3. 기술·운영·재무 템플릿을 내려받아 실제 값이 있는 DOCX를 만든다.
4. Desktop 1440×900, 1920×1080, Mobile 390×844에서 확인한다.

## 1. 단일 출시 준비 제품

프로젝트 개요에서 `출시 준비`를 연다.

기대 결과:

- 한 화면에 `기술 분석`, `운영 분석`, `재무 분석`, `보고서 다운로드`가 순서대로 있다.
- 예전 TechOps 독립 workflow나 과거 Finance 대형 입력 workspace가 나타나지 않는다.
- 상단 바로가기로 네 section을 이동할 수 있다.
- `TaskRun`, `Snapshot`, hash, canonical 같은 내부 용어가 일반 화면에 보이지 않는다.

## 2. 호환 route

각 주소로 직접 진입한다.

| 주소 | 기대 결과 |
| --- | --- |
| `/launch-readiness` | 출시 준비 상단 |
| `/technology` | 동일 화면의 기술 section |
| `/operations` | 동일 화면의 운영 section |
| `/tech-ops` | 동일 출시 준비 화면 |
| `/finance` | 동일 화면의 재무 section |

어떤 주소도 `TechOpsPage`나 과거 `FinancePage`를 열면 안 된다.

## 3. 기술 분석

1. `입력 템플릿 다운로드`를 누른다.
2. 기술 계획을 작성하고 DOCX를 업로드한다.
3. 분석이 완료될 때까지 화면과 Work Center를 확인한다.

기대 결과:

- 버튼은 즉시 처리 상태를 표시한다.
- 가짜 percentage 없이 입력 확인·분석·검토·정리 macro 단계가 보인다.
- `작업센터에서 상세 기록 보기`가 같은 job의 Work Center 상세를 연다.
- 완료 후 점수·한국어 판단·요약·우선 과제가 보인다.
- 새 기술 문서를 올리면 이전 결과가 최신 입력 결과처럼 남지 않는다.
- PDF 미리보기와 다운로드가 동작한다.

## 4. 운영 분석

기술과 같은 순서로 운영 템플릿을 작성·업로드한다.

기대 결과:

- 운영 프로세스·인력·파트너·고객 지원·품질·장애 대응·KPI·파일럿·확장·운영 위험을 입력할 수 있다.
- 기존 TechOps나 BM 단계를 먼저 완료하라고 요구하지 않는다.
- 기술과 운영 결과가 서로 덮어쓰지 않는다.
- 완료 결과와 PDF가 운영 문서 기준이다.

## 5. 재무 USER DOCUMENT authority

Market 분석과 사업 모델 결과가 없는 새 프로젝트에서 수행한다.

1. 재무 템플릿을 다운로드한다.
2. 필수 비용·성장·고객·가격·변동비 값을 작성한다.
3. DOCX를 업로드한다.

기대 결과:

- Market/사업 모델 결과 없이 업로드와 분석을 시작할 수 있다.
- 문서가 완전히 검증된 뒤 한 번에 반영된다.
- 잘못된 숫자, 음수, 100 초과 이탈률, 중복/미지원 fieldKey, 필수값 누락은 이해 가능한 오류로 나온다.
- 오류가 나도 이전 current preparation은 부분 변경되지 않는다.
- 기존 Market/BM 값이 있더라도 사용자 DOCX 값을 몰래 덮지 않는다.
- 완료 화면에 매출·영업이익·운전자금과 해석이 보인다.

## 6. 멱등 재시도와 실패

- 같은 기술/운영 업로드 요청이 같은 명령 키로 재전송되면 기존 작업이 반환되고 새 artifact/snapshot이 생기지 않아야 한다.
- 새 분석은 새 명령 키를 사용해야 한다.
- provider 오류는 raw traceback/provider 문장이 아니라 안전한 사용자 오류로 보인다.
- 일시 오류는 Full의 bounded retry를 사용하고 무한 재시도하지 않는다.

## 7. Work Center와 진행

- 기술·운영 TaskRun이 Quick/Full Work Center에 표시되는지 확인한다.
- 본문 상세 버튼이 새 JobCenter를 중복 렌더하지 않고 기존 단일 Work Center sheet를 여는지 확인한다.
- 상세에서는 전체 JobEvent·실패 정보·retry를 볼 수 있고 본문은 macro 진행만 보여야 한다.

## 8. 보고서 선택

| 선택 | 기대 결과 |
| --- | --- |
| 0개 | 미리보기 버튼 disabled |
| 기술 1개 | 기술 개별 PDF |
| 운영 1개 | 운영 개별 PDF |
| 재무 1개 | 재무 개별 PDF |
| 2개 | 통합 표지 + 선택한 두 보고서 + 출처 |
| 3개 | 통합 표지 + 세 보고서 + 출처 |

완료되지 않았거나 stale인 보고서는 선택 가능 목록에 나타나면 안 된다.

## 9. PDF 미리보기

- modal이 `document.body` portal 계층에서 열리고 배경 scroll이 잠긴다.
- Escape와 닫기 동작, focus trap, 닫은 뒤 focus 복귀를 확인한다.
- iframe 안에서 한글이 깨지지 않고 표가 페이지 폭을 넘지 않는다.
- 기술/운영 PDF에는 입력 근거·영역별 준비도·위험·Gate·과제·출처가 있다.
- 재무 PDF에는 3개년 손익·월별 현금흐름·stress·Monte Carlo·해석이 있다.
- 통합 PDF의 중복 URL은 한 번만 나오고 클릭 가능하며 검은 링크 테두리가 보이지 않는다.

## 10. Journey와 Final Report 회귀

- Project Journey에는 `출시 준비` 하나만 있다.
- 기술·운영·재무 중 실패/입력 필요/진행 상태가 우선순위에 맞게 하나의 상태로 집계된다.
- 기술 상태가 재무 상태에 덮어써지거나 반대 현상이 없어야 한다.
- 기존 프로젝트 Final Report는 legacy TechOps 결과로 계속 만들 수 있다.
- 새 프로젝트는 기술+운영 current 결과와 Finance 결과를 source로 사용할 수 있다.

## 11. Migration·Docker 운영 검증

Docker가 있는 CI/검증 PC에서 다음을 수행한다.

1. empty PostgreSQL에 V1부터 V27까지 적용한다.
2. 실제 기존 Full schema V26 복제본을 V27로 upgrade한다.
3. 기존 데이터와 FK/index가 보존되는지 확인한다.
4. backend image를 build하고 Noto CJK가 설치되는지 확인한다.
5. backend/AI health check 후 container에서 기술·재무·통합 PDF를 생성한다.
6. PostgreSQL 데이터를 tmpfs로 초기화해 upgrade 검증을 우회하지 않는다.

## 사용자 승인 질문

- 이 화면이 Mini에서 설계한 하나의 `출시 준비 분석 센터`처럼 느껴지는가?
- 기술·운영·재무의 입력과 결과 흐름을 처음 보아도 이해할 수 있는가?
- 이전 TechOps/Finance 화면이 되살아난 느낌이 없는가?
- 재무가 이전 사업 단계 없이도 시작된다는 점이 명확한가?
- 실행 상태와 Work Center의 역할 구분이 자연스러운가?
- PDF 미리보기와 개별/통합 다운로드 선택이 명확한가?

위 시각·상호작용 항목은 사용자 승인 전까지 `USER REVIEW PENDING`으로 유지한다.
