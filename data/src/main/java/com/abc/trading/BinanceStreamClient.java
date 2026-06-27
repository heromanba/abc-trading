
package com.abc.trading;

import com.binance.connector.client.impl.WebSocketStreamClientImpl;
import java.util.ArrayList;

public class BinanceStreamClient {
    public static void main(String[] args) {
        // Initialize the official WebSocket client
        WebSocketStreamClientImpl client = new WebSocketStreamClientImpl();

        // 1. Listening to a Single Stream (e.g., Aggregate Trade Stream for BTCUSDT)
        System.out.println("Connecting to BTCUSDT trade stream...");
        int tradeStreamId = client.aggTradeStream("btcusdt", response -> {
            // This callback triggers every time Binance pushes a trade event
            System.out.println("Received Trade Event: " + response);
        });

        // 2. Combining Multiple Streams (e.g., listening to multiple tickers at once)
        ArrayList<String> multipleStreams = new ArrayList<>();
        multipleStreams.add("ethusdt@trade");
        multipleStreams.add("solusdt@trade");

        System.out.println("Connecting to combined streams (ETH & SOL)...");
        int combinedStreamId = client.combineStreams(multipleStreams, response -> {
            System.out.println("Received Combined Event: " + response);
        });

        // Add a shutdown hook to close the WebSocket connections gracefully when stopping the application
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Closing active streams...");
            client.closeConnection(tradeStreamId);
            client.closeAllConnections();
        }));
    }
}
