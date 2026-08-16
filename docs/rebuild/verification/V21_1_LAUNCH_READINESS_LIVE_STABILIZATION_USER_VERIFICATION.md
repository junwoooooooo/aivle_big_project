# V21.1 Launch Readiness 사용자 검증

자동 기능 검증은 완료되었다. 이 문서는 실제 인증 프로젝트에서의 시각·다운로드 검증 절차다.

## 1. Finance DOCX 왕복

1. 출시 준비의 재무 영역에서 새 템플릿을 내려받는다.
2. 문서에서 `[필수]`와 `[선택]`, 작성 안내, 섹션, 예시가 구분되는지 확인한다.
3. 금액에 `12,000,000원`, 이탈률에 `3.5%`, 3개년 목표에 `1,000 / 2,000 / 4,000`을 입력한다.
4. 선택 항목 하나는 비우고 다른 하나는 `해당 없음`으로 작성한다.
5. DOCX를 업로드한다.

기대 결과:

- HTTP 422 없이 재무 분석이 시작된다.
- Market/BM 결과가 없는 프로젝트에서도 시작된다.
- 원본 사용자 문서가 Finance authority로 유지된다.

## 2. 필드 오류

1. 3개년 목표에 `1억원 / two / 3`을 입력한다.
2. 필수 항목 하나를 비운다.
3. 업로드한다.

기대 결과:

- 상단에 `재무 입력 문서를 확인해 주세요.`가 표시된다.
- 아래에 `3개년 성장 목표`, 비운 필수 항목의 한국어 label과 각각의 이유가 표시된다.
- generic `요청을 완료하지 못했습니다`만 표시되지 않는다.
- 긴 원문이나 민감한 문서 내용이 오류 화면에 그대로 나오지 않는다.

## 3. PDF 미리보기

1. 완료된 Technology 보고서에서 `PDF 미리보기`를 누른다.
2. Operations, Finance, 통합 보고서에서도 반복한다.

기대 결과:

- 클릭 직후 Dialog가 열리고 `보고서를 준비하고 있습니다.`가 보인다.
- fetch가 끝나기 전까지 화면이 무반응으로 남지 않는다.
- PDF가 브라우저 기본 viewer나 iframe이 아니라 Dialog 안 canvas 페이지로 표시된다.
- 미리보기 클릭만으로 다운로드가 시작되지 않는다.
- 준비 중에는 PDF 미리보기 버튼만 비활성화되며 다른 모듈 작업은 사용할 수 있다.

## 4. 취소와 오류

1. 느린 네트워크에서 PDF 준비 중 Dialog를 닫는다.
2. 요청이 완료될 때까지 기다린다.

기대 결과:

- 닫힌 Dialog가 늦은 응답 때문에 다시 열리지 않는다.
- 잘못된 MIME, 너무 작은 응답, `%PDF-`가 아닌 응답은 미리보기나 다운로드로 전달되지 않는다.
- 생성 실패 시 `PDF 보고서를 만들지 못했습니다.`가 표시된다.

## 5. 명시적 다운로드

1. 정상 미리보기에서 `PDF 다운로드`를 누른다.
2. 저장한 파일을 Chrome/Edge와 PDF reader에서 연다.

기대 결과:

- 사용자가 버튼을 누른 경우에만 저장이 시작된다.
- 파일이 `%PDF-`로 시작하며 정상적으로 열린다.
- Technology, Operations, Finance, Technology+Operations, Technology+Finance, 3종 통합 모두 정상이다.

## 6. Finance command replay

1. 같은 DOCX import 요청을 동일 Idempotency-Key로 재전송한다.
2. 같은 key로 다른 DOCX도 재전송한다.

기대 결과:

- 같은 문서는 기존 TaskRun/preparation/snapshot을 반환한다.
- 새 artifact/preparation/snapshot이 생기지 않는다.
- 다른 문서는 `IDEMPOTENCY_CONFLICT`가 되며 새 입력 상태가 생기지 않는다.

## 7. 독립 실행

다음 세 프로젝트를 각각 Project만 생성한 상태에서 확인한다.

- Technology DOCX 업로드 및 시작
- Operations DOCX 업로드 및 시작
- Finance DOCX 업로드 및 시작

세 작업 모두 Market/BM/기존 TechOps 결과를 요구하지 않아야 한다.

## 8. 사용자 시각 검토

다음을 Chrome과 Edge에서 확인한다.

- 한글 글꼴과 줄바꿈
- 페이지 간 간격과 canvas 선명도
- 긴 보고서 스크롤
- 390px 모바일 Dialog
- PDF.js 렌더 실패 fallback 문구와 다운로드 버튼
- 다운로드 파일의 실제 열기

## 9. 별도 HOLD

현재 호스트에는 Docker CLI가 없다. 따라서 V27을 수정하지 않은 상태로 다음 검증은 별도 환경에서 수행한다.

1. empty PostgreSQL에 V1부터 V27까지 적용
2. 기존 Full V26 schema 복제본을 V27로 upgrade
3. 기존 데이터와 current/stale/lineage 보존 확인

