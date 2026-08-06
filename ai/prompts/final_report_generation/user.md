다음 프로젝트 근거를 Section 기반 최종 보고서로 구성하세요.

{{input}}

selectedMarketingAssets와 marketingComparison을 반드시 근거로 사용하세요.
없는 사실을 추가하지 말고, 불확실성은 assumptions, researchNeeds, risks로 분리하세요.
아래 필드 이름과 JSON 타입을 정확히 사용해 JSON object 하나만 반환하세요.

{
  "executiveSummary": "string",
  "idea": {},
  "legalReview": {},
  "selectedConcept": {},
  "analysis": {},
  "personaInsights": {},
  "marketingStrategy": {},
  "facts": ["string"],
  "assumptions": ["string"],
  "researchNeeds": ["string"],
  "risks": ["string"],
  "decision": "CONDITIONAL_GO",
  "decisionReasons": ["string"],
  "nextActions": ["string"]
}

facts, assumptions, researchNeeds, risks는 항목이 없으면 빈 배열로 반환할 수 있습니다.
decisionReasons와 nextActions는 비워 두거나 누락하지 마세요.
JSON 밖의 설명 문장과 Markdown code fence를 반환하지 마세요.
