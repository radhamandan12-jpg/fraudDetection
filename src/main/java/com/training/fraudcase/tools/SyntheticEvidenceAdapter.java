package com.training.fraudcase.tools;

import com.training.fraudcase.domain.enums.AnomalyLevel;
import com.training.fraudcase.domain.enums.AuthOutcome;
import com.training.fraudcase.domain.enums.RiskBand;
import com.training.fraudcase.domain.enums.TransactionChannel;
import com.training.fraudcase.domain.enums.TransactionStatus;
import com.training.fraudcase.domain.enums.TrustLevel;
import com.training.fraudcase.support.Hashes;
import com.training.fraudcase.tools.dto.CaseSla;
import com.training.fraudcase.tools.dto.DeviceSignals;
import com.training.fraudcase.tools.dto.LocationComparison;
import com.training.fraudcase.tools.dto.MerchantRiskProfile;
import com.training.fraudcase.tools.dto.PriorDisputes;
import com.training.fraudcase.tools.dto.TransactionDetails;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Deterministic, read-only training adapter behind the approved tool
 * catalogue. Results are reproducible for a given
 * {@code capstone.tools.deterministic-seed} and input.
 *
 * Keyword markers in references produce fixed scenarios for demos/tests:
 *   FRAUD   -> new device, distant location, unavailable auth
 *   DUP     -> duplicate payment fingerprint match
 *   GENUINE -> trusted device, local activity, frictionless auth
 *   MISSING -> transaction not found
 *   BLOCK   -> merchant blocklist match (maskedMerchant)
 *   REPEAT  -> several similar prior disputes (maskedCustomerRef)
 * Otherwise values derive from a seeded hash of the inputs.
 */
@Component
public class SyntheticEvidenceAdapter {

    private final String seed;

    public SyntheticEvidenceAdapter(com.training.fraudcase.config.CapstoneProperties props) {
        this.seed = props.getTools().getDeterministicSeed();
    }

    private int hash(String... parts) {
        return Hashes.stableInt(seed, String.join("|", parts));
    }

    private static boolean has(String value, String marker) {
        return value != null && value.toUpperCase().contains(marker);
    }

    public TransactionDetails transactionDetails(String transactionRef, BigDecimal expectedAmount, String currency) {
        if (transactionRef == null || transactionRef.isBlank()
                || has(transactionRef, "MISSING") || has(transactionRef, "404")) {
            return new TransactionDetails(false, null, null, null, null, null, null, null, false, null);
        }
        BigDecimal amount = expectedAmount != null ? expectedAmount
                : BigDecimal.valueOf(9.99 + hash(transactionRef) % 990).setScale(2, RoundingMode.HALF_UP);
        String curr = currency != null ? currency : "USD";
        Instant ts = Instant.now().minus(1 + hash(transactionRef, "ts") % 72, ChronoUnit.HOURS);

        AuthOutcome auth;
        if (has(transactionRef, "FRAUD")) auth = AuthOutcome.UNAVAILABLE;
        else if (has(transactionRef, "GENUINE")) auth = AuthOutcome.FRICTIONLESS;
        else if (has(transactionRef, "DUP")) auth = AuthOutcome.FRICTIONLESS;
        else auth = AuthOutcome.values()[hash(transactionRef, "auth") % AuthOutcome.values().length];

        // Explicit scenario markers suppress the hash fallback so demo refs
        // behave predictably; otherwise ~1-in-11 refs look like duplicates.
        boolean explicit = has(transactionRef, "DUP") || has(transactionRef, "MERCH")
                || has(transactionRef, "GENUINE") || has(transactionRef, "FRAUD");
        boolean dup = has(transactionRef, "DUP") || (!explicit && hash(transactionRef, "dup") % 11 == 0);
        TransactionChannel channel = has(transactionRef, "ATM") ? TransactionChannel.ATM : TransactionChannel.CARD_NOT_PRESENT;
        String merchant = has(transactionRef, "FRAUD") ? "MRC-UNRECOGNISED-77" : "MRC-" + (100 + hash(transactionRef, "m") % 900);

        return new TransactionDetails(true, TransactionStatus.POSTED, ts, amount, curr, channel,
                auth, merchant, dup, dup ? "TXN-DUP-ORIG-" + (hash(transactionRef) % 900 + 100) : null);
    }

    public DeviceSignals deviceSignals(String maskedCustomerRef, String contextRef) {
        boolean fraud = has(contextRef, "FRAUD") || has(maskedCustomerRef, "NEWDEV");
        boolean genuine = has(contextRef, "GENUINE") || has(maskedCustomerRef, "TRUSTED");
        boolean known;
        TrustLevel trust;
        Instant firstSeen;
        List<String> obs;
        if (fraud) {
            known = false; trust = TrustLevel.LOW;
            firstSeen = Instant.now().minus(3, ChronoUnit.HOURS);
            obs = List.of("Device first observed hours before the transaction", "No prior trusted sessions");
        } else if (genuine) {
            known = true; trust = TrustLevel.HIGH;
            firstSeen = Instant.now().minus(400, ChronoUnit.DAYS);
            obs = List.of("Device used for >100 prior sessions", "Biometric unlock present");
        } else {
            int h = hash(maskedCustomerRef, "dev");
            known = h % 3 != 0;
            trust = TrustLevel.values()[h % TrustLevel.values().length];
            firstSeen = Instant.now().minus(1 + h % 300, ChronoUnit.DAYS);
            obs = List.of(known ? "Device recognised" : "Device not previously observed",
                    "Trust level derived from synthetic adapter");
        }
        return new DeviceSignals(known, firstSeen, trust, obs);
    }

    public LocationComparison recentLocations(String maskedCustomerRef, String homeRegion, String contextRef) {
        boolean fraud = has(contextRef, "FRAUD") || has(maskedCustomerRef, "DISTANT");
        boolean genuine = has(contextRef, "GENUINE") || has(maskedCustomerRef, "LOCAL")
                || has(maskedCustomerRef, "TRUSTED");
        if (fraud) {
            return new LocationComparison(4200, 95, AnomalyLevel.HIGH, List.of(
                    "Transaction location inconsistent with recent trusted activity",
                    "Impossible-travel window versus last trusted session"));
        }
        if (genuine) {
            return new LocationComparison(4, 4320, AnomalyLevel.NONE, List.of(
                    "Location consistent with trusted home region " + homeRegion));
        }
        int h = hash(maskedCustomerRef, "loc");
        double km = h % 5000;
        long delta = 30 + h % 5000;
        AnomalyLevel level = km > 2000 && delta < 180 ? AnomalyLevel.HIGH
                : km > 800 ? AnomalyLevel.MEDIUM : km > 100 ? AnomalyLevel.LOW : AnomalyLevel.NONE;
        return new LocationComparison(km, delta, level, List.of(
                "Compared against last 5 trusted locations", "Distance and time delta are synthetic"));
    }

    public MerchantRiskProfile merchantRisk(String maskedMerchant) {
        boolean blocked = has(maskedMerchant, "BLOCK") || has(maskedMerchant, "UNRECOGNISED");
        int h = hash(maskedMerchant, "mrc");
        RiskBand band = blocked ? RiskBand.HIGH
                : RiskBand.values()[h % RiskBand.values().length];
        String category = has(maskedMerchant, "FUEL") ? "FUEL"
                : has(maskedMerchant, "GROC") ? "GROCERY" : "ECOMMERCE_GENERAL";
        List<String> obs = blocked
                ? List.of("Merchant appears on internal watch list", "Elevated chargeback rate")
                : List.of("Merchant profile within normal training parameters");
        return new MerchantRiskProfile(band, blocked, category, obs);
    }

    public PriorDisputes priorDisputes(String maskedCustomerRef) {
        boolean repeat = has(maskedCustomerRef, "REPEAT");
        int h = hash(maskedCustomerRef, "hist");
        int count = repeat ? 4 + h % 4 : h % 3;
        int similar = repeat ? 2 + h % 3 : (count > 0 ? h % count + 0 : 0);
        List<String> obs = repeat
                ? List.of("Multiple earlier claims share device or merchant traits")
                : count == 0 ? List.of("No prior disputes in lookback window")
                        : List.of(count + " prior dispute(s) in lookback window");
        return new PriorDisputes(count, Math.min(similar, count), 180, obs);
    }

    public CaseSla caseSla(String route, String riskLevel) {
        int hours = switch (riskLevel == null ? "MEDIUM" : riskLevel.toUpperCase()) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 8;
            case "MEDIUM" -> 24;
            default -> 72;
        };
        String queue = route == null ? "MANUAL_REVIEW" : route.toUpperCase();
        String priority = hours <= 8 ? "P1" : hours <= 24 ? "P2" : "P3";
        return new CaseSla(queue, priority, Instant.now().plus(hours, ChronoUnit.HOURS),
                "Training SLA target: " + priority + " within " + hours + "h for queue " + queue);
    }
}
