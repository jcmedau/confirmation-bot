package com.skypower.confirmation_bot.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class EvolutionWhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(EvolutionWhatsAppService.class);

    private final RestClient restClient;
    private final EvolutionProperties props;

    public EvolutionWhatsAppService(RestClient.Builder builder, EvolutionProperties props) {
        this.props = props;
        this.restClient = builder
                .baseUrl(props.getBaseUrl())
                .defaultHeader("apikey", props.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Sends a WhatsApp message via Evolution API.
     *
     * @return true if the message was sent successfully, false otherwise
     */
    public boolean sendMessage(String phoneNumber, String text) {
        String cleanPhone = phoneNumber.replaceAll("[^\\d]", "");

        Map<String, Object> payload = Map.of(
                "number", cleanPhone,
                "text", text
        );

        try {
            restClient.post()
                    .uri("/message/sendText/" + props.getInstance())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Message sent to {}", cleanPhone);
            return true;
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message to {}: {}", cleanPhone, e.getMessage());
            return false;
        }
    }
}
