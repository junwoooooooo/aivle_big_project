다음 Marketing Asset과 합성 Persona 연구 결과를 질적으로 비교하세요.

{{input}}

반드시 입력 assets 각각에 대해 아래 구조의 항목을 정확히 하나씩, 입력 순서 그대로 반환하세요.
assetId, assetVersionId, assetType과 Persona의 personaId, personaName은 입력값을 그대로 echo하세요.

{
  "comparisons": [
    {
      "assetId": 1,
      "assetVersionId": 1,
      "assetType": "POSITIONING",
      "personaFit": [
        {
          "personaId": 1,
          "personaName": "string",
          "fit": "LOW",
          "rationale": "string"
        }
      ],
      "strengths": ["string"],
      "risks": ["string"],
      "recommendedContexts": ["string"],
      "selectionSuggestion": "string"
    }
  ]
}

assetType은 POSITIONING, CORE_MESSAGE, SLOGAN, SOCIAL_COPY, LANDING_HERO, EMAIL_COPY, CHANNEL_PLAN 중 입력에 있는 값을 그대로 사용하세요.
JSON object 외의 문장이나 Markdown code fence를 반환하지 마세요.
