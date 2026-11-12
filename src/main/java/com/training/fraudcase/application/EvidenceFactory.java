package com.training.fraudcase.application;

import com.training.fraudcase.domain.CaseFacts;
import com.training.fraudcase.domain.EvidenceItemView;
import com.training.fraudcase.domain.enums.AnomalyLevel;
import com.training.fraudcase.domain.enums.AuthOutcome;
import com.training.fraudcase.domain.enums.EvidenceType;
import com.training.fraudcase.domain.enums.Provenance;
import com.training.fraudcase.domain.enums.RiskBand;
import com.training.fraudcase.domain.enums.Severity;
import com.training.fraudcase.domain.enums.ToolOutcome;
import com.training.fraudcase.domain.enums.TrustLevel;
import com.training.fraudcase.tools.ToolResult;
import com.training.fraudcase.tools.dto.AmountDeviation;
import com.training.fraudcase.tools.dto.CaseSla;
import com.training.fraudcase.tools.dto.DeviceSignals;
import com.training.fraudcase.tools.dto.LocationComparison;
import com.training.fraudcase.tools.dto.MerchantRiskProfile;
import com.training.fraudcase.tools.dto.PriorDisputes;
import com.training.fraudcase.tools.dto.ScreeningResult;
import com.training.fraudcase.tools.dto.TransactionDetails;
import com.training.fraudcase.tools.dto.TransactionVelocity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Converts structured tool results and validated intake into material
 * EvidenceItems with severity and provenance — one record per fact.
 */
@Component
public class EvidenceFactory {

    public List<EvidenceItemView> fromResults(List<ToolResult<?>> results, CaseFacts facts) {
        List<EvidenceItemView> items = new ArrayList<>();
        items.add(intake(facts));
        for (ToolResult<?> r : results) {
            if (r.data() instanceof TransactionDetails d) {
                transaction(items, d);
            } else if (r.data() instanceof DeviceSignals d) {
                device(items, d);
            } else if (r.data() instanceof LocationComparison d) {
                location(items, d);
            } else if (r.data() instanceof MerchantRiskProfile d) {
                merchant(items, d);
            } else if (r.data() instanceof PriorDisputes d) {
                history(items, d);
            } else if (r.data() instanceof TransactionVelocity d) {
                items.add(item(EvidenceType.VELOCITY, anomalySeverity(d.anomalyLevel()),
                        "Velocity: " + d.countLast10Min() + " txn/10min, " + d.countLast60Min()
                                + " txn/60min vs baseline " + d.baselineDailyCount() + "/day",
                        r.toolName()));
            } else if (r.data() instanceof AmountDeviation d) {
                items.add(item(EvidenceType.TRANSACTION, anomalySeverity(d.anomalyLevel()),
                        "Amount deviation " + d.ratioVsBaseline() + "x baseline median "
                                + d.baselineMedian(), r.toolName()));
            } else if (r.data() instanceof ScreeningResult d) {
                items.add(item(EvidenceType.SCREENING, d.matchFound() ? Severity.HIGH : Severity.INFO,
                        d.matchFound()
                                ? "Screening match on " + d.listName() + " (" + d.matchCategory() + ")"
                                : "No screening match (" + d.listName() + ")", r.toolName()));
            } else if (r.data() instanceof CaseSla d) {
                items.add(item(EvidenceType.SLA, Severity.INFO,
                        "SLA " + d.priority() + " for queue " + d.queue() + " target " + d.targetAt(),
                        r.toolName()));
            } else if (r.outcome() != ToolOutcome.OK
                    && (r.toolName().equals("getTransactionDetails") || r.toolName().equals("getDeviceSignals"))) {
                items.add(item(EvidenceType.TRANSACTION, Severity.HIGH,
                        "Tool " + r.toolName() + " unavailable: " + r.summary(), r.toolName()));
            }
        }
        return items;
    }

    private EvidenceItemView intake(CaseFacts f) {
        return item(EvidenceType.CUSTOMER_STATEMENT, Severity.INFO,
                "Customer reports " + f.issueType() + "; merchantRecognised=" + f.merchantRecognised()
                        + "; cardPossession=" + f.cardPossession()
                        + "; statement=\"" + abbreviate(f.customerStatement(), 160) + "\"",
                Provenance.USER_INTAKE, null);
    }

    private void transaction(List<EvidenceItemView> items, TransactionDetails d) {
        if (!d.found()) {
            items.add(item(EvidenceType.TRANSACTION, Severity.HIGH,
                    "Transaction record not found", "getTransactionDetails"));
            return;
        }
        items.add(item(EvidenceType.TRANSACTION, Severity.INFO,
                "Transaction " + d.status() + " " + d.amount() + " " + d.currency()
                        + " via " + d.channel() + " merchant=" + d.maskedMerchant(),
                "getTransactionDetails"));
        if (d.authOutcome() == AuthOutcome.FAILED) {
            items.add(item(EvidenceType.TRANSACTION, Severity.HIGH,
                    "Authentication outcome FAILED", "getTransactionDetails"));
        } else if (d.authOutcome() == AuthOutcome.UNAVAILABLE) {
            items.add(item(EvidenceType.TRANSACTION, Severity.MEDIUM,
                    "Authentication outcome unavailable", "getTransactionDetails"));
        }
        if (d.duplicateFingerprintMatch()) {
            items.add(item(EvidenceType.TRANSACTION, Severity.HIGH,
                    "Duplicate payment fingerprint confirmed" +
                            (d.duplicateOfRef() != null ? ", matches " + d.duplicateOfRef() : ""),
                    "getTransactionDetails"));
        }
    }

    private void device(List<EvidenceItemView> items, DeviceSignals d) {
        if (!d.deviceKnown()) {
            items.add(item(EvidenceType.DEVICE, Severity.HIGH,
                    "Device not previously observed (first seen " + d.firstSeenAt() + ")",
                    "getDeviceSignals"));
        } else {
            items.add(item(EvidenceType.DEVICE, Severity.INFO,
                    "Known device since " + d.firstSeenAt(), "getDeviceSignals"));
        }
        if (d.trustLevel() == TrustLevel.LOW) {
            items.add(item(EvidenceType.DEVICE, Severity.HIGH, "Device trust level LOW",
                    "getDeviceSignals"));
        } else if (d.trustLevel() == TrustLevel.MEDIUM) {
            items.add(item(EvidenceType.DEVICE, Severity.MEDIUM, "Device trust level MEDIUM",
                    "getDeviceSignals"));
        }
    }

    private void location(List<EvidenceItemView> items, LocationComparison d) {
        Severity sev = switch (d.anomalyLevel()) {
            case HIGH -> Severity.HIGH;
            case MEDIUM -> Severity.MEDIUM;
            case LOW -> Severity.LOW;
            case NONE -> Severity.INFO;
        };
        items.add(item(EvidenceType.LOCATION, sev,
                "Location anomaly " + d.anomalyLevel() + ": " + d.distanceKm()
                        + " km from trusted activity, " + d.timeDeltaMinutes() + " min since last trusted location",
                "compareRecentLocations"));
    }

    private void merchant(List<EvidenceItemView> items, MerchantRiskProfile d) {
        if (d.blocklistMatch()) {
            items.add(item(EvidenceType.MERCHANT, Severity.HIGH,
                    "Merchant matches internal blocklist (" + d.category() + ")", "getMerchantRiskProfile"));
        } else {
            items.add(item(EvidenceType.MERCHANT,
                    d.riskBand() == RiskBand.HIGH ? Severity.MEDIUM : Severity.INFO,
                    "Merchant risk band " + d.riskBand() + " category " + d.category(),
                    "getMerchantRiskProfile"));
        }
    }

    private void history(List<EvidenceItemView> items, PriorDisputes d) {
        Severity sev = d.count() >= 3 ? Severity.MEDIUM : d.count() > 0 ? Severity.LOW : Severity.INFO;
        items.add(item(EvidenceType.HISTORY, sev,
                d.count() + " prior dispute(s), " + d.similarCount() + " similar, lookback "
                        + d.lookbackDays() + " days", "getPriorDisputes"));
    }

    private static Severity anomalySeverity(AnomalyLevel level) {
        return switch (level) {
            case HIGH -> Severity.HIGH;
            case MEDIUM -> Severity.MEDIUM;
            case LOW -> Severity.LOW;
            case NONE -> Severity.INFO;
        };
    }

    private EvidenceItemView item(EvidenceType type, Severity sev, String summary, String tool) {
        return item(type, sev, summary, Provenance.TOOL_RESULT, tool);
    }

    private EvidenceItemView item(EvidenceType type, Severity sev, String summary,
                                  Provenance provenance, String tool) {
        return new EvidenceItemView("EV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                type, sev, summary, provenance, Instant.now(), tool);
    }

    private static String abbreviate(String s, int n) {
        return s == null ? "" : s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
