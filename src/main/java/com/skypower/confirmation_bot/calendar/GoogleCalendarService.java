package com.skypower.confirmation_bot.calendar;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.skypower.confirmation_bot.model.Appointment;
import com.skypower.confirmation_bot.model.AppointmentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GoogleCalendarService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);
    public static final String STATUS_KEY = "confirmationStatus";
    public static final String MESSAGE_SENT_AT_KEY = "messageSentAt";
    public static final String RETRY_COUNT_KEY = "retryCount";

    // Explicitly request the fields we need — ensures extendedProperties are always returned
    // by the Calendar API list endpoint (it may omit them otherwise).
    private static final String EVENT_FIELDS =
            "items(id,summary,start,description,extendedProperties)";

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?i)phone\\s*:\\s*([+\\d][\\d\\s\\-.()]{5,20})");

    private final Calendar calendar;
    private final GoogleCalendarProperties props;
    private final ZoneId zoneId;

    public GoogleCalendarService(@Lazy Calendar calendar, GoogleCalendarProperties props, ZoneId appZoneId) {
        this.calendar = calendar;
        this.props    = props;
        this.zoneId   = appZoneId;
    }

    // ── Fetch methods ──────────────────────────────────────────────────────────

    public List<Appointment> fetchAllAppointmentsForDay(LocalDate date) {
        ZonedDateTime startOfDay = date.atStartOfDay(zoneId);
        ZonedDateTime endOfDay   = date.atTime(23, 59, 59).atZone(zoneId);
        return fetchEvents(startOfDay, endOfDay, null);
    }

    public List<Appointment> fetchPendingAppointments() {
        ZonedDateTime now    = ZonedDateTime.now();
        ZonedDateTime future = now.plus(Duration.ofHours(props.getLookaheadHours()));
        return fetchEvents(now, future, AppointmentStatus.PENDING);
    }

    public List<Appointment> fetchAwaitingReplyAppointments() {
        ZonedDateTime now    = ZonedDateTime.now();
        ZonedDateTime future = now.plus(Duration.ofDays(3));
        return fetchEvents(now, future, AppointmentStatus.AWAITING_REPLY);
    }

    /** Fetch every appointment (all statuses) between two dates, inclusive. */
    public List<Appointment> fetchAllAppointmentsInRange(LocalDate from, LocalDate to) {
        ZonedDateTime start = from.atStartOfDay(zoneId);
        ZonedDateTime end   = to.atTime(23, 59, 59).atZone(zoneId);
        return fetchEvents(start, end, null);
    }

    private List<Appointment> fetchEvents(ZonedDateTime from, ZonedDateTime to, AppointmentStatus filterStatus) {
        try {
            Events events = calendar.events().list(props.getCalendarId())
                    .setTimeMin(new DateTime(from.toInstant().toEpochMilli()))
                    .setTimeMax(new DateTime(to.toInstant().toEpochMilli()))
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .setFields(EVENT_FIELDS)   // ← explicitly include extendedProperties
                    .execute();

            List<Event> items = events.getItems();
            if (items == null || items.isEmpty()) return Collections.emptyList();

            return items.stream()
                    .filter(e -> {
                        AppointmentStatus status = parseStatus(e);
                        boolean pass = filterStatus == null || status == filterStatus;
                        if (!pass) {
                            log.debug("Skipping event '{}' — status={} (filter={})",
                                    e.getSummary(), status, filterStatus);
                        }
                        return pass;
                    })
                    .map(this::toAppointment)
                    .toList();

        } catch (IOException e) {
            log.error("Failed to fetch calendar events", e);
            return Collections.emptyList();
        }
    }

    // ── Extended properties ────────────────────────────────────────────────────

    public Map<String, String> getExtendedProperties(String eventId) {
        try {
            Event event = calendar.events().get(props.getCalendarId(), eventId).execute();
            if (event.getExtendedProperties() == null) return Collections.emptyMap();
            Map<String, String> privateProps = event.getExtendedProperties().getPrivate();
            return privateProps != null ? privateProps : Collections.emptyMap();
        } catch (IOException e) {
            log.error("Failed to get extended properties for event {}", eventId, e);
            return Collections.emptyMap();
        }
    }

    public void updateStatus(String eventId, AppointmentStatus status) {
        updateExtendedProperties(eventId, Map.of(STATUS_KEY, status.name()));
    }

    public void updateStatusWithMetadata(String eventId, AppointmentStatus status, Map<String, String> extra) {
        Map<String, String> props = new HashMap<>(extra);
        props.put(STATUS_KEY, status.name());
        updateExtendedProperties(eventId, props);
    }

    public void updateExtendedProperties(String eventId, Map<String, String> newProps) {
        try {
            Event event = calendar.events().get(props.getCalendarId(), eventId).execute();

            Event.ExtendedProperties ext = event.getExtendedProperties();
            if (ext == null) ext = new Event.ExtendedProperties();

            Map<String, String> privateProps = ext.getPrivate();
            if (privateProps == null) privateProps = new HashMap<>();

            privateProps.putAll(newProps);
            ext.setPrivate(privateProps);
            event.setExtendedProperties(ext);

            // Set calendar event color when the status is being updated
            if (newProps.containsKey(STATUS_KEY)) {
                String colorId = colorIdForStatus(newProps.get(STATUS_KEY));
                if (colorId != null) {
                    event.setColorId(colorId);
                }
            }

            calendar.events().patch(props.getCalendarId(), eventId, event).execute();
            log.info("Extended properties updated for event {}: {}", eventId, newProps);

        } catch (IOException e) {
            log.error("Failed to update extended properties for event {}", eventId, e);
        }
    }

    // ── Color mapping ──────────────────────────────────────────────────────────
    // Google Calendar color IDs: 2=Sage(green), 5=Banana(yellow), 11=Tomato(red)

    private String colorIdForStatus(String statusName) {
        if (statusName == null) return null;
        return switch (statusName) {
            case "CONFIRMED"      -> "2";  // Sage — green
            case "NO_ANSWER"      -> "5";  // Banana — yellow
            case "CANCELLED"      -> "11"; // Tomato — red
            default               -> null; // leave color unchanged for other statuses
        };
    }

    // ── Mapping ────────────────────────────────────────────────────────────────

    private Appointment toAppointment(Event event) {
        return new Appointment(
                event.getId(),
                event.getSummary(),
                parseStartTime(event),
                parsePhoneNumber(event.getDescription()),
                parseStatus(event),
                isAllDay(event)
        );
    }

    /** Returns true when the event has a date-only start (i.e. it is an all-day event). */
    private boolean isAllDay(Event event) {
        return event.getStart() != null && event.getStart().getDateTime() == null;
    }

    private ZonedDateTime parseStartTime(Event event) {
        var start = event.getStart();
        if (start.getDateTime() != null) {
            return Instant.ofEpochMilli(start.getDateTime().getValue())
                    .atZone(zoneId);
        }
        return LocalDate.parse(start.getDate().toStringRfc3339())
                .atStartOfDay(zoneId);
    }

    private String parsePhoneNumber(String description) {
        if (description == null || description.isBlank()) return null;
        Matcher m = PHONE_PATTERN.matcher(description);
        return m.find() ? m.group(1).trim() : null;
    }

    private AppointmentStatus parseStatus(Event event) {
        if (event.getExtendedProperties() == null) return AppointmentStatus.PENDING;
        Map<String, String> privateProps = event.getExtendedProperties().getPrivate();
        if (privateProps == null) return AppointmentStatus.PENDING;
        String raw = privateProps.get(STATUS_KEY);
        if (raw == null) return AppointmentStatus.PENDING;
        try {
            return AppointmentStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return AppointmentStatus.PENDING;
        }
    }
}
