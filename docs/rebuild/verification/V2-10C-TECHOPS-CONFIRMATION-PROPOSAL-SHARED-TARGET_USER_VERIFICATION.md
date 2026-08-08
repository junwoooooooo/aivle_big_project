# V2-10C 사용자 검증

Backend TechOps/Finance 표적 테스트, AI `test_tech_ops_proposal.py`와 type alignment, Frontend TechOps model/hook 테스트 및 targeted ESLint를 실행한다.

브라우저에서 TechOps 진입 직후 제품 사양이 수정 가능하고 확인 전 missing인지, 세 제안이 모두 non-null인지, 다른 제안 요청 뒤 proposalVersion이 2이고 값이 다른지 확인한다. canonical 3개년 목표를 확정하고 Snapshot을 만든 뒤 Finance에서 같은 구조·수치가 read-only로 보이며 재입력을 요구하지 않는지 확인한다.

실패 시 TechOps preparation의 requiredFacts/proposalDecisions, TechOps Snapshot requiredFacts, Finance preparation의 threeYearTargets provenance를 수집한다.
