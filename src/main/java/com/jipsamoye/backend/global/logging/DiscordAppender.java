package com.jipsamoye.backend.global.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DiscordAppender extends AppenderBase<ILoggingEvent> {

    private String webhookUrl;

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;

        try {
            String message = buildMessage(event);
            sendToDiscord(message);
        } catch (Exception e) {
            addError("Discord 알림 전송 실패", e);
        }
    }

    private String buildMessage(ILoggingEvent event) {
        String timestamp = Instant.ofEpochMilli(event.getTimeStamp())
                .atZone(ZoneId.of("Asia/Seoul"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StringBuilder sb = new StringBuilder();
        sb.append("🚨 **[ERROR]** `").append(timestamp).append("`\\n");
        sb.append("**Logger:** `").append(event.getLoggerName()).append("`\\n");
        sb.append("**Message:** ").append(escapeJson(event.getFormattedMessage())).append("\\n");

        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            sb.append("**Exception:** `").append(throwable.getClassName()).append(": ").append(escapeJson(throwable.getMessage())).append("`\\n");
            sb.append("```\\n");
            StackTraceElementProxy[] stackTrace = throwable.getStackTraceElementProxyArray();
            int limit = Math.min(stackTrace.length, 10);
            for (int i = 0; i < limit; i++) {
                sb.append(stackTrace[i].getSTEAsString()).append("\\n");
            }
            if (stackTrace.length > 10) {
                sb.append("... ").append(stackTrace.length - 10).append(" more\\n");
            }
            sb.append("```");
        }

        return sb.toString();
    }

    private void sendToDiscord(String message) throws Exception {
        URL url = new URL(webhookUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        String json = "{\"content\":\"" + message + "\"}";

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        conn.getResponseCode();
        conn.disconnect();
    }

    private String escapeJson(String text) {
        if (text == null) return "null";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "  ");
    }
}
