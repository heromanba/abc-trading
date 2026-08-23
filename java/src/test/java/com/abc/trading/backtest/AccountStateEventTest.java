package com.abc.trading.backtest;

import com.abc.trading.data.MarketDataSnapshot;
import com.abc.trading.execution.SignalDirection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountStateEventTest {
    @Test
    void backtestLogContainsAccountStateBalanceAndMarginFields(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("events.csv");
        BacktestEngine engine = new BacktestEngine(output.toString());
        engine.addVenue("XNAS");
        engine.configureAccount("XNAS", 1_000.0, "USD", 2.0);
        engine.addInstrument("AAPL", "XNAS");
        engine.start();
        engine.runMarketData(new MarketDataSnapshot[] {
                new MarketDataSnapshot("AAPL", 100, 100.0, 101.0, 100.5, 100.5, 100.5, 1)
        });
        engine.submitMarketOrder("account-test", "AAPL", "buy", SignalDirection.BUY, 1, 100, 100.0);
        engine.close();

        String log = Files.readString(output);
        assertTrue(log.lines().findFirst().orElseThrow().contains("account_total"));
        assertTrue(log.contains(",ACCOUNT_STATE,"));
        assertTrue(log.contains(",1000.00000000,"));
    }
}
