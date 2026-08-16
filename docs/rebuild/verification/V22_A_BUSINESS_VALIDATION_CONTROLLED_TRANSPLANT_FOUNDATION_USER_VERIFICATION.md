# V22-A Business Validation 사용자 검증

## 준비

- 사업안 선택, 가설, 최종 법률 검토, Market Seed가 완료된 실제 프로젝트를 사용한다.
- Work Center를 열어 Market FULL과 BM이 별도 Job으로 남는지 함께 확인한다.

## 1. 단일 시작

1. `/app/projects/{projectId}/business-validation`을 연다.
2. 경쟁 정보와 저장한 운영 정보가 보이는지 확인한다.
3. `사업 검증 시작`을 한 번 누른다.

기대: Market 분석이 먼저 진행되고 BM은 대기로 보인다. raw JobEvent는 본문에 나오지 않는다.

## 2. 서버 continuation

Market 실행 중 페이지를 닫았다가 다시 연다.

기대: Market 성공 후 사용자가 별도 버튼을 누르지 않아도 BM이 이어지고, Work Center에는 Market/BM 별도 TaskRun이 남는다.

## 3. 실패 경계

- Market 실패: BM이 시작되지 않고 `사업 검증 다시 실행`이 보인다.
- Market 성공/BM 실패: Market 전체 결과가 남고 `BM 다시 시도`가 보인다.
- BM 재시도: Work Center에서 Market 재실행 없이 BM Job만 새로 생긴다.

## 4. 완료와 stale

- 완료 시 한 route에서 `시장 분석 결과`와 `비즈니스 모델 결과`를 연속 확인한다.
- 선택 사업안/가설/Market Seed를 변경하면 기존 결과가 사라지지 않고 재검증 필요 안내가 보인다.

## 5. 호환 route

- `/market`
- `/business-model`

두 URL이 `/business-validation`으로 이동하며 옛 독립 실행 화면을 다시 열지 않아야 한다.

## 사용자 검토 상태

- 실제 AI 품질, 장시간 continuation, 긴 결과 화면 밀도, desktop/mobile layout: **USER REVIEW PENDING**.
- PostgreSQL empty/upgrade migration: **ENVIRONMENT VERIFICATION PENDING**.
