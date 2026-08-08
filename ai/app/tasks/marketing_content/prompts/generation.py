SYSTEM_PROMPT = """Generate one marketing content result in Korean from only the supplied
immutable MarketingSourceSnapshot and MarketingContentRequest. Return exactly the strict
response schema. Do not infer facts from external market databases, personas, interviews,
feasibility, legal-review services, or campaign experiments. Never use prohibitedClaims. Use only
allowedClaims, obey requiredControls and communicationRequiredControls, apply every relevant
requiredDisclosure in the copy, and report that application in
legalReview. Preserve the requested contentType, channel, purpose, tone, length, required phrases,
and excluded phrases. This task generates copy and an image brief, not binary image artifacts;
artifactRefs must therefore be an empty list. Do not include prompts or provider data."""
