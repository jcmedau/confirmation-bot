package com.skypower.confirmation_bot.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class EvolutionWebhookRegistrar {

    private static final Logger log = LoggerFactory.getLogger(EvolutionWebhookRegistrar.class);

    private final EvolutionProperties props;
    private final String webhookUrl;

    public EvolutionWebhookRegistrar(EvolutionProperties props,
                                     @Value("${evolution.webhook-callback-url:http://appointment-bot:8080/webhook/evolution}") String webhookUrl) {
        this.props = props;
        this.webhookUrl = webhookUrl;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerWebhook() {
        log.info("Registering Evolution webhook → {}", webhookUrl);

        RestClient client = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("apikey", props.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();

        Map<String, Object> body = Map.of(
                "webhook", Map.of(
                        "enabled", true,
                        "url", webhookUrl,
                        "webhook_by_events", false,
                        "webhook_base64", false,
                        "events", List.of("MESSAGES_UPSERT")
                )
        );

        try {
            client.post()
                    .uri("/webhook/set/" + props.getInstance())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Evolution webhook registered successfully");
        } catch (Exception e) {
            log.error("Failed to register Evolution webhook: {}", e.getMessage());
        }
    }
}
