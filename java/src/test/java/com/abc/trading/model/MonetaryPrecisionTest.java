package com.abc.trading.model;

import com.abc.trading.adapters.binance.BinanceAccountSnapshot;
import com.abc.trading.execution.Commission;
import com.abc.trading.portfolio.AccountBalance;
import com.abc.trading.portfolio.AccountLedger;
import com.abc.trading.portfolio.AccountState;
import com.abc.trading.portfolio.AccountType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MonetaryPrecisionTest {
    @Test
    void preservesExactMoneyAndCommissionAmounts() {
        Money money = new Money(new BigDecimal("1000.00500001"), "USDT");
        Commission commission = new Commission(new BigDecimal("0.00000001"), "USDT");

        assertEquals(new BigDecimal("1000.00500001"), money.amountDecimal());
        assertEquals(new BigDecimal("0.00000001"), commission.amountDecimal());
        assertEquals(new BigDecimal("1000.00500001"), money.rounded(RoundingMode.HALF_EVEN).amountDecimal());
    }

    @Test
    void preservesExactAccountSnapshotValues() {
        BinanceAccountSnapshot snapshot = new BinanceAccountSnapshot(
                1000L, "USDT", new BigDecimal("1000.00500001"),
                new BigDecimal("900.00499999"), new BigDecimal("0.00000001"),
                new BigDecimal("0.000000005"), new BigDecimal("-0.00000001"));
        AccountState state = snapshot.toAccountState("BINANCE");
        AccountBalance balance = state.balances().get("USDT");

        assertEquals(new BigDecimal("1000.00500001"), state.balanceTotalDecimal());
        assertEquals(new BigDecimal("900.00499999"), balance.freeDecimal());
        assertEquals(new BigDecimal("1000.00500000"), state.equityDecimal());
    }

    @Test
    void ledgerStoresExactStartingBalance() {
        AccountLedger ledger = new AccountLedger();
        ledger.configure("BINANCE", new BigDecimal("1000.00500001"), "USDT",
                BigDecimal.ONE, AccountType.MARGIN);

        assertEquals(new BigDecimal("1000.00500001"), ledger.state("BINANCE", 1).balanceTotalDecimal());
    }
}
