# V23-B2 사용자 검증

## V23-C 제한 smoke에서 확인할 항목

- FULL fresh 또는 recollect 한 번에서 section provider attempt가 10회를 넘지 않는지 확인한다.
- 기존 저장 본문에 실제 존재하는 passage만 evidence quote로 나오며, 재서술·이어붙인 quote가 거부되는지 확인한다.
- 숫자가 없는 정성적 규격·인증·유통 조건은 quote로 유지되되 임의 숫자가 생기지 않는지 확인한다.
- promoted evidence가 section당 4건, 총 28건을 넘지 않고 raw document body가 응답에 포함되지 않는지 확인한다.
- summary가 promoted evidence를 인용하되 평가·처방·근거 없는 수치를 만들지 않는지 확인한다.
- BM이 같은 Business Validation의 exact FULL Version에서 온 CHANNEL evidence ID만 사용하고 section extraction을 다시 실행하지 않는지 확인한다.
- timeout, 429, malformed JSON 상황에서도 기존 FULL 수집 결과가 유지되고 degradation이 남는지 확인한다.

VISUAL: NOT APPLICABLE — no frontend change.

PROVIDER QUALITY: REVIEW PENDING.
