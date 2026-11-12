# Capstone Self-Evaluation Report

- Participant: **Radhika Mandan**
- Evaluation date: 2026-11-12 (post-remediation re-evaluation; prior evaluation scored 79/100)

## 1. Executive Summary
- Project: `com.training:fraud-capstone` 1.0.0 — Intelligent Payment Fraud Investigation and Dispute Resolution Agent (decision support only; humans always decide)
- Primary framework: Spring AI 1.1.8 (`spring-ai-starter-model-anthropic`)
- Secondary framework: LangChain4j 1.22.0 (`langchain4j`, `langchain4j-anthropic`); deterministic rules adapter is the third `FraudCaseProcessor`
- Build status: `mvn clean test` — **BUILD SUCCESS** (no Maven wrapper; `mvn` used)
- Test status: **61 tests run, 0 failures, 0 errors, 0 skipped** (H2 in-memory, `test` profile)
- Overall score: **91/100**
- Percentage: **91%**
- Readiness: **Demo Ready**
- Scoring model used: **both AI frameworks implemented → 10 points each, total 100**; framework parity evaluated in §5.3

## 2. Repository and Technology Overview
| Area | Finding | Evidence |
|---|---|---|
| Business objective | Payment-fraud/dispute triage: intake → tool-based investigation → grounded recommendation → mandatory human review → routing | `README.md`, `InvestigationOrchestrator` |
| Java / Spring Boot | Java 21, Spring Boot 3.5.14 | `pom.xml` |
| Build & key deps | Maven; Spring MVC, Thymeleaf, Spring Data JPA, MySQL connector, Spring AI Anthropic starter, LangChain4j anthropic, JUnit 5 | `pom.xml` |
| AI frameworks | **Both** Spring AI and LangChain4j, plus deterministic rules adapter | `SpringAiFraudCaseAgent`, `LangChain4jFraudCaseAgent`, `RulesFraudCaseAgent` |
| Model provider | Anthropic-compatible endpoint; key/base-url/model all externalised via env vars | `application.yml` (`ANTHROPIC_API_KEY`/`CLAUDE_API_KEY`, `LLM_BASE_URL`, `ANTHROPIC_MODEL`) |
| Database | MySQL `fraud_capstone` runtime; H2 for tests; `ddl-auto: update`; all text columns explicit `TEXT`, all enums explicit `varchar(32)` | `application.yml`, entity `columnDefinition`s |
| Vector store | None — deterministic in-memory lexical retriever (token-frequency cosine, top-k=4, threshold=0.35, abstains below) | `LexicalPolicyRetriever` |
| UI | Thymeleaf + custom dark-theme CSS: dashboard, intake, case detail, evidence, traces, citations, chat, review | `templates/`, `static/css/app.css` |
| MCP / external integration | None | `pom.xml` (no MCP deps) |
| Entry point | `com.training.fraudcase.FraudCapstoneApplication` | `FraudCapstoneApplication.java` |
| Framework-neutral contract | `FraudCaseProcessor` (mode/analyze/answer); `PolicyRetriever`; `ToolResult<T>`; `FraudCaseContext`; `FraudCaseRecommendation` schema | `agent/common/` |
| Mode selection | `capstone.processor-mode` = `rules` / `spring-ai` / `langchain4j` via `@ConditionalOnProperty`; `ProcessorRegistry` falls back to rules | `ProcessorRegistry`, `application.yml` |

## 3. Build and Test Results
| Command | Result | Details |
|---|---|---|
| `mvn clean test` | **BUILD SUCCESS** | 61 tests, 0 failures/errors/skipped — up from 33 pre-remediation |
| `mvn -q compile` | Success | No warnings of note |
| Coverage tooling | Not configured | No JaCoCo/other plugin in `pom.xml` |
| Live run — rules | Pass | Port 8090; full pipeline verified on MySQL |
| Live run — spring-ai | Pass | Port 8091; real model (`global.anthropic.claude-sonnet-4-6`) via `LLM_BASE_URL` gateway |
| Live run — langchain4j | Pass | Port 8092; same gateway/model, ~11 s end-to-end |

## 4. Evaluation Scorecard
| # | Metric | Maximum | Score | Status | Evaluator Comments | Evidence |
|---|---|---:|---:|---|---|---|
| 1 | BFS fraud & dispute use case | 10 | 10 | Implemented | Full lifecycle with all 6 classifications and 4 routes; dedicated synthetic sanctions/PEP screening + R-008 escalation; fraud/dispute/payment-ops queues separated; human ownership enforced; no loan-underwriting concepts. | `Classification`, `Route`, `CaseStatus`, `RulesFraudCaseAgent` |
| 2 | Architecture & maintainability | 12 | 12 | Implemented | Clean layered packages; single `FraudCaseProcessor` contract; adapters isolated from business services (no framework imports in domain/persistence); config-driven selection with fail-safe; no rule duplication; DB-derived case IDs + intake dedup. | package layout, `ProcessorRegistry`, `FraudCaseService.nextCaseId` |
| 3 | Fraud workflow & deterministic tools | 15 | 14 | Implemented | 11 allow-listed tools covering the rubric list: transaction details, device, geolocation, merchant risk, prior disputes, velocity, amount deviation, sanctions/PEP, SLA, policy retrieval, audit. Timed, bounded, hashed, traced, safe-failing; deterministic seeded adapter + 13 scenario markers; orchestrator runs bounded plan; referential integrity prevents fabricated IDs. Deducted: no dedicated account/customer-profile lookup tool. | `EvidenceTools.ALLOWED_TOOLS`, `SyntheticEvidenceAdapter`, `InvestigationOrchestrator` |
| 4 | Spring AI implementation | 10 | 9 | Implemented | `ChatClient`+system/user prompt resources, `.tools()`, `.entity()` typed output, case-keyed `MessageWindowChatMemory`, externalised config, fail-safe verified live. Adapter contract tests with stub `ChatModel`. Missing: `VectorStore` (lexical retriever injected via prompt vars instead). | `SpringAiFraudCaseAgent`, `SpringAiConfig`, `SpringAiAdapterContractTest`, live CASE-2026-0013 |
| 5 | LangChain4j implementation | 10 | 9 | Implemented | `AiServices` (`@SystemMessage`/`@V`/`@MemoryId`), `@Tool` methods, per-case `MessageWindowChatMemory` + `InMemoryChatMemoryStore`, `PolicyContentRetriever` as `ContentRetriever`, JSON→DTO + `stripFences` + validator, tool error handlers. Contract tests with stub service. Missing: `EmbeddingStore`. | `LangChain4jConfig`, `FraudInvestigationService`, `LangChain4jAdapterContractTest`, live CASE-2026-0017 |
| 6 | RAG, embeddings, retrieval, citations | 15 | 11 | Partially Implemented | 5 policy docs incl. retired fixture; front matter with documentId/title/version/effectiveDate/owner/**status**/**expiryDate**; section-aware chunking (15 chunks); top-k + threshold + abstention; RETIRED/expired docs excluded at ingestion; citations carry full metadata end-to-end; explicit no-answer path; retired + no-answer + top-k tests. Missing: embedding generation, `VectorStore`/`EmbeddingStore`, paraphrased/conflicting-source tests. | `PolicyIngestionService`, `LexicalPolicyRetriever`, `policies/*.md`, `POL-LEG-001` skip at boot |
| 7 | Structured output, memory, human review | 8 | 8 | Implemented | Typed `FraudCaseRecommendation`; schema + referential-integrity validation pre-persistence; forced `humanReviewRequired=true`; bounded case-keyed memory both modes; 5 dispositions with mandatory reason/`selectedRoute`; routes only — no autonomous action. | `RecommendationValidator`, `recordReview`, live reviews |
| 8 | Security, privacy, responsible AI, audit | 8 | 7 | Implemented | No secrets in repo; `.env*` git-ignored; synthetic-only enforced at intake; PAN-like + markup/script rejected; hashed tool args + audit payloads; append-only audit with actor/mode; h2-console disabled; structured 404/409/500 error contract; `allow-financial-actions`/`allow-customer-contact` false. Remaining: trusted `X-Analyst-Id`/`X-Reviewer-Id` headers (documented training posture), instruction-injection defence is markup-pattern only. | `application.yml`, `.gitignore`, `validateIntake`, `ApiExceptionHandler`, `AuditEventWriter` |
| 9 | Testing, reliability, observability | 8 | 7 | Implemented | 61 tests: adapter contract tests both frameworks (stub models), 14 validator tests, retrieval variants, dedup + ID-generation, new markers, full API pipeline, memory isolation, review guards. Latency + outcome traces per tool call; per-stage audit events. Remaining: no MySQL-dialect IT (H2 only — the class of defect that produced the original criticals), no metrics/JaCoCo, no retry-idempotency harness. | `src/test/` (8 test classes), `ToolTraceRecorder` |
| 10 | UI, documentation, developer experience | 4 | 4 | Implemented | Polished UI with all screens and states; README covers prerequisites, modes, curl samples, all 13 scenario markers, safety invariants; AGENTS.md invariants updated; PRESENTATION.md demo script. | `templates/`, `README.md`, `AGENTS.md` |
| | **Total** | **100** | **91** | | | |

## 5. Framework Evaluation
### 5.1 Spring AI — Implemented (9/10)
`SpringAiFraudCaseAgent` builds `chatClient.prompt().system(systemPrompt).user(template).tools(evidenceTools).advisors(CONVERSATION_ID=caseId).call().entity(FraudCaseRecommendation.class)` then runs `RecommendationValidator.validateAndEnforce` with full referential-integrity checks. **Verified live** (`CASE-2026-0013`, ~12 s): `UNAUTHORISED_TRANSACTION`/`HIGH` → `FRAUD_INVESTIGATION`, conf 0.91; **all 8 cited `toolTraceIds` are a strict subset of persisted `TR-…` traces**; 3 real citations; `ruleIds=[]` per updated prompt contract. A separate run where the model invented a rule ID was rejected → safe `INCOMPLETE`. Contract test with stub `ChatModel` covers valid/fenced/fabricated/malformed outputs. Missing: `VectorStore`.

### 5.2 LangChain4j — Implemented (9/10)
`AiServices`-backed `FraudInvestigationService` with `AnthropicChatModel`, allow-listed tools, per-case `MessageWindowChatMemory`, `PolicyContentRetriever` as `ContentRetriever`, tool argument/execution error handlers, `stripFences` + Jackson mapping + same validator. **Verified live** (`CASE-2026-0017`, ~11 s): `UNAUTHORISED_TRANSACTION`/`HIGH` → `FRAUD_INVESTIGATION`, conf 0.91; **8/8 trace IDs ⊆ persisted traces**; 3 real `POL-CNP-017` citations; grounded 3 843-char follow-up answer. Contract test with stub service covers the same matrix. Missing: `EmbeddingStore`.

### 5.3 Framework Parity — equivalent business outcome confirmed
Same synthetic fraud intake through both adapters produced the same classification (`UNAUTHORISED_TRANSACTION`), risk (`HIGH`), route (`FRAUD_INVESTIGATION`), forced human review, real evidence/trace references and real `POL-CNP-017` citations; both fail safely to `INCOMPLETE` on model/validation errors. Latency: ~11–12 s each. Confidence identical (0.91). No wording identity required — contract parity holds.

## 6. Acceptance Scenario Results
| Scenario | Mode | Expected | Actual | Result | Evidence |
|---|---|---|---|---|---|
| Known device, familiar merchant, normal amount | rules | Genuine/low-risk, human approval | `GENUINE_TRANSACTION` → `MANUAL_REVIEW` (R-006, conf 0.75) | PASS | `CASE-2026-0014` |
| New device, unusual location, >5x amount | rules | Suspicious/unauthorised + cited evidence | `UNAUTHORISED_TRANSACTION` → `FRAUD_INVESTIGATION` (R-004); amount-deviation evidence 10.00x HIGH | PASS | `CASE-2026-0016` |
| ≥5 transactions in 10 minutes | rules | Velocity indicator recorded | `VELOCITY`/`HIGH` evidence: 5 txn/10min recorded | PASS | `CASE-2026-0015` |
| Confirmed synthetic screening match | rules | Mandatory escalation, not autonomous | SCREENING/HIGH → R-008 forced `FRAUD_INVESTIGATION`, still human-gated | PASS | `CASE-2026-0011` |
| Evidence service unavailable | rules | `INSUFFICIENT_EVIDENCE`/more info | `INSUFFICIENT_EVIDENCE` → `MANUAL_REVIEW`/INCOMPLETE (R-002) | PASS | `RulesFraudCaseAgentTest`, `missingTransactionFailsToIncomplete` |
| Same case submitted twice | rules | Idempotent/duplicate protection | Identical intake returned existing `CASE-2026-0006` + dedup audit event | PASS | live posts; `duplicateIntakeReturnsOpenCase` |
| Policy question with active source | spring-ai / lc4j / rules | Grounded answer with doc/section/version | Grounded answers citing `POL-CNP-017 v3.2` chunks + evidence IDs | PASS | live follow-ups on `CASE-2026-0013`/`0017` |
| Question absent from corpus | rules | Explicit no-information response | Abstains below 0.35 threshold; flags gap instead of inventing | PASS | `abstainsBelowThreshold`; live abstention log |
| Retired policy contains matching text | rules | Retired policy excluded | `POL-LEG-001` skipped at ingestion (boot log), unreachable in retrieval | PASS | `retiredPolicyNeverRetrieved`; boot log |
| Model returns malformed output | spring-ai / lc4j | Validated retry or safe failure | Malformed JSON, fabricated evidence/trace IDs, invented rule IDs → `InvalidRecommendationException` → `INCOMPLETE` | PASS | contract tests; live `CASE-2026-0012` INCOMPLETE |
| Two separate case IDs | all | Memory isolated | Case B cannot see case A's conversation (API test); memory keyed by caseId | PASS | `caseConversationIsIsolated` |
| Prompt injection in narrative | rules | Treated as data; constraints retained | Markup/script rejected at intake (400); statement stored as evidence data only | PASS | `rejectsMarkupInStatement` |

## 7. Strengths
1. **Single contract, three interchangeable brains** — `FraudCaseProcessor` implemented by rules/Spring AI/LangChain4j with config-driven selection and safe fallback; parity verified live against a real model with identical business outcomes.
2. **Guardrails that hold under adversarial output** — schema validation plus *referential-integrity* validation: the model can no longer persist invented evidence, trace, citation or rule IDs (verified live: fabricated IDs → safe `INCOMPLETE`; valid run cited 8/8 real traces).
3. **Complete deterministic evidence chain** — 11 timed/bounded/traced allow-listed tools covering the full rubric list (incl. velocity, amount-deviation, sanctions/PEP), hashed requests, provenance-tagged evidence, end-to-end citation metadata, mandatory human review.

## 8. Gaps and Risks
| Priority | Gap or Risk | Impact | Evidence | Recommended Fix |
|---|---|---|---|---|
| Medium | No embeddings/`VectorStore`/`EmbeddingStore` — lexical cosine retrieval only | Retrieval quality capped at token overlap; misses paraphrases | `LexicalPolicyRetriever` | Swap-in adapter behind `PolicyRetriever` (Spring AI VectorStore or LC4J EmbeddingStore) |
| Medium | H2-only tests can't see MySQL dialect issues | The original criticals (tinytext, enum) escaped the suite; class of defect remains blind | `src/test/resources/application-test.yml` | Add Testcontainers MySQL integration test for schema + persistence paths |
| Medium | Trusted `X-Analyst-Id`/`X-Reviewer-Id` identity headers | No authentication/authorisation; acceptable only for training | `FraudCaseController` | Spring Security filter or SSO header validation behind a profile |
| Low | Instruction-injection defence limited to markup/PAN patterns | Embedded "ignore your rules" text in narrative passes through to model context | `FraudCaseService.MARKUP` | Add content-boundary framing/instruction-isolation in prompts; expand filters |
| Low | No metrics/coverage tooling (JaCoCo), no retry-idempotency harness | Observability limited to audit events + traces | `pom.xml` | Add Micrometer metrics + JaCoCo; idempotency tests on create/investigate |
| Low | No MCP server integration (rubric optional) | External tool federation not demonstrated | `pom.xml` | Optional: expose tools via MCP server adapter |

## 9. Security and Responsible AI Findings
| Severity | Finding | File or Component | Remediation |
|---|---|---|---|
| Info | No secrets committed; key/base-url externalised via env vars; `.env*` git-ignored | `application.yml`, `.gitignore` | None — verified `git check-ignore` |
| Info | Synthetic-only data enforced (`syntheticDataConfirmed`/`reviewRequired` AssertTrue); PAN-like + markup rejected | `CreateCaseRequest`, `validateIntake` | None |
| Info | Tool traces + audit store hashes, never raw payloads; model never sees unmasked data | `ToolTraceRecorder`, `AuditEventWriter` | None |
| Info | `allow-financial-actions=false`, `allow-customer-contact=false`; recommendation is route-only; `humanReviewRequired` forced | `CapstoneProperties`, `RecommendationValidator` | None |
| Low | Unauthenticated identity headers — any caller can claim any analyst/reviewer ID | `FraudCaseController` header params | Introduce authentication filter (documented as training posture) |
| Low | Instruction-injection into narrative/policy text not specifically defended beyond markup pattern | `MARKUP` pattern, prompts | Add delimiters/instruction-isolation; monitor for override phrases |

## 10. Recommended Improvements
### Critical
- None remaining.
### High
- None remaining.
### Medium
- Embedding-based retrieval behind `PolicyRetriever` (Spring AI `VectorStore` / LC4J `EmbeddingStore`) + paraphrased/conflicting-source tests.
- Testcontainers MySQL integration test covering schema generation and the long-text/enum persistence paths.
- Authentication for analyst/reviewer identity (replace trusted headers).
### Low
- Micrometer metrics + JaCoCo; idempotency/duplicate-action tests.
- Account/customer-profile lookup tool to complete the rubric tool list.
- Optional MCP server exposure of the evidence tools.

## 11. Definition-of-Done Checklist
- [x] Fraud case intake works with synthetic data
- [x] Deterministic tools provide traceable evidence
- [x] Structured recommendations are validated
- [x] Policy answers are grounded and cited
- [x] Human review is mandatory
- [x] Conversation/case memory is isolated
- [x] Secrets are not stored in the repository
- [x] Error and no-information states are handled safely
- [x] Automated tests pass
- [x] README enables another participant to run the project

## 12. Final Verdict
**Demo Ready.** Every critical and high finding from the prior 79/100 evaluation is remediated and re-verified on the production-configured MySQL path: case IDs can no longer collide or silently overwrite (verified `CASE-2026-0010` = max+1 after restart; duplicate intake returns the open case); all text/enum columns are correctly typed (long statements, 1 520-char messages, long review reasons persist); and the recommendation validator now enforces referential integrity — verified live as both AI adapters cited only real trace IDs while a fabricated rule ID failed safely to `INCOMPLETE`. The missing fraud tools (velocity, amount-deviation, sanctions/PEP) and the policy lifecycle (status/expiry, retired exclusion) are implemented, evidenced, and tested — 61 tests pass. The remaining gaps are architectural-depth items (embeddings/vector store, MySQL-dialect IT, authentication, metrics) that cap the score at 91 but do not block a demonstration.
