package com.apartment.bot;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

@Component
public class PushoverNotifier {
    private final String PUSHOVER_USER_KEY = "u7gru2v16gcnsid28ftgkpf6xzkoy1"; // Aditi
    //private final String PUSHOVER_USER_KEY = "u1kkpk442tbarr5dz1egtdfuumrngn"; // Hemant
    private final String PUSHOVER_API_TOKEN = "ak1cvhpycz66kaymmiobmdyr6rbnpe";

    private LogUtil logUtil;

    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public PushoverNotifier(LogUtil logUtil) {
        // Start a scheduled task to process messages every 1 minute
        scheduler.scheduleAtFixedRate(this::processQueue, 300, 10, TimeUnit.SECONDS);
        this.logUtil = logUtil;
    }

    public boolean queueNotification(String message) {
        boolean status = messageQueue.offer(message);
        logUtil.println("Pushover notification queuing status = " + status + System.lineSeparator() + message);
        return status;
    }

    private void processQueue() {
        String message = messageQueue.poll();
        if (message != null) {
            sendPushoverNotification(message);
        }
    }

    private boolean sendPushoverNotification(String message) {
        logUtil.println("Sending pushover notification" + System.lineSeparator() + message);
        try {
            String urlString = "https://api.pushover.net/1/messages.json";
            String params = String.format("token=%s&user=%s&message=%s",
                    URLEncoder.encode(PUSHOVER_API_TOKEN, StandardCharsets.UTF_8),
                    URLEncoder.encode(PUSHOVER_USER_KEY, StandardCharsets.UTF_8),
                    URLEncoder.encode(message, StandardCharsets.UTF_8));

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(params.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                logUtil.println("✅ Pushover notification sent successfully!");
                return true;
            } else {
                String responseMessage = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                logUtil.println("❗ Failed to send Pushover notification, response code: " + responseCode);
                logUtil.println("🔎 Pushover Response: " + responseMessage);
            }
        } catch (IOException e) {
            logUtil.println("❗ Error sending Pushover notification: " + e.getMessage());
        }
        return false;
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }
}
