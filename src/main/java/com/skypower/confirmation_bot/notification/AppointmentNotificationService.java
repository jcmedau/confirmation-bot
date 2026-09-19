package com.skypower.confirmation_bot.notification;

import com.skypower.confirmation_bot.calendar.GoogleCalendarService;
import com.skypower.confirmation_bot.model.Appointment;
import com.skypower.confirmation_bot.model.AppointmentStatus;
import com.skypower.confirmation_bot.whatsapp.EvolutionWhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class AppointmentNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    /** Pause between consecutive outbound WhatsApp messages to avoid rate-limiting. */
    private static final long INTER_MESSAGE_DELAY_MS = 3_000;

    private final GoogleCalendarService calendarService;
    private final EvolutionWhatsAppService whatsAppService;
    private final NotificationProperties notificationProps;
    private final MessageTemplates messageTemplates;
    private final ZoneId zoneId;

    public AppointmentNotificationService(GoogleCalendarService calendarService,
                                          EvolutionWhatsAppService whatsAppService,
                                          NotificationProperties notificationProps,
                                          MessageTemplates messageTemplates,
                                          ZoneId appZoneId) {
        this.calendarService    = calendarService;
        this.whatsAppService    = whatsAppService;
        this.notificationProps  = notificationProps;
        this.messageTemplates   = messageTemplates;
        this.zoneId             = appZoneId;
    }

    // ── Send confirmations ─────────────────────────────────────────────────────

    public void sendPendingConfirmations() {
        List<LocalDate> targets = buildTargetDates(LocalDate.now(zoneId));
        log.info("Processing pending confirmations for target dates: {}", targets);

        List<Appointment> allPending = targets.stream()
                .flatMap(date -> calendarService.fetchAllAppointmentsForDay(date).stream())
                .filter(a -> a.status() == AppointmentStatus.PENDING)
                .toList();

        // Log and skip appointments that have no phone number registered
        allPending.stream()
                .filter(a -> a.phoneNumber() == null || a.phoneNumber().isBlank())
                .forEach(a -> log.info("Skipping '{}' — no phone number on file", a.patientName()));

        List<Appointment> pending = allPending.stream()
                .filter(a -> a.phoneNumber() != null && !a.phoneNumber().isBlank())
                .toList();

        log.info("Found {} pending appointment(s) with phone numbers ({} skipped — no phone)",
                pending.size(), allPending.size() - pending.size());

        for (Appointment a : pending) {
            String message = format(messageTemplates.getConfirmation(), a);
            boolean sent = whatsAppService.sendMessage(a.phoneNumber(), message);

            if (sent) {
                Map<String, String> meta = new HashMap<>();
                meta.put(GoogleCalendarService.MESSAGE_SENT_AT_KEY, String.valueOf(System.currentTimeMillis()));
                meta.put(GoogleCalendarService.RETRY_COUNT_KEY, "0");
                calendarService.updateStatusWithMetadata(a.eventId(), AppointmentStatus.AWAITING_REPLY, meta);
                log.info("Confirmation sent → {} ({})", a.patientName(), a.phoneNumber());
            } else {
                log.warn("Skipping calendar update for {} — message delivery failed", a.patientName());
            }

            // Brief pause so we don't flood the WhatsApp gateway
            try {
                Thread.sleep(INTER_MESSAGE_DELAY_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Confirmation loop interrupted after sending to {}", a.patientName());
                break;
            }
        }
    }

    // ── Target date calculation (holiday-aware) ───────────────────────────────

    /**
     * Builds the list of dates whose appointments should be confirmed today.
     * Starts from the day-of-week defaults and expands iteratively: if any
     * target date is a holiday (all-day "Feriado" or "Holiday" event), the
     * dates that holiday would have covered are added to the set.
     * This handles back-to-back holidays automatically.
     */
    /** Package-visible for integration tests. */
    public List<LocalDate> targetDatesForTest(LocalDate today) { return buildTargetDates(today); }

    private List<LocalDate> buildTargetDates(LocalDate today) {
        Set<LocalDate> visited  = new LinkedHashSet<>();
        Set<LocalDate> toExpand = new LinkedHashSet<>(defaultTargetDates(today));

        while (!toExpand.isEmpty()) {
            LocalDate date = toExpand.iterator().next();
            toExpand.remove(date);

            if (!visited.add(date)) continue; // already processed

            if (isHoliday(date)) {
                log.info("Holiday detected on {} — expanding target dates", date);
                for (LocalDate extra : defaultTargetDates(date)) {
                    if (!visited.contains(extra)) {
                        toExpand.add(extra);
                    }
                }
            }
        }

        List<LocalDate> result = new ArrayList<>(visited);
        result.sort(LocalDate::compareTo);
        log.info("Target dates for today ({}): {}", today, result);
        return result;
    }

    /**
     * Pure day-of-week rule — no calendar calls.
     * <ul>
     *   <li>Mon–Thu, Sun → [tomorrow]</li>
     *   <li>Friday        → [Sat, Sun, Mon]</li>
     *   <li>Saturday      → [Sun, Mon]</li>
     * </ul>
     */
    private List<LocalDate> defaultTargetDates(LocalDate today) {
        return switch (today.getDayOfWeek()) {
            case FRIDAY   -> List.of(today.plusDays(1), today.plusDays(2), today.plusDays(3));
            case SATURDAY -> List.of(today.plusDays(1), today.plusDays(2));
            default       -> List.of(today.plusDays(1)); // Mon, Tue, Wed, Thu, Sun
        };
    }

    /**
     * Returns true if the given date has an all-day event titled "Feriado",
     * "feriado", "Holiday", or "holiday" on the calendar.
     */
    private boolean isHoliday(LocalDate date) {
        return calendarService.fetchAllAppointmentsForDay(date).stream()
                .filter(Appointment::allDay)
                .anyMatch(a -> {
                    String name = a.patientName();
                    return name != null &&
                           (name.equalsIgnoreCase("feriado") || name.equalsIgnoreCase("holiday"));
                });
    }

    // ── Handle patient reply ───────────────────────────────────────────────────

    public void handleReply(String fromPhone, String messageBody) {
        String normalizedPhone = normalizePhone(fromPhone);
        String reply = normalizeReply(messageBody);
        log.info("Reply received from {}: '{}' (normalized: '{}')", normalizedPhone, messageBody.trim(), reply);

        Optional<Appointment> match = calendarService.fetchAwaitingReplyAppointments()
                .stream()
                .filter(a -> a.phoneNumber() != null)
                .filter(a -> normalizePhone(a.phoneNumber()).equals(normalizedPhone))
                .findFirst();

        if (match.isEmpty()) {
            log.warn("No AWAITING_REPLY appointment found for phone {}", normalizedPhone);
            return;
        }

        Appointment a = match.get();

        if (isConfirm(reply)) {
            calendarService.updateStatus(a.eventId(), AppointmentStatus.CONFIRMED);
            whatsAppService.sendMessage(a.phoneNumber(), format(messageTemplates.getThanks(), a));
            log.info("Appointment CONFIRMED for {}", a.patientName());
        } else if (isReschedule(reply)) {
            calendarService.updateStatus(a.eventId(), AppointmentStatus.RESCHEDULED);
            whatsAppService.sendMessage(a.phoneNumber(), format(messageTemplates.getReschedule(), a));
            log.info("Appointment RESCHEDULED for {}", a.patientName());
        } else if (isCancel(reply)) {
            calendarService.updateStatus(a.eventId(), AppointmentStatus.CANCELLED);
            whatsAppService.sendMessage(a.phoneNumber(), format(messageTemplates.getCancellation(), a));
            log.info("Appointment CANCELLED for {}", a.patientName());
        } else {
            log.info("Unrecognized reply '{}' from {} — sending invalid-option message", reply, normalizedPhone);
            whatsAppService.sendMessage(a.phoneNumber(), messageTemplates.getInvalidOption());
        }
    }

    // ── Retry unanswered ───────────────────────────────────────────────────────

    public void retryUnanswered() {
        log.info("Checking for unanswered appointments...");
        long waitingMillis = Duration.ofHours(notificationProps.getWaitingHours()).toMillis();

        List<Appointment> awaiting = calendarService.fetchAwaitingReplyAppointments();

        for (Appointment a : awaiting) {
            Map<String, String> extProps = calendarService.getExtendedProperties(a.eventId());
            long sentAt     = parseLong(extProps.get(GoogleCalendarService.MESSAGE_SENT_AT_KEY));
            int  retryCount = parseInt(extProps.get(GoogleCalendarService.RETRY_COUNT_KEY));
            long elapsed    = System.currentTimeMillis() - sentAt;

            if (elapsed < waitingMillis) continue;

            if (retryCount == 0) {
                log.info("Retrying unanswered appointment for {} ({})", a.patientName(), a.phoneNumber());
                boolean retrySent = whatsAppService.sendMessage(a.phoneNumber(), format(messageTemplates.getNoAnswerRetry(), a));

                if (retrySent) {
                    Map<String, String> meta = new HashMap<>();
                    meta.put(GoogleCalendarService.MESSAGE_SENT_AT_KEY, String.valueOf(System.currentTimeMillis()));
                    meta.put(GoogleCalendarService.RETRY_COUNT_KEY, "1");
                    calendarService.updateStatusWithMetadata(a.eventId(), AppointmentStatus.AWAITING_REPLY, meta);
                } else {
                    log.warn("Retry message delivery failed for {} — keeping AWAITING_REPLY", a.patientName());
                }

            } else {  // retryCount >= 1
                log.info("No answer after retry for {} — marking NO_ANSWER", a.patientName());
                calendarService.updateStatus(a.eventId(), AppointmentStatus.NO_ANSWER);
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String format(String template, Appointment a) {
        if (template == null) return "";
        String name = a.patientName() != null ? a.patientName() : "Paciente";
        // Convert to configured timezone so messages always show local time (e.g. America/Sao_Paulo)
        var localTime = a.startTime() != null ? a.startTime().withZoneSameInstant(zoneId) : null;
        String date = localTime != null ? localTime.format(DATE_FMT) : "";
        // All-day events have no specific time — omit the hour placeholder
        String time = (!a.allDay() && localTime != null) ? localTime.format(TIME_FMT) : "dia todo";
        return template
                .replace("{nome}", name)
                .replace("{data}", date)
                .replace("{hora}", time);
    }

    // ── Reply normalization & matching ────────────────────────────────────────

    /** Strips special characters and lowercases the raw reply for comparison. */
    private String normalizeReply(String raw) {
        if (raw == null) return "";
        return raw.replace(String.valueOf((char) 34), "")
                  .replaceAll("[!@#$%^&*():';/.,<>?`~=_+\\[\\]\\\\{}|()\\-]", "")
                  .trim()
                  .toLowerCase();
    }

    private boolean isConfirm(String reply) {
        return reply.equals("1")
            || reply.equals("confirmado")
            || reply.equals("confirmar")
            || reply.equals("confirmo");
    }

    private boolean isReschedule(String reply) {
        return reply.equals("2")
            || reply.equals("reagendar")
            || reply.equals("reagendado")
            || reply.equals("reagendo");
    }

    private boolean isCancel(String reply) {
        return reply.equals("3")
            || reply.equals("cancelar")
            || reply.equals("cancelado")
            || reply.equals("cancelo");
    }

    private String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("[^\\d]", "");
    }

    private long parseLong(String value) {
        try { return Long.parseLong(value); } catch (Exception e) { return 0L; }
    }

    private int parseInt(String value) {
        try { return Integer.parseInt(value); } catch (Exception e) { return 0; }
    }
}
