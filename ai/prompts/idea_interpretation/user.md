다음 아이디어 원문을 분석하세요.

<idea>
{{input}}
</idea>

반드시 다음 필드만 포함한 JSON을 반환하세요.
originalSourceSummary, normalizedDescription, facts, assumptions, constraints, openQuestions,
readiness(UNDER_SPECIFIED|APPROPRIATE|OVER_SPECIFIED), warnings, evidenceNeeds,
originDraft, fieldMetadata, clarificationQuestions

위 필드는 모두 반드시 포함하세요. 값이 없어도 필드를 생략하지 말고 허용된 nullable 필드는 null,
배열 필드는 []로 반환하세요. 아래에 정의하지 않은 extra field는 추가하지 마세요.

originDraft 형식:
productServiceDescription(string|null), problem(string[]),
target({customerTypes:string[],segment:string|null,situation:string|null,needs:string[]}|null),
solution(string[]), coreValue(string[]), primaryCategory(string|null), targetRegion(string|null),
fixedValues({field:string,value:string}[]), confirmedValues(object), assumptions(string[]),
pricingIntent(string|null), revenueModelIntent(string|null), salesChannelIntent(string|null),
knownUnitCost(string|null), alternatives(string[]), knownCompetitors(string[]),
differentiationIntent(string|null), internalConstraints(string[]).

fieldMetadata 항목 형식:
{key, sourceType(USER_CONFIRMED|AI_PROPOSED), requiredForStages(IDEA_ORIGIN|LEGAL_PRECHECK|CONCEPT_BUILD 배열),
status(MISSING|AI_PROPOSED|USER_CONFIRMED), locked(boolean), fallbackPolicy(NO_FALLBACK|AI_MAY_PROPOSE|BLOCK_STAGE)}.
status가 MISSING이면 sourceType은 AI_PROPOSED, locked는 false로 반환하세요.

clarificationQuestions 항목 형식:
{targetField, requirement(REQUIRED_FOR_IDEA_ORIGIN|REQUIRED_FOR_LEGAL_PRECHECK), question, reason}.
openQuestions에는 clarificationQuestions의 question 문자열만 같은 순서로 넣으세요.
