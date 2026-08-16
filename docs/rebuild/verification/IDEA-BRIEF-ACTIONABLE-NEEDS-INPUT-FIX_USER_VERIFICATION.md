# IDEA-BRIEF-ACTIONABLE-NEEDS-INPUT-FIX User Verification

1. 새 프로젝트 생성
2. Idea 입력
3. Follow-up 답변
4. 마지막 AI 분석 완료 확인
5-A. 필수 정보가 충분하면 바로 Review가 나오는지 확인
5-B. 필수 정보가 부족하면 빈 질문 화면이 아니라 누락 필드 직접 입력 화면이 나오는지 확인
6. 누락 정보 입력
7. `FINAL_SYNTHESIS` 재실행 확인
8. Review 확인
9. Idea Confirm
10. Concept Factory 진입

절대로 `questions=0` 상태에서 `POST /idea-brief/answers {"answers":[]}`가 발생하면 안 된다.
