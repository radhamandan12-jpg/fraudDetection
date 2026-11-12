# Intelligent Payment Fraud Investigation & Dispute Resolution Agent

Spring Boot capstone implementing the fraud/dispute triage assistant described
in the capstone specification. The assistant accelerates case intake, evidence
collection, policy-grounded triage and routing — while **humans always make the
final decision**.

## What it does (8-stage pipeline)

| Stage | Behaviour |
|---|---|
| 1 Intake | Validates mandatory fields; masks customer/payment identifiers (rejects unmasked PAN-like values and markup/scripts in statements) |
| 2 Plan | Classifies the investigation goal; builds a bounded, allow-listed evidence plan |
| 3 Act | Invokes allow-listed tools (`getTransactionDetails`, `getDeviceSignals`, `compareRecentLocations`, `getMerchantRiskProfile`, `getPriorDisputes`, `calculateCaseSla`, `retrievePolicyEvidence`, `saveAuditEvent`) — every call timed, bounded, traced and failing safely |
| 4 Retrieve | Searches approved policy chunks; keeps documentId/version/section/chunkId/score; abstains below threshold |
| 5 Assess | Compares user facts, tool evidence and policy; flags contradictions and gaps |
| 6 Recommend | Returns a typed classification, risk, route, confidence, evidence IDs, citations, missing info — always `humanReviewRequired=true` |
| 7 Review | Reviewer must record a disposition and reason before routing/closing |
| 8 Audit | Append-only audit of state changes, tool traces, citations and review actions |

Case states: `NEW → ENRICHING → READY_FOR_REVIEW | INCOMPLETE → UNDER_REVIEW → ROUTED → CLOSED`
(transitions enforced in `FraudCaseService`, never by model output).

## Processor modes (US-08)

Three adapters implement one contract, `FraudCaseProcessor.analyze(FraudCaseContext)`:

| Mode | Adapter | Notes |
|---|---|---|
| `rules` (default) | `RulesFraudCaseAgent` | Deterministic rules R-001…R-007/R-900 with rule IDs + exact input facts. Always available, no model. |
| `spring-ai` | `SpringAiFraudCaseAgent` | `ChatClient` + `@Tool`-registered `EvidenceTools` + `MessageChatMemoryAdvisor` keyed by caseId + `.entity()` typed output |
| `langchain4j` | `LangChain4jFraudCaseAgent` | `AiServices` interface (`@SystemMessage`, `@MemoryId`, `@UserMessage`), `@Tool` methods, `MessageWindowChatMemory` per case, `PolicyContentRetriever` retrieval augmentation |

Selection: `capstone.processor-mode: rules | spring-ai | langchain4j` (env `PROCESSOR_MODE`).
If a selected adapter is missing, the registry fails safely to `rules`.

## Where Spring AI vs LangChain4j apply (§6.1)

Both adapters sit **behind the same contract** and share the same:

- **Prompts** — `resources/prompts/fraud-system.txt` + `fraud-user.txt` (role, facts, guardrails, output contract)
- **Tools** — `EvidenceTools` methods carry *both* `@Tool` annotations (Spring AI `org.springframework.ai.tool.annotation.Tool` and LangChain4j `dev.langchain4j.agent.tool.Tool`), so one class serves both frameworks
- **Structured output** — one `FraudCaseRecommendation` schema; Spring AI uses `.entity()`, LangChain4j uses explicit JSON→DTO mapping; both pass through `RecommendationValidator`
- **Memory** — bounded, keyed by `caseId` in both (`MessageWindowChatMemory`); a fact from Case A can never appear in Case B (tested)
- **RAG** — framework-neutral `PolicyRetriever` (deterministic lexical scorer over ingested policy chunks) injected into prompts; LangChain4j additionally wraps it as a `ContentRetriever`/`RetrievalAugmentor`. Swap point for `VectorStore`/`EmbeddingStore` adapters
- **Rules mode stays independent and testable** — used for all unit/integration tests

## Run it

```bash
cd fraud-capstone
mvn spring-boot:run            # rules mode, MySQL (fraud_capstone), port 8090
PROCESSOR_MODE=spring-ai mvn spring-boot:run      # needs ANTHROPIC_API_KEY
PROCESSOR_MODE=langchain4j mvn spring-boot:run    # needs ANTHROPIC_API_KEY
```

Model config env vars (both AI modes): `ANTHROPIC_API_KEY` (or `CLAUDE_API_KEY`),
`ANTHROPIC_BASE_URL` (or `LLM_BASE_URL`), `ANTHROPIC_MODEL`. Note LangChain4j
expects the base URL *with* `/v1` — the default handles this; override with
`LC4J_BASE_URL` for a verbatim URL. Example for a gateway that exposes
Anthropic-compatible endpoints:

```bash
PROCESSOR_MODE=spring-ai ANTHROPIC_BASE_URL=$LLM_BASE_URL \
  ANTHROPIC_MODEL=global.anthropic.claude-sonnet-4-6 mvn spring-boot:run
```

UI: http://localhost:8090  ·  API: `/api/cases`

Database: MySQL `fraud_capstone` on localhost:3306 (user `fraudapp`, override with
`DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`). Tests use H2 in-memory via
`src/test/resources/application-test.yml` — no MySQL needed for `mvn test`.

Analyst/reviewer identity is taken from `X-Analyst-Id` / `X-Reviewer-Id` headers
(defaults `agent-anon`/`reviewer-anon`) — a real deployment must put
authentication in front.

### Sample scenario (§1.3)

```bash
curl -s -X POST localhost:8090/api/cases -H 'Content-Type: application/json' -d '{
 "reportedAt":"2026-11-10T10:15:00Z","channel":"WEB",
 "maskedCustomerRef":"CUST-8842","homeRegion":"EU-IE",
 "transactionRef":"TXN-FRAUD-0042","amount":129.99,"currency":"EUR",
 "occurredAt":"2026-11-10T09:40:00Z","transactionStatus":"POSTED",
 "transactionChannel":"CARD_NOT_PRESENT","maskedMerchant":"MRC-UNRECOGNISED-77",
 "issueType":"UNRECOGNISED_TRANSACTION",
 "customerStatement":"I did not make this purchase. I do not recognise the merchant and still have my card.",
 "cardPossession":"IN_POSSESSION","merchantRecognised":false,
 "syntheticDataConfirmed":true,"reviewRequired":true}'

curl -X POST localhost:8090/api/cases/CASE-2026-0001/investigations
curl localhost:8090/api/cases/CASE-2026-0001/evidence
curl localhost:8090/api/cases/CASE-2026-0001/recommendation
```

Scenario markers for reproducible demos (synthetic adapter):
`TXN-FRAUD-*` (new device, distant location, auth unavailable), `TXN-DUP-*`
(duplicate fingerprint), `TXN-GENUINE-*` (clean signals), `TXN-MISSING-*`
(not found), merchant containing `BLOCK` (blocklist), customer containing
`REPEAT` (prior disputes) / `TRUSTED` (clean device+location).

## Tests

```bash
mvn test
```

33 tests: state machine, intake validation, deterministic rules (all routes),
recommendation schema validation, retrieval abstention + citation metadata,
tool determinism, full API pipeline (intake → investigate → recommend →
follow-up → review), memory isolation between cases, safe-failure paths.

## Layout

```
src/main/java/com/training/fraudcase/
  api/          REST controller, DTOs, exception handler
  application/  FraudCaseService, InvestigationOrchestrator, planner, registry
  domain/       framework-neutral facts, views, enums, state machine
  agent/common/ FraudCaseProcessor, context, schema, validator
  agent/rules/  deterministic fallback (R-001…)
  agent/springai/   ChatClient adapter + memory/tool wiring
  agent/langchain4j/AI Service adapter + retriever/memory wiring
  tools/        EvidenceTools (8 allow-listed), synthetic adapter, trace
  knowledge/    policy ingestion, chunking, lexical retriever
  persistence/  JPA entities, repositories, audit writer
  ui/           Thymeleaf controller
src/main/resources/
  prompts/      system + user templates (shared contract)
  policies/     synthetic policy documents (POL-CNP-017, POL-DUP-009, POL-MRC-004, POL-SLA-002)
  templates/    Thymeleaf views
```

## Safety invariants

- Masked refs only; PAN-like digit runs rejected at intake.
- Model output is validated into a typed DTO before persistence/display;
  invalid output fails safely to `INCOMPLETE`/manual review.
- `humanReviewRequired` is enforced true on every recommendation.
- No financial actions or customer contact (`allow-financial-actions: false`,
  `allow-customer-contact: false`) — recommendations are advisory routes only.
- Tool traces store a request **hash**, never raw args; audit events store a
  payload hash — no sensitive payloads in logs/audit.
- When no policy passage meets the threshold the system flags the gap and
  routes to `MANUAL_REVIEW` rather than inventing procedure.
