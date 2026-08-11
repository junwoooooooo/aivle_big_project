# Twin Bank 외부 자산 계약

## 목적과 소유 경계

Twin Bank는 코드 저장소와 분리하여 전달·보관하는 재배포 제한 자산이다. Git 저장소와 Docker 이미지에 포함하지 않으며, 운영자가 관리하는 host 경로를 AI container에 읽기 전용으로 mount한다.

필수 파일은 정확히 다음 두 개다.

- `twin_cards_generic.jsonl`
- `twin_frame.csv`

저장소에는 production bank, sample card, 가짜 대체 bank를 추가하지 않는다. `.gitignore`와 `.dockerignore`의 기존 차단 규칙을 유지한다.

## Runtime 계약

- Host 기본 예시: `../aivle_private_assets/twin-bank`
- 설정: `TWIN_BANK_HOST_DIR`
- Container mount: `/app/app/twin/bank` (read-only)
- AI 환경변수: `TWIN_BANK_DIR=/app/app/twin/bank`
- 두 파일 중 하나라도 없거나 유효한 frame이 비어 있으면 `TWIN_BANK_UNAVAILABLE`로 실패한다.

현재 코드 주석의 참고 규모는 약 8,604개 card/frame과 약 10.8 MB다. 이는 현재 자산을 설명하는 참고값이며 migration, schema, 고정 cardinality 계약이 아니다.

## 전달·보관 원칙

자산 소유자는 승인된 별도 저장 위치를 통해 두 파일을 전달한다. 개발자별 local clone, Git LFS, Docker build context, CI artifact에 복제하지 않는다. Container에는 실행 시점에 읽기 전용 bind mount하며, AI process는 내용을 수정하거나 결과 authority로 사용하지 않는다.

## 내용 비노출 검증

다음 명령은 파일 이름·개수·크기만 확인한다. 카드 본문, `pid_hash`, CSV row 내용은 출력하지 않는다.

PowerShell:

```powershell
$bank = Resolve-Path $env:TWIN_BANK_HOST_DIR
Get-ChildItem -LiteralPath $bank -File |
  Where-Object Name -In @('twin_cards_generic.jsonl', 'twin_frame.csv') |
  Select-Object Name, Length
(Get-ChildItem -LiteralPath $bank -File |
  Where-Object Name -In @('twin_cards_generic.jsonl', 'twin_frame.csv')).Count
```

Container mount 확인:

```powershell
docker compose exec ai-server python -c "import os; p=os.environ['TWIN_BANK_DIR']; print([(n, os.path.getsize(os.path.join(p,n))) for n in ('twin_cards_generic.jsonl','twin_frame.csv')])"
```
