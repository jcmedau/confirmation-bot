package com.skypower.confirmation_bot.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.messages")
public class MessageTemplates {

    private String confirmation;
    private String thanks;
    private String reschedule;
    private String cancellation;
    private String noAnswerRetry;
    private String invalidOption;

    public String getConfirmation() { return confirmation; }
    public void setConfirmation(String confirmation) { this.confirmation = confirmation; }

    public String getThanks() { return thanks; }
    public void setThanks(String thanks) { this.thanks = thanks; }

    public String getReschedule() { return reschedule; }
    public void setReschedule(String reschedule) { this.reschedule = reschedule; }

    public String getCancellation() { return cancellation; }
    public void setCancellation(String cancellation) { this.cancellation = cancellation; }

    public String getNoAnswerRetry() { return noAnswerRetry; }
    public void setNoAnswerRetry(String noAnswerRetry) { this.noAnswerRetry = noAnswerRetry; }

    public String getInvalidOption() { return invalidOption; }
    public void setInvalidOption(String invalidOption) { this.invalidOption = invalidOption; }
}
