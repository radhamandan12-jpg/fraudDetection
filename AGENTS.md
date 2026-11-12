# AGENTS.md — fraud-capstone

## Build & test

- `mvn compile` / `mvn test` / `mvn spring-boot:run`
- Java 21, Maven, Spring Boot 3.5.14, Spring AI 1.1.8, LangChain4j 1.22.0
- Tests run in rules mode only — no API key needed. All 33 tests must pass.
- App runs on H2 in-memory by default (port 8090); `application-example.yml` documents overrides.

## Key invariants — do not break

- `FraudCaseProcessor` is the single contract for all three modes. Keep domain, rules, persistence and controllers free of Spring AI / LangChain4j imports.
- State transitions live in `FraudCaseService.transition()` against the `CaseStatus` allowed-transition table — never in model output.
- Every recommendation must pass `RecommendationValidator` and have `humanReviewRequired=true`.
- Tools are allow-listed in `EvidenceTools.ALLOWED_TOOLS`; every invocation is timed, bounded by `capstone.tools.timeout-ms`, traced (`ToolTraceRecorder`) and fails safely to a structured result.
- Tool methods carry BOTH `@Tool` annotations (Spring AI + LangChain4j) — keep them in sync when changing signatures.
- `capstone.processor-mode` switches adapters via `@ConditionalOnProperty`; `ProcessorRegistry` falls back to `rules` when a mode's beans are absent.
- Audit events and tool traces store hashes, never raw sensitive payloads.
- Policy chunks keep `documentId/version/section/chunkId` end-to-end; retrieval abstains below `capstone.retrieval.similarity-threshold`.
- Memory is keyed by caseId in both AI modes; never share context across cases.
- `EvidenceTools` needs `CaseToolContext.set(caseId)` around calls so traces bind to the right case (orchestrator and AI adapters both do this).

## Synthetic adapter markers

`SyntheticEvidenceAdapter` keys deterministic results on markers in refs:
`FRAUD`, `DUP`, `GENUINE`, `MISSING`, `BLOCK`, `REPEAT`, `TRUSTED`, `DISTANT`, `LOCAL`, `NEWDEV`.
Otherwise values derive from `capstone.tools.deterministic-seed` + input hash.
Update this list AND the docs when adding markers.
