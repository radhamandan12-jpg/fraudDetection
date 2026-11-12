package com.training.fraudcase;

import com.training.fraudcase.tools.SyntheticEvidenceAdapter;
import com.training.fraudcase.domain.enums.AnomalyLevel;
import com.training.fraudcase.domain.enums.AuthOutcome;
import com.training.fraudcase.domain.enums.TrustLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.training.fraudcase.TestFixtures.adapter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntheticEvidenceAdapterTest {

    private final SyntheticEvidenceAdapter a = adapter();

    @Test
    void deterministicForSameSeedAndInputs() {
        var d1 = a.deviceSignals("CUST-1", "TXN-1");
        var d2 = a.deviceSignals("CUST-1", "TXN-1");
        assertEquals(d1.deviceKnown(), d2.deviceKnown());
        assertEquals(d1.trustLevel(), d2.trustLevel());
    }

    @Test
    void fraudScenarioMarkers() {
        var txn = a.transactionDetails("TXN-FRAUD-0042", new BigDecimal("129.99"), "EUR");
        assertTrue(txn.found());
        assertEquals(AuthOutcome.UNAVAILABLE, txn.authOutcome());
        var dev = a.deviceSignals("CUST-8842", "TXN-FRAUD-0042");
        assertFalse(dev.deviceKnown());
        assertEquals(TrustLevel.LOW, dev.trustLevel());
        var loc = a.recentLocations("CUST-8842", "EU-IE", "TXN-FRAUD-0042");
        assertEquals(AnomalyLevel.HIGH, loc.anomalyLevel());
    }

    @Test
    void duplicateScenarioMarker() {
        var txn = a.transactionDetails("TXN-DUP-0007", new BigDecimal("10"), "USD");
        assertTrue(txn.found());
        assertTrue(txn.duplicateFingerprintMatch());
        assertTrue(txn.duplicateOfRef() != null);
    }

    @Test
    void missingTransactionNotFound() {
        var txn = a.transactionDetails("TXN-MISSING-9", null, null);
        assertFalse(txn.found());
    }

    @Test
    void blocklistedMerchant() {
        var m = a.merchantRisk("MRC-BLOCK-99");
        assertTrue(m.blocklistMatch());
    }

    @Test
    void velocityMarkerProducesBurst() {
        var v = a.transactionVelocity("CUST-VELOCITY-1", "TXN-1");
        assertTrue(v.countLast10Min() >= 5);
        assertEquals(AnomalyLevel.HIGH, v.anomalyLevel());
        var calm = a.transactionVelocity("CUST-8842", "TXN-1");
        assertTrue(calm.countLast10Min() < 5);
    }

    @Test
    void bigAmountMarkerProducesHighDeviation() {
        var d = a.amountDeviation("CUST-8842", new BigDecimal("600"), "TXN-BIGAMT-1");
        assertTrue(d.ratioVsBaseline().doubleValue() > 5.0);
        assertEquals(AnomalyLevel.HIGH, d.anomalyLevel());
        var normal = a.amountDeviation("CUST-8842", new BigDecimal("600"), "TXN-1");
        assertTrue(normal.ratioVsBaseline().doubleValue() <= 1.5);
    }

    @Test
    void sanctionMarkerProducesScreeningMatch() {
        var hit = a.sanctionsScreening("CUST-8842", "MRC-SANCTION-1");
        assertTrue(hit.matchFound());
        var clean = a.sanctionsScreening("CUST-8842", "MRC-SHOP-12");
        assertFalse(clean.matchFound());
    }
}
