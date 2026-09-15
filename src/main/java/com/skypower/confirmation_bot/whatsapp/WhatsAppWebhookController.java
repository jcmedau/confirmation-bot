package com.skypower.confirmation_bot.whatsapp;

import com.skypower.confirmation_bot.notification.AppointmentNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final AppointmentNotificationService notificationService;

    public WhatsAppWebhookController(AppointmentNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/evolution")
    public ResponseEntity<Void> handleWebhook(@RequestBody WebhookPayload payload) {
        String event = payload.getEvent();
        log.info("Webhook received — event: '{}'", event);

        // Evolution API v1 sends "MESSAGES_UPSERT" (uppercase, underscore)
        // Evolution API v2 sends "messages.upsert" (lowercase, dot) — accept both
        boolean isMessageEvent = "messages.upsert".equalsIgnoreCase(event)
                || "MESSAGES_UPSERT".equalsIgnoreCase(event);
        if (!isMessageEvent) {
            log.debug("Ignoring event type: '{}'", event);
            return ResponseEntity.ok().build();
        }

        WebhookPayload.Data data = payload.getData();
        if (data == null || data.getKey() == null) {
            log.warn("Message event '{}' arrived but data/key is null", event);
            return ResponseEntity.ok().build();
        }

        log.info("  fromMe={} remoteJid='{}'", data.getKey().isFromMe(), data.getKey().getRemoteJid());

        // Ignore messages sent by us
        if (data.getKey().isFromMe()) {
            return ResponseEntity.ok().build();
        }

        // Extract phone: strip "@s.whatsapp.net" or "@g.us"
        String remoteJid = data.getKey().getRemoteJid();
        if (remoteJid == null) return ResponseEntity.ok().build();
        String phone = remoteJid.replace("@s.whatsapp.net", "").replace("@g.us", "");

        // getText() handles both plain "conversation" and quoted "extendedTextMessage"
        if (data.getMessage() == null) {
            log.warn("  message object is null for phone {}", phone);
            return ResponseEntity.ok().build();
        }
        String body = data.getMessage().getText();
        if (body == null || body.isBlank()) {
            log.debug("  Non-text or empty message from {} — ignoring", phone);
            return ResponseEntity.ok().build();
        }

        log.info("Reply from {}: '{}'", phone, body.trim());
        notificationService.handleReply(phone, body.trim());

        return ResponseEntity.ok().build();
    }
}
