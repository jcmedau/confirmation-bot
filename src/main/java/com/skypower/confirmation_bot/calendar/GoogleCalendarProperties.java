package com.skypower.confirmation_bot.calendar;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "google.calendar")
public class GoogleCalendarProperties {

    private String credentialsPath = "credentials/service-account.json";
    private String calendarId = "primary";
    private int lookaheadHours = 48;
    private String applicationName = "confirmation-bot";
    private String scheduleCron = "0 0 8 * * *";

    public String getCredentialsPath() { return credentialsPath; }
    public void setCredentialsPath(String credentialsPath) { this.credentialsPath = credentialsPath; }

    public String getCalendarId() { return calendarId; }
    public void setCalendarId(String calendarId) { this.calendarId = calendarId; }

    public int getLookaheadHours() { return lookaheadHours; }
    public void setLookaheadHours(int lookaheadHours) { this.lookaheadHours = lookaheadHours; }

    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }

    public String getScheduleCron() { return scheduleCron; }
    public void setScheduleCron(String scheduleCron) { this.scheduleCron = scheduleCron; }
}
