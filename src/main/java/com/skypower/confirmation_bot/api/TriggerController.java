package com.skypower.confirmation_bot.api;

import com.skypower.confirmation_bot.notification.AppointmentNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class TriggerController {

    private static final Logger log = LoggerFactory.getLogger(TriggerController.class);

    private final AppointmentNotificationService notificationService;

    @Value("${app.trigger.key}")
    private String triggerKey;

    public TriggerController(AppointmentNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/triggernow")
    public ResponseEntity<Map<String, String>> triggerNow(@RequestBody Map<String, String> body) {
        String providedKey = body.get("key");

        if (providedKey == null || !providedKey.equals(triggerKey)) {
            log.warn("Trigger attempt with invalid key: '{}'", providedKey);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized"));
        }

        log.info("Manual trigger received — running sendPendingConfirmations");
        notificationService.sendPendingConfirmations();

        return ResponseEntity.ok(Map.of("status", "ok", "message", "Confirmations triggered successfully"));
    }
}
