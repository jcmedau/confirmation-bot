package com.skypower.confirmation_bot;

import com.skypower.confirmation_bot.calendar.GoogleCalendarService;
import com.skypower.confirmation_bot.notification.AppointmentNotificationService;
import com.skypower.confirmation_bot.model.Appointment;
import com.skypower.confirmation_bot.model.AppointmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import com.skypower.confirmation_bot.whatsapp.EvolutionWhatsAppService;

@SpringBootTest
@TestPropertySource(properties = {
        "google.calendar.credentials-path=${user.dir}/src/main/resources/credentials/service-account.json",
        "google.calendar.calendar-id=agenda.larissarosolen@gmail.com",
        "evolution.base-url=http://messages.skypower-aviation.com",
        "app.trigger.key=TriggerSkyPower2026"
})
class GoogleCalendarIntegrationTest {

    @Autowired
    private GoogleCalendarService calendarService;

    @Autowired
    private AppointmentNotificationService notificationService;

    @Autowired
    private EvolutionWhatsAppService whatsAppService;

    @Test
    void fetchTomorrowAppointments() {
        // Uses the same holiday-aware, day-of-week logic as the production scheduler
        LocalDate today   = LocalDate.now();
        List<LocalDate> targets = notificationService.targetDatesForTest(today);
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

        System.out.println();
        System.out.printf("╔══════════════════════════════════════════════════════════╗%n");
        System.out.printf("║  Today: %-49s║%n", today);
        System.out.printf("║  Target dates: %-42s║%n", targets);
        System.out.println("╠══════════════════════════════════════════════════════════╣");

        int total = 0;
        for (LocalDate date : targets) {
            List<Appointment> appointments = calendarService.fetchAllAppointmentsForDay(date);
            System.out.printf("║  ── %s (%d appt(s)) %-27s║%n",
                    date, appointments.size(), "");
            for (Appointment a : appointments) {
                String time  = (a.startTime() != null && !a.allDay()) ? a.startTime().format(timeFmt) : "all-day";
                String name  = a.patientName() != null ? a.patientName() : "(no title)";
                String phone = a.phoneNumber() != null ? a.phoneNumber() : "no phone";
                System.out.printf("║     %s  %-28s  %-10s  [%s]%n",
                        time, truncate(name, 28), truncate(phone, 10), a.status());
                total++;
            }
        }

        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.printf ("║  Total: %-49s║%n", total);
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    /**
     * Sends the real confirmation WhatsApp message to the first PENDING
     * appointment tomorrow, then marks it AWAITING_REPLY in the calendar.
     * Run manually — it fires a live message.
     */
    @Test
    void sendConfirmationToFirstPendingTomorrowAppointment() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Appointment> appointments = calendarService.fetchAllAppointmentsForDay(tomorrow)
                .stream()
                .filter(a -> a.status() == com.skypower.confirmation_bot.model.AppointmentStatus.PENDING)
                .filter(a -> a.phoneNumber() != null)
                .toList();

        if (appointments.isEmpty()) {
            System.out.println("No PENDING appointments with a phone number for tomorrow — nothing to send.");
            return;
        }

        Appointment first = appointments.get(0);
        System.out.printf("%nSending confirmation to: %s  |  phone: %s  |  time: %s%n",
                first.patientName(), first.phoneNumber(), first.startTime());

        notificationService.sendPendingConfirmations();

        System.out.println("Done — check WhatsApp and the calendar status (should be AWAITING_REPLY).");
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    @Test
    void confirmFirstTomorrowAppointment() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Appointment> appointments = calendarService.fetchAllAppointmentsForDay(tomorrow);

        if (appointments.isEmpty()) {
            System.out.println("No appointments found for tomorrow — nothing to confirm.");
            return;
        }

        Appointment first = appointments.get(0);
        System.out.println("Confirming: " + first.patientName() + " at " + first.startTime());

        calendarService.updateStatus(first.eventId(), AppointmentStatus.CONFIRMED);

        System.out.println("Done! Status set to CONFIRMED for event: " + first.eventId());
        System.out.println("Re-run fetchTomorrowAppointments to see the updated status.");
    }

    /**
     * Utility test — resets ALL tomorrow's appointments back to PENDING
     * by clearing the confirmation status, messageSentAt and retryCount
     * extended properties. Run this manually to start a clean cycle.
     */
    @Test
    void resetTomorrowAppointmentsToPending() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Appointment> appointments = calendarService.fetchAllAppointmentsForDay(tomorrow);

        System.out.println();
        System.out.println("Resetting " + appointments.size() + " appointment(s) for " + tomorrow + " → PENDING");

        int reset = 0;
        for (Appointment a : appointments) {
            if (a.status() == AppointmentStatus.PENDING) {
                System.out.println("  SKIP (already PENDING): " + a.patientName());
                continue;
            }

            // Clear all bot-managed properties and set status to PENDING
            calendarService.updateExtendedProperties(a.eventId(), Map.of(
                    GoogleCalendarService.STATUS_KEY,           AppointmentStatus.PENDING.name(),
                    GoogleCalendarService.MESSAGE_SENT_AT_KEY,  "",
                    GoogleCalendarService.RETRY_COUNT_KEY,      ""
            ));

            System.out.println("  RESET: " + a.patientName() + " [was " + a.status() + "]");
            reset++;
        }

        System.out.println();
        System.out.println("Done — " + reset + " appointment(s) reset to PENDING.");
        System.out.println("Run fetchTomorrowAppointments to verify.");
        System.out.println();
    }


    /**
     * Searches the next 30 days for all-day events on the calendar.
     * Useful to verify that the bot correctly detects all-day appointments.
     */
    @Test
    void findAllDayAppointmentsNext30Days() {
        LocalDate today = LocalDate.now();
        LocalDate end   = today.plusDays(30);

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.printf ("║  All-day appointments: %s → %s        ║%n", today, end);
        System.out.println("╠══════════════════════════════════════════════════════════╣");

        var allInRange = calendarService.fetchAllAppointmentsInRange(today, end);
        var allDay = allInRange.stream().filter(a -> a.allDay()).toList();

        if (allDay.isEmpty()) {
            System.out.println("║  No all-day appointments found in the next 30 days.      ║");
        } else {
            for (var a : allDay) {
                String date  = a.startTime() != null ? a.startTime().toLocalDate().toString() : "?";
                String name  = a.patientName() != null ? a.patientName() : "(no title)";
                String phone = a.phoneNumber() != null ? a.phoneNumber() : "no phone";
                System.out.printf("║  %s  %-26s  %-14s  [%s]%n",
                        date, truncate(name, 26), truncate(phone, 14), a.status());
            }
        }

        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.printf ("║  Found: %-49s║%n", allDay.size() + " all-day event(s) out of " + allInRange.size() + " total");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    /**
     * Shows what dates would be targeted for confirmations TODAY, taking holidays
     * into account. Useful to verify the holiday-expansion logic without sending
     * any messages. Run manually.
     */
    @Test
    void showTodayTargetDates() {
        // We call the public entry point which internally runs buildTargetDates()
        // The easiest observable side-effect is the log output, but we can also
        // check which dates have PENDING appointments after the expansion.
        LocalDate today = LocalDate.now();

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.printf ("║  Holiday-aware target dates from %-23s║%n", today);
        System.out.println("╠══════════════════════════════════════════════════════════╣");

        // Replicate the buildTargetDates logic here so we can print the result
        // (the real method is private — we read the log or just observe the dates).
        // We do a 7-day scan for all-day Feriado/Holiday events to report which
        // dates are holidays.
        var next7 = calendarService.fetchAllAppointmentsInRange(today, today.plusDays(7));
        var holidays = next7.stream()
                .filter(a -> a.allDay())
                .filter(a -> a.patientName() != null &&
                             (a.patientName().equalsIgnoreCase("feriado") ||
                              a.patientName().equalsIgnoreCase("holiday")))
                .map(a -> a.startTime().toLocalDate())
                .distinct()
                .sorted()
                .toList();

        if (holidays.isEmpty()) {
            System.out.println("║  No holidays found in the next 7 days.                  ║");
        } else {
            for (var h : holidays) {
                System.out.printf("║  🏖  Holiday detected: %-34s║%n", h);
            }
        }

        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println("  (Run sendConfirmationToFirstPendingTomorrowAppointment to");
        System.out.println("   exercise the full holiday-aware flow with real messages.)");
        System.out.println();
    }

    @Test
    void sendWhatsAppMessageDirectly() {
        // Sends a real WhatsApp message via the Evolution API configured in application.properties.
        // Change the number below to your own before running.
        String targetPhone = "5519991278903"; // <- replace with a real number (country code + DDD + number)
        String message     = "Teste de integracao — confirmation-bot esta funcionando!";

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║        WHATSAPP DIRECT SEND TEST                        ║");
        System.out.printf ("║  To   : %-48s║%n", targetPhone);
        System.out.println("╠══════════════════════════════════════════════════════════╣");

        boolean sent = whatsAppService.sendMessage(targetPhone, message);

        if (sent) {
            System.out.println("║  OK  Message delivered successfully.                    ║");
        } else {
            System.out.println("║  FAIL  Delivery failed — check logs for details.        ║");
        }
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();

        org.junit.jupiter.api.Assertions.assertTrue(sent, "WhatsApp message should be sent successfully");
    }


    /**
     * Sends confirmation messages for Saturday, Sunday AND Monday.
     * Run on a Friday — the service's day-of-week logic targets all three days.
     * ⚠️  This fires real WhatsApp messages and marks appointments AWAITING_REPLY.
     */
    @Test
    void sendFridayConfirmations() {
        LocalDate today   = LocalDate.now();
        List<LocalDate> targets = notificationService.targetDatesForTest(today);

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.printf ("║  FRIDAY SWEEP — today: %-33s║%n", today);
        System.out.printf ("║  Target dates: %-42s║%n", targets);
        System.out.println("╠══════════════════════════════════════════════════════════╣");

        int totalPending = 0;
        for (LocalDate date : targets) {
            long pending = calendarService.fetchAllAppointmentsForDay(date).stream()
                    .filter(a -> a.status() == com.skypower.confirmation_bot.model.AppointmentStatus.PENDING)
                    .filter(a -> a.phoneNumber() != null && !a.phoneNumber().isBlank())
                    .count();
            System.out.printf("║  %s  →  %d pending appointment(s)%-20s║%n", date, pending, "");
            totalPending += pending;
        }

        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.printf ("║  Sending %d message(s) now...%-29s║%n", totalPending, "");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();

        notificationService.sendPendingConfirmations();

        System.out.println();
        System.out.println("Done — check WhatsApp and the calendar (statuses → AWAITING_REPLY).");
    }


    /**
     * Shows the status of every appointment for today, tomorrow, Sunday and Monday.
     * Safe to run — read-only, no messages sent, no calendar changes.
     */
    @Test
    void showWeekendAppointmentStatus() {
        LocalDate today  = LocalDate.now();
        List<LocalDate> dates = List.of(today, today.plusDays(1), today.plusDays(2), today.plusDays(3));
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.printf ("║  APPOINTMENT STATUS — %s (Fri) → %s (Mon)  ║%n", today, today.plusDays(3));
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");

        int grandTotal = 0;
        for (LocalDate date : dates) {
            List<Appointment> appts = calendarService.fetchAllAppointmentsForDay(date);
            System.out.printf("║  ── %s (%s) — %d appointment(s) %s║%n",
                    date, date.getDayOfWeek(), appts.size(),
                    " ".repeat(Math.max(0, 28 - String.valueOf(appts.size()).length() - date.getDayOfWeek().toString().length())));
            if (appts.isEmpty()) {
                System.out.println("║     (none)                                                           ║");
            }
            for (Appointment a : appts) {
                String time   = (a.startTime() != null && !a.allDay()) ? a.startTime().format(timeFmt) : "all-day";
                String name   = truncate(a.patientName() != null ? a.patientName() : "(no title)", 24);
                String phone  = truncate(a.phoneNumber() != null ? a.phoneNumber() : "no phone", 14);
                String status = a.status() != null ? a.status().name() : "UNKNOWN";
                System.out.printf("║     %s  %-24s  %-14s  %-14s║%n", time, name, phone, status);
                grandTotal++;
            }
        }

        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        System.out.printf ("║  Total: %-61s║%n", grandTotal + " appointment(s)");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }
}
