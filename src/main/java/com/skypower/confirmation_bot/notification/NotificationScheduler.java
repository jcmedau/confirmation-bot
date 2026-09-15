package com.skypower.confirmation_bot.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.util.TimeZone;

@Component
@EnableScheduling
public class NotificationScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    // Wait 1 minute after startup before the first retry scan so the WhatsApp
    // session has time to connect and any in-flight calendar updates have settled.
    private static final Duration STARTUP_DELAY = Duration.ofMinutes(1);

    // Retry check runs every 30 minutes to catch appointments whose 3-hour window has elapsed.
    private static final Duration RETRY_INTERVAL = Duration.ofMinutes(30);

    private final AppointmentNotificationService notificationService;
    private final NotificationProperties notificationProps;
    private final ZoneId zoneId;

    public NotificationScheduler(AppointmentNotificationService notificationService,
                                  NotificationProperties notificationProps,
                                  ZoneId appZoneId) {
        this.notificationService = notificationService;
        this.notificationProps   = notificationProps;
        this.zoneId              = appZoneId;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        TimeZone tz = TimeZone.getTimeZone(zoneId);

        // ── Morning sweep (09:00 by default) ──────────────────────────────────
        String morningCron = timeToCron(notificationProps.getMorningTime());
        log.info("Scheduling morning confirmation sweep: cron='{}' zone='{}'", morningCron, zoneId);
        registrar.addCronTask(new CronTask(
                () -> {
                    log.info("Morning sweep triggered — sending pending confirmations");
                    notificationService.sendPendingConfirmations();
                },
                new CronTrigger(morningCron, tz)
        ));

        // ── Afternoon sweep (14:00 by default) ────────────────────────────────
        String afternoonCron = timeToCron(notificationProps.getAfternoonTime());
        log.info("Scheduling afternoon confirmation sweep: cron='{}' zone='{}'", afternoonCron, zoneId);
        registrar.addCronTask(new CronTask(
                () -> {
                    log.info("Afternoon sweep triggered — sending pending confirmations");
                    notificationService.sendPendingConfirmations();
                },
                new CronTrigger(afternoonCron, tz)
        ));

        // ── Retry check every 30 minutes ──────────────────────────────────────
        log.info("Scheduling no-answer retry check every {} minutes (first run in {} seconds)",
                RETRY_INTERVAL.toMinutes(), STARTUP_DELAY.toSeconds());
        registrar.addFixedRateTask(new FixedRateTask(
                () -> {
                    log.info("Retry check triggered — checking unanswered appointments");
                    notificationService.retryUnanswered();
                },
                RETRY_INTERVAL,
                STARTUP_DELAY
        ));
    }

    /**
     * Converts "HH:mm" to a Spring cron expression "0 mm HH * * *".
     */
    private String timeToCron(String time) {
        String[] parts = time.split(":");
        return "0 " + Integer.parseInt(parts[1]) + " " + Integer.parseInt(parts[0]) + " * * *";
    }
}
