package com.skypower.confirmation_bot.model;

import java.time.ZonedDateTime;

public record Appointment(
        String eventId,
        String patientName,
        ZonedDateTime startTime,
        String phoneNumber,
        AppointmentStatus status,
        boolean allDay
) {}
