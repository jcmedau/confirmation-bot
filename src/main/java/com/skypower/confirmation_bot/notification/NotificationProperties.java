package com.skypower.confirmation_bot.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.notification")
public class NotificationProperties {

    /** How many hours to wait for a reply before sending the no-answer reminder. */
    private int waitingHours = 3;

    /** Time of day (HH:mm) to send the first confirmation sweep, in the app timezone. */
    private String morningTime = "09:00";

    /** Time of day (HH:mm) to send the second confirmation sweep, in the app timezone. */
    private String afternoonTime = "14:00";

    public int getWaitingHours() { return waitingHours; }
    public void setWaitingHours(int waitingHours) { this.waitingHours = waitingHours; }

    public String getMorningTime() { return morningTime; }
    public void setMorningTime(String morningTime) { this.morningTime = morningTime; }

    public String getAfternoonTime() { return afternoonTime; }
    public void setAfternoonTime(String afternoonTime) { this.afternoonTime = afternoonTime; }
}
