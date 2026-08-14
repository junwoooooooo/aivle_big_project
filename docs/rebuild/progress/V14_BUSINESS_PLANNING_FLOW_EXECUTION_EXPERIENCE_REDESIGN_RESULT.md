# V14 사업 기획 흐름·실행 경험 재설계 결과

## 상태

**PARTIAL / LIVE VISUAL HOLD**

기능 구현, 표적 테스트, 변경 파일 lint, 프런트엔드 production build, Idea canonical 백엔드 통합 테스트는 통과했다. 다만 인증 프로젝트 화면을 여는 로컬 백엔드가 Flyway V1 migration 실패로 기동되지 않아 필수 실브라우저 화면 행렬은 완료하지 못했다. 따라서 V14를 COMPLETE로 선언하지 않는다.

## START SHA

| 항목 | 결과 |
|---|---|
| branch | `full` |
| START HEAD | `b455b697aff0bb08e81b8c4c75f30da8f438fda5` |
| local `origin/full` | `b455b697aff0bb08e81b8c4c75f30da8f438fda5` |
| 예상 SHA 일치 | PASS |
| `git fetch origin full` | 네트워크 제한으로 실패. 로컬 remote ref를 시작 기준으로 사용 |
| 시작 worktree | clean |

V13 RESULT와 USER VERIFICATION을 읽었으며, V13의 인증 Project 화면 미검증 상태를 이번 작업의 선행 제약으로 유지했다.

## CREATE / SETTINGS FORM RESTORE

| 화면 | 이전 | V14 |
|---|---|---|
| 새 프로젝트 Sheet | 전역 `.project-form-layout`이 적용된 horizontal 업무 폼 | V10 표현과 동일하게 순수 `.project-form` vertical 흐름 복구 |
| 프로젝트 설정 | `.project-sheet__form` | 그대로 유지하고 `data-form-kind="admin"`으로 계층 명시 |
| 프로젝트 업무 폼 | `ProjectFormRow`, `project-form-layout` | 변경 없음 |

원인은 V11에서 새 프로젝트 Sheet에 추가된 `.project-form-layout` 클래스였다. 전역 CSS를 완화하지 않고 해당 관리 화면에서 업무 폼 class만 제거했다.

## SAFETY LANGUAGE MATRIX

| 내부 decision | 사용자 제목/상태 |
|---|---|
| 공통 | `아이디어 진행 가능 여부` |
| `ALLOW` | `✓ 다음 단계로 진행할 수 있습니다.` |
| `ALLOW_WITH_RESTRICTIONS` | `△ 일부 내용을 조정하면 진행할 수 있습니다.` |
| `BLOCK_OR_REFRAME` | `현재 형태로는 다음 단계로 진행하기 어렵습니다.` |
| 실행 중 | `입력 내용을 확인하고 아이디어를 정리하고 있습니다.` 계열 |

SafetyReview의 내부 decision, category, restriction 계약은 변경하지 않았다. 법률·규제 검토로 오인시키는 표현도 사용하지 않았다.

## IDEA FIELD LANGUAGE MATRIX

| 이전 | V14 |
|---|---|
| 사용 맥락 | AI가 파악한 사용 상황 |
| 업종 분류 | AI가 분류한 사업 분야 |
| 컨셉 탐색 범위 | AI가 정리한 사업안 탐색 범위 |
| 한 줄 아이디어 정의 | AI가 정리한 한 줄 정의 |
| 지역 해석 | AI가 이해한 대상 지역 |
| 경쟁자 맥락 | AI가 파악한 경쟁 상황 |

사업 기획의 현재 라우팅 화면에서는 `컨셉` 사용자 문구를 `사업안`으로 교체했다. 내부 class/API/TaskType/domain 명칭은 유지했다. 라우팅에서 제외된 legacy concept-factory/selection 소스와 후속 Marketing/Twin 모듈의 용어는 범위 밖으로 보존했다.

## AI EDIT DATA FLOW

```text
IdeaBriefReview textarea 수정
→ updateInterpretation reducer
→ confirmBrief
→ PATCH /idea-brief/interpretation
→ IdeaBrief.interpretationJson + userEdited=true
→ confirm snapshot
→ 기존 Concept/사업안 실행 입력
```

안내 문구는 “수정한 내용은 이후 사업안 생성에 반영되며 처음 입력한 내용은 별도로 보존된다”는 실제 계약을 직접 설명한다.

## USER INPUT PROVENANCE CONTRACT

백엔드 통합 테스트를 추가해 다음을 함께 검증했다.

- `interpretedProblem` 수정값이 `interpretationJson`에 저장됨
- `userEdited=true` 저장
- 원래 `problem` 필드 값 유지
- 원래 provenance `USER_INPUT` 유지
- 수정된 interpretation이 CONFIRMED snapshot에 유지

## IDEA REVIEW BEFORE / AFTER

| 항목 | 이전 | V14 |
|---|---|---|
| Desktop 구성 | Summary/Safety/User/AI가 긴 1열 | `내가 입력한 내용` 40% / `AI가 정리한 내용` 60% |
| 진행 가능 여부 | 큰 `안전 확인 완료` Card | 상단 compact status strip |
| AI 출처 | 필드마다 badge 반복 | Section 수준 `AI 정리` |
| 결정 후보 | 확인/수정 후 확인/결정하지 않음 | 맞아요/수정/아직 정하지 않음 |
| Mobile | 긴 1열 | 진행 가능 여부 → 사용자 입력 → AI 정리 → 결정사항 → action |

## CONFIRM FLOW BEFORE / AFTER

| 상태 | 이전 | V14 |
|---|---|---|
| REVIEW | `IdeaBriefReview` | 같은 `IdeaBriefReview` |
| CONFIRMING | 별도 표현 없음 | 동일 workspace에서 loading/disabled action |
| CONFIRMED | 별도 `ConfirmedIdeaSummary` 전체 교체 | 동일 review workspace를 read-only로 전환 |
| 완료 안내 | 별도 summary page | compact 완료 strip |
| 세부 재확인 | 전체 필드 즉시 반복 | `확정 내용 보기` disclosure |
| 다음 행동 | `다음 단계 · 사업안 검토` | `사업안 생성 및 검토로 이동 →` |

reload 후 CONFIRMED도 동일 component를 사용한다. downstream 결과가 있으면 수정 경고를 유지하되 쉬운 문장으로 교체했다.

## BUSINESS PROPOSAL STATE MATRIX

| 상태 | 제목 | 본문/행동 | tab |
|---|---|---|---|
| PRE_GENERATION | 사업안 생성 및 검토 | 생성 → 법률·규제 검토 → 비교·선택 preview, `사업안 생성 및 법률 검토 시작` | 없음 |
| GENERATING | 사업안 생성 및 검토 | Execution Experience | 없음 |
| NEEDS_INPUT | 사업안 생성 및 검토 | 현재 phase + 추가 정보 필요 | 없음 |
| READY, 1개 | 사업안 검토 | 사업안 목록 | 목록만 |
| READY, 2개 이상 | 사업안 검토 | 목록과 비교 | 비교 tab 표시 |
| SELECTED/FOLLOWUP | 기존 선택·가설·법률 후속 흐름 | 기존 runtime 유지 | 결과 기준 |

## EXECUTION PHASE MAPPING

### Idea

| phase | 사용자 label | 대표 event |
|---|---|---|
| INPUT_REVIEW | 입력 내용 확인 | queued, started |
| ELIGIBILITY | 진행 가능 여부 확인 | `SAFETY_REVIEW` / `job.idea.extracting` |
| INTERPRETATION | 아이디어 정리 | `IDEA_INTERPRETATION` / questions preparing |
| PREPARE_REVIEW | 확인할 내용 준비 | `INTERPRETATION_COMMIT`, brief preparing, completed |

### 사업안

| phase | 사용자 label | 대표 event |
|---|---|---|
| DIRECTION | 사업 방향 구성 | conditions, directions, queued/running |
| GENERATE | 사업안 생성 | draft, proposal, generate |
| LEGAL | 법률·규제 검토 | legal 계열 |
| DISTINCTNESS | 차별성 확인 | duplicate, excluded, distinct |
| READY | 비교 준비 | materializing, completed, summary |

unknown event는 raw stage를 노출하지 않고 `결과를 준비하고 있습니다.`로 표시한다.

## EXECUTION UX MATRIX

| 항목 | Project Execution Experience | Work Center |
|---|---|---|
| 현재 phase | 표시 | event에서 추론 가능 |
| 완료/현재/예정 rail | 표시 | 미표시 |
| 경과·최근 업데이트 | 실제 값이 있을 때 표시 | 상세 기록 유지 |
| raw event list | 기본 노출 제거 | 전체 이력 유지 |
| 실패·입력 필요 | 사용자 행동 중심 요약 | 정확한 실패 상세/retry 유지 |
| 상세 진입 | `전체 처리 기록 보기` | 기존 Bottom Sheet 유지 |

가짜 percentage는 추가하지 않았다.

## MOTION MATRIX

| 요소 | 기본 | reduced motion |
|---|---|---|
| current phase | 2초 subtle ring pulse | animation 제거 |
| 완료 station | brand fill + check | 동일 정보 유지 |
| button press | 기존 V13 token | 기존 reduced-motion 계약 유지 |

## TEST MATRIX

| 검증 | 결과 |
|---|---|
| V14 표적 frontend 8 files / 42 tests | PASS |
| Idea/사업안 phase mapper unknown, FAILED, NEEDS_INPUT | PASS |
| 같은 workspace confirmed route/action | PASS |
| pre-run tab 금지, ready 2개 compare 조건 | PASS |
| Project create admin form class | PASS |
| changed-file ESLint | PASS |
| frontend production build | PASS, 기존 chunk-size warning만 존재 |
| backend `IdeaBriefCanonicalizationIntegrationTests` | PASS |
| `git diff --check` | PASS |
| frontend full suite | 473 PASS / 26 FAIL. V14 범위 밖의 기존 영어/접근성 기대값 실패 |

## LIVE VISUAL MATRIX

| 화면 | 1440×900 | 1920×1080 | 390×844 | 결과 |
|---|---:|---:|---:|---|
| 새 프로젝트 Sheet | 미검증 | 미검증 | 미검증 | HOLD |
| 프로젝트 설정 | 미검증 | 미검증 | 미검증 | HOLD |
| Idea Review | 미검증 | 미검증 | 미검증 | HOLD |
| Idea Confirmed | 미검증 | 미검증 | 미검증 | HOLD |
| Idea Running | 미검증 | 미검증 | 미검증 | HOLD |
| 사업안 Pre-run | 미검증 | 미검증 | 미검증 | HOLD |
| 사업안 Running | 미검증 | 미검증 | 미검증 | HOLD |
| 사업안 Ready | 미검증 | 미검증 | 미검증 | HOLD |

브라우저에서 V14 프런트엔드 landing/login 화면은 정상 로드했다. 그러나 로컬 백엔드가 아래 이유로 기동되지 않아 인증 Project route에 진입하지 못했다.

1. 기존 H2: Flyway `V1 new pipeline baseline` failed migration history
2. 별도 V14 H2: 동일 V1 migration 적용 실패
3. 실제 로그인 시 `요청을 완료하지 못했습니다` 확인

## REMAINING GAP

1. Flyway local H2 baseline 문제를 별도 인프라 작업으로 복구한 뒤 인증 Project 화면을 열어야 한다.
2. 1440×900, 1920×1080, 390×844에서 필수 8개 화면의 bounding/overflow/3초 이해 가능성을 확인해야 한다.
3. 프로젝트 설정 Sheet는 코드상 compact vertical 계약을 보존했지만 실제 브라우저 시각 검증이 남았다.
4. full frontend suite의 기존 26개 실패는 V14 변경과 분리해 baseline 정비가 필요하다.

위 gap이 남아 있으므로 V14는 **COMPLETE가 아니다**.
