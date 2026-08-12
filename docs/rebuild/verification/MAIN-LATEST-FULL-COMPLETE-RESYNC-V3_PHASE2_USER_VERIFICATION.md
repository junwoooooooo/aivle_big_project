# PHASE 2 사용자 검증

1. 한 프로젝트에서 Concept를 확정하고 Market FULL을 실행해 result lineage가 동일 Concept인지 확인한다.
2. Concept를 바꾼 뒤 과거 Market FULL로 BM/TechOps를 실행하면 fail-closed인지 확인한다.
3. 경쟁사 seed를 8개까지 저장하고 순서가 유지되는지, 중복/9번째/타 프로젝트 접근이 차단되는지 확인한다.
4. TechOps를 확정하지 않아도 Finance가 current Market FULL + BM exact lineage만으로 준비되는지 확인한다.
5. TaskRun 재시도와 SSE 재연결 후 current report가 중복 생성되지 않는지 확인한다.
