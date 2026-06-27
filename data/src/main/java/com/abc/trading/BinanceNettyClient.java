package com.abc.trading;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.net.URI;

public class BinanceNettyClient {
    
    private static final String HOST = "stream.binance.com";
    private static final int PORT = 9443;
    
    // Explicitly configure the /stream endpoint using query string arguments
    private static final String URL_STRING = "wss://stream.binance.com:9443/ws/btcusdt@aggTrade";

    public static void main(String[] args) throws Exception {
        URI uri = new URI(URL_STRING);
        EventLoopGroup group = new NioEventLoopGroup();

        try {
            // Establish standard SSL client context 
            final SslContext sslCtx = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE).build();

            // Pass the explicitly generated URI object to the handshaker block
            final BinanceWebSocketHandler handler = new BinanceWebSocketHandler(
                    WebSocketClientHandshakerFactory.newHandshaker(
                            uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders()));

            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioSocketChannel.class)
             .handler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ChannelPipeline p = ch.pipeline();
                     
                     // 1. SSL/TLS Layer (Using the safe static HOST variable)
                     p.addLast(sslCtx.newHandler(ch.alloc(), HOST, PORT));
                     
                     // 2. HTTP Protocol Parsing
                     p.addLast(new HttpClientCodec());
                     
                     // 3. Object Aggregation (8MB max chunk limit)
                     p.addLast(new HttpObjectAggregator(8192 * 1024));
                     
                     // 4. WebSocket Payload Frame Extensions
                     p.addLast(WebSocketClientCompressionHandler.INSTANCE);
                     
                     // 5. Custom Connection Flow Handler
                     p.addLast(handler);
                 }
             });

            System.out.println("Connecting to Netty WebSocket pipeline...");
            
            // FIX: Use the clean, hardcoded static HOST configuration string instead of uri.getHost()
            Channel ch = b.connect(HOST, PORT).sync().channel();
            
            // Await full handshake evaluation loop
            handler.handshakeFuture().sync();
            System.out.println("Handshake complete! Streaming market data...");

            // Block main execution runtime scope until channel exits
            ch.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
