use futures_util::StreamExt;
use serde::Deserialize;
use std::fs;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};
use tokio::time::sleep;
use tokio_tungstenite::{connect_async, tungstenite::protocol::Message};

#[derive(Debug, Deserialize)]
struct TickerMessage {
    #[serde(rename = "s")]
    symbol: String,
    #[serde(rename = "c")]
    close_price: String,
    #[serde(rename = "v")]
    volume: String,
    #[serde(rename = "E")]
    event_time: u64,
}

fn rss_kb() -> usize {
    if let Ok(status) = fs::read_to_string("/proc/self/status") {
        for line in status.lines() {
            if let Some(rest) = line.strip_prefix("VmRSS:") {
                return rest.split_whitespace().next().unwrap_or("0").parse::<usize>().unwrap_or(0);
            }
        }
    }
    0
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let url = "wss://stream.binance.com:9443/ws/btcusdt@bookTicker";
    let (ws_stream, _) = connect_async(url).await?;
    let (_, mut read) = ws_stream.split();

    let messages = Arc::new(AtomicUsize::new(0));
    let parse_errors = Arc::new(AtomicUsize::new(0));
    let latencies_ns = Arc::new(Mutex::new(Vec::<u128>::new()));
    let messages_for_task = messages.clone();
    let parse_errors_for_task = parse_errors.clone();
    let latencies_for_task = latencies_ns.clone();

    tokio::spawn(async move {
        while let Some(msg) = read.next().await {
            match msg {
                Ok(Message::Text(text)) => {
                    let start = Instant::now();
                    match serde_json::from_str::<serde_json::Value>(&text) {
                        Ok(value) => {
                            let payload_count = match &value {
                                serde_json::Value::Array(items) => items.len(),
                                serde_json::Value::Object(map) => {
                                    if let Some(data) = map.get("data") {
                                        data.as_array().map_or(0, |items| items.len())
                                    } else {
                                        1
                                    }
                                }
                                _ => 0,
                            };
                            let latency_ns = start.elapsed().as_nanos();
                            let count = messages_for_task.fetch_add(payload_count, Ordering::SeqCst) + payload_count;
                            for _ in 0..payload_count {
                                latencies_for_task.lock().unwrap().push(latency_ns);
                            }
                            if count % 100 == 0 {
                                println!("rust tokio processed {count} messages");
                            }
                        }
                        Err(_) => {
                            parse_errors_for_task.fetch_add(1, Ordering::SeqCst);
                        }
                    }
                }
                Ok(Message::Close(_)) => break,
                Err(err) => {
                    eprintln!("websocket error: {err}");
                    break;
                }
                _ => {}
            }
        }
    });

    sleep(Duration::from_secs(5)).await;
    let benchmark_started = Instant::now();
    sleep(Duration::from_secs(45)).await;

    let duration_secs = benchmark_started.elapsed().as_secs_f64();
    let count = messages.load(Ordering::SeqCst);
    let parse_errors_count = parse_errors.load(Ordering::SeqCst);
    let latencies = latencies_ns.lock().unwrap().clone();
    let mut sorted = latencies.clone();
    sorted.sort_unstable();
    let avg_ns = if sorted.is_empty() { 0 } else { sorted.iter().sum::<u128>() / sorted.len() as u128 };
    let p99_9_index = ((sorted.len() as f64 * 0.999) as usize).min(sorted.len().saturating_sub(1));
    let p99_9_ns = sorted.get(p99_9_index).copied().unwrap_or(0);
    let throughput_mps = if duration_secs > 0.0 { count as f64 / duration_secs } else { 0.0 };
    let rss_kb = rss_kb();

    println!("rust tokio benchmark metrics");
    println!("  messages: {count}");
    println!("  parse_errors: {parse_errors_count}");
    println!("  duration_seconds: {duration_secs:.3}");
    println!("  average_ingest_latency_us: {:.3}", avg_ns as f64 / 1000.0);
    println!("  p99_9_latency_us: {:.3}", p99_9_ns as f64 / 1000.0);
    println!("  throughput_mps: {throughput_mps:.3}");
    println!("  rss_kb: {rss_kb}");
    Ok(())
}
