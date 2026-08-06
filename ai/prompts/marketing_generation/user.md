다음 프로젝트 근거를 사용해 Marketing Workspace 초안을 생성하세요.

{{input}}

아래 JSON 계약과 정확히 같은 필드 이름과 타입으로 JSON object 하나만 반환하세요.
positioning, coreMessage, landingHero의 각 문자열은 비워 두지 마세요.
slogans, personaMessages, channelPlan, socialCopies는 각각 한 개 이상의 항목을 포함하세요.
assumptions와 warnings는 항목이 없더라도 빈 배열로 포함하세요.
emailCopies는 선택 필드이며, 생성하는 경우 문자열 배열로만 반환하세요.

{
  "positioning": "string",
  "coreMessage": "string",
  "slogans": ["string"],
  "personaMessages": [
    {
      "personaId": 1,
      "personaName": "string",
      "message": "string",
      "rationale": "string"
    }
  ],
  "channelPlan": [
    {
      "channel": "string",
      "objective": "string",
      "message": "string"
    }
  ],
  "socialCopies": ["string"],
  "landingHero": {
    "headline": "string",
    "subheadline": "string",
    "cta": "string"
  },
  "assumptions": ["string"],
  "warnings": ["string"]
}

JSON 밖의 설명 문장과 Markdown code fence를 반환하지 마세요.
