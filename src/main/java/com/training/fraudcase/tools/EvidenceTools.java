package com.training.fraudcase.tools;

import com.training.fraudcase.config.CapstoneProperties;
import com.training.fraudcase.domain.enums.ActorType;
import com.training.fraudcase.domain.enums.ToolOutcome;
import com.training.fraudcase.knowledge.PolicyRetriever;
import com.training.fraudcase.persistence.AuditEventWriter;
import com.training.fraudcase.tools.dto.AuditEventResult;
import com.training.fraudcase.tools.dto.CaseSla;
import com.training.fraudcase.tools.dto.DeviceSignals;
import com.training.fraudcase.tools.dto.LocationComparison;
import com.training.fraudcase.tools.dto.MerchantRiskProfile;
import com.training.fraudcase.tools.dto.PolicyEvidenceResult;
import com.training.fraudcase.tools.dto.PriorDisputes;
import com.training.fraudcase.tools.dto.TransactionDetails;
import dev.langchain4j.agent.tool.P;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Approved tool catalogue (read-only, deterministic). Methods are invoked
 * directly by the investigation orchestrator in every mode, and are also
 * registered with the model when processor-mode is spring-ai or
 * langchain4j. Every call is timed, bounded by capstone.tools.timeout-ms,
 * traced against the current case and fails safely to a structured
 * UNAVAILABLE result rather than leaking provider details.
 */
@Component
public class EvidenceTools {

    private static final Logger log = LoggerFactory.getLogger(EvidenceTools.class);

    public static final List<String> ALLOWED_TOOLS = List.of(
            "getTransactionDetails", "getDeviceSignals", "compareRecentLocations",
            "getMerchantRiskProfile", "getPriorDisputes", "calculateCaseSla",
            "retrievePolicyEvidence", "saveAuditEvent");

    private final SyntheticEvidenceAdapter adapter;
    private final PolicyRetriever policyRetriever;
    private final AuditEventWriter auditWriter;
    private final ToolTraceRecorder traceRecorder;
    private final long timeoutMs;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "evidence-tools");
        t.setDaemon(true);
        return t;
    });

    public EvidenceTools(SyntheticEvidenceAdapter adapter, PolicyRetriever policyRetriever,
                         AuditEventWriter auditWriter, ToolTraceRecorder traceRecorder,
                         CapstoneProperties props) {
        this.adapter = adapter;
        this.policyRetriever = policyRetriever;
        this.auditWriter = auditWriter;
        this.traceRecorder = traceRecorder;
        this.timeoutMs = props.getTools().getTimeoutMs();
    }

    @org.springframework.ai.tool.annotation.Tool(name = "getTransactionDetails",
            description = "Retrieve transaction state, channel and authentication fields for a transaction reference")
    @dev.langchain4j.agent.tool.Tool("Retrieve transaction state, channel and authentication fields for a transaction reference")
    public ToolResult<TransactionDetails> getTransactionDetails(
            @ToolParam(description = "Transaction reference from the case intake") @P("Transaction reference from the case intake") String transactionRef,
            @ToolParam(description = "Claimed amount, optional") @P("Claimed amount, optional") BigDecimal expectedAmount,
            @ToolParam(description = "ISO currency code, optional") @P("ISO currency code, optional") String currency) {
        return invoke("getTransactionDetails", "ref=" + transactionRef,
                () -> adapter.transactionDetails(transactionRef, expectedAmount, currency),
                d -> d.found() ? "found status=" + d.status() + " auth=" + d.authOutcome()
                        + " dupFingerprint=" + d.duplicateFingerprintMatch() : "not found");
    }

    @org.springframework.ai.tool.annotation.Tool(name = "getDeviceSignals",
            description = "Check device novelty and trust indicators for a masked customer reference")
    @dev.langchain4j.agent.tool.Tool("Check device novelty and trust indicators for a masked customer reference")
    public ToolResult<DeviceSignals> getDeviceSignals(
            @ToolParam(description = "Masked customer reference") @P("Masked customer reference") String maskedCustomerRef,
            @ToolParam(description = "Related transaction reference for context") @P("Related transaction reference for context") String transactionRef) {
        return invoke("getDeviceSignals", "cust=" + maskedCustomerRef,
                () -> adapter.deviceSignals(maskedCustomerRef, transactionRef),
                d -> "deviceKnown=" + d.deviceKnown() + " trust=" + d.trustLevel());
    }

    @org.springframework.ai.tool.annotation.Tool(name = "compareRecentLocations",
            description = "Compare current and recent trusted locations for a masked customer")
    @dev.langchain4j.agent.tool.Tool("Compare current and recent trusted locations for a masked customer")
    public ToolResult<LocationComparison> compareRecentLocations(
            @ToolParam(description = "Masked customer reference") @P("Masked customer reference") String maskedCustomerRef,
            @ToolParam(description = "Customer home region from intake") @P("Customer home region from intake") String homeRegion,
            @ToolParam(description = "Related transaction reference for context") @P("Related transaction reference for context") String transactionRef) {
        return invoke("compareRecentLocations", "cust=" + maskedCustomerRef,
                () -> adapter.recentLocations(maskedCustomerRef, homeRegion, transactionRef),
                d -> "distanceKm=" + d.distanceKm() + " anomaly=" + d.anomalyLevel());
    }

    @org.springframework.ai.tool.annotation.Tool(name = "getMerchantRiskProfile",
            description = "Return risk band, blocklist status and category for a masked merchant")
    @dev.langchain4j.agent.tool.Tool("Return risk band, blocklist status and category for a masked merchant")
    public ToolResult<MerchantRiskProfile> getMerchantRiskProfile(
            @ToolParam(description = "Masked merchant text from intake") @P("Masked merchant text from intake") String maskedMerchant) {
        return invoke("getMerchantRiskProfile", "merchant=" + maskedMerchant,
                () -> adapter.merchantRisk(maskedMerchant),
                d -> "riskBand=" + d.riskBand() + " blocklist=" + d.blocklistMatch());
    }

    @org.springframework.ai.tool.annotation.Tool(name = "getPriorDisputes",
            description = "Summarise earlier dispute claims for a masked customer")
    @dev.langchain4j.agent.tool.Tool("Summarise earlier dispute claims for a masked customer")
    public ToolResult<PriorDisputes> getPriorDisputes(
            @ToolParam(description = "Masked customer reference") @P("Masked customer reference") String maskedCustomerRef) {
        return invoke("getPriorDisputes", "cust=" + maskedCustomerRef,
                () -> adapter.priorDisputes(maskedCustomerRef),
                d -> "count=" + d.count() + " similar=" + d.similarCount()
                        + " lookbackDays=" + d.lookbackDays());
    }

    @org.springframework.ai.tool.annotation.Tool(name = "calculateCaseSla",
            description = "Calculate a training SLA target for a proposed route and risk level")
    @dev.langchain4j.agent.tool.Tool("Calculate a training SLA target for a proposed route and risk level")
    public ToolResult<CaseSla> calculateCaseSla(
            @ToolParam(description = "Proposed route, e.g. FRAUD_INVESTIGATION") @P("Proposed route, e.g. FRAUD_INVESTIGATION") String route,
            @ToolParam(description = "Assessed risk level, e.g. HIGH") @P("Assessed risk level, e.g. HIGH") String riskLevel) {
        return invoke("calculateCaseSla", "route=" + route + ";risk=" + riskLevel,
                () -> adapter.caseSla(route, riskLevel),
                d -> "queue=" + d.queue() + " priority=" + d.priority() + " targetAt=" + d.targetAt());
    }

    @org.springframework.ai.tool.annotation.Tool(name = "retrievePolicyEvidence",
            description = "Search approved policy chunks and return passages with citation metadata and scores")
    @dev.langchain4j.agent.tool.Tool("Search approved policy chunks and return passages with citation metadata and scores")
    public ToolResult<PolicyEvidenceResult> retrievePolicyEvidence(
            @ToolParam(description = "Natural-language retrieval query") @P("Natural-language retrieval query") String query) {
        return invoke("retrievePolicyEvidence", "query=" + query,
                () -> {
                    var passages = policyRetriever.retrieve(query);
                    return new PolicyEvidenceResult(query, passages.size(), passages);
                },
                r -> "matches=" + r.passages().size());
    }

    @org.springframework.ai.tool.annotation.Tool(name = "saveAuditEvent",
            description = "Append an important audit event for the current case")
    @dev.langchain4j.agent.tool.Tool("Append an important audit event for the current case")
    public ToolResult<AuditEventResult> saveAuditEvent(
            @ToolParam(description = "Event type, e.g. NOTE") @P("Event type, e.g. NOTE") String eventType,
            @ToolParam(description = "Short non-sensitive summary") @P("Short non-sensitive summary") String summary) {
        return invoke("saveAuditEvent", "type=" + eventType,
                () -> {
                    String caseId = CaseToolContext.currentCaseId();
                    String eventId = auditWriter.append(caseId == null ? "UNBOUND" : caseId,
                            eventType, ActorType.SYSTEM, summary);
                    return new AuditEventResult(eventId, eventType, Instant.now());
                },
                r -> "eventId=" + r.eventId());
    }

    private <T> ToolResult<T> invoke(String toolName, String argsDesc,
                                     Supplier<T> work, Function<T, String> summarizer) {
        String traceId = "TR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String caseId = CaseToolContext.currentCaseId();
        long start = System.nanoTime();
        ToolResult<T> result;
        try {
            T data = CompletableFuture.supplyAsync(work, executor)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            result = new ToolResult<>(traceId, toolName, ToolOutcome.OK, data,
                    summarizer.apply(data), Instant.now(), "SYNTHETIC_ADAPTER");
        } catch (java.util.concurrent.TimeoutException e) {
            result = new ToolResult<>(traceId, toolName, ToolOutcome.UNAVAILABLE, null,
                    "Timed out after " + timeoutMs + "ms", Instant.now(), "SYNTHETIC_ADAPTER");
        } catch (Exception e) {
            Throwable cause = e instanceof java.util.concurrent.ExecutionException && e.getCause() != null
                    ? e.getCause() : e;
            log.warn("tool {} failed: {}", toolName, cause.toString());
            result = new ToolResult<>(traceId, toolName, ToolOutcome.ERROR, null,
                    "Tool failed safely: " + cause.getClass().getSimpleName(), Instant.now(), "SYNTHETIC_ADAPTER");
        }
        traceRecorder.record(traceId, caseId, toolName, argsDesc,
                result.outcome(), (System.nanoTime() - start) / 1_000_000, result.summary());
        return result;
    }
}
