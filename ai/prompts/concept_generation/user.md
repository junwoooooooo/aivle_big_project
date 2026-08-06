Create exactly one Concept candidate for this slot:

{{input}}

Before returning JSON, verify:
1. The response has exactly one top-level `concept` field and no `concepts` array.
2. Every required ConceptCandidate field is complete.
3. `operatingModel` and the other object fields are JSON objects.
4. `originTrace` contains every required key exactly once with unchanged `sourceValue` and a non-missing `conceptValue`.
5. Target and locked pricing/revenue/channel trace values equal their candidate fields.
6. `variationFocus` does not override locked values, required origin structure, or legal guardrails.
7. The response contains no extra fields, Markdown, or prose.
