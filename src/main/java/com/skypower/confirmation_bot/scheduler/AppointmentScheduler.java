package com.skypower.confirmation_bot.scheduler;

import com.skypower.confirmation_bot.calendar.GoogleCalendarService;
import com.skypower.confirmation_bot.model.Appointment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// Replaced by NotificationScheduler
// // @Component
// @EnableScheduling
public class AppointmentScheduler {

    private static final Logger log = LoggerFactory.getLogger(AppointmentScheduler.class);

    private final GoogleCalendarService calendarService;

    public AppointmentScheduler(GoogleCalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @Scheduled(cron = "${google.calendar.schedule-cron:0 0 8 * * *}")
    public void processAppointments() {
        log.info("Starting appointment processing...");
        List<Appointment> appointments = calendarService.fetchPendingAppointments();
        log.info("Found {} pending appointment(s) with phone numbers", appointments.size());
        appointments.forEach(a ->
                log.info("  → {} | {} | {}", a.patientName(), a.startTime(), a.phoneNumber())
        );
    }
}
