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
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

        List<Appointment> appointments = calendarService.fetchAllAppointmentsForDay(tomorrow);

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.printf ("║  Appointments for %-38s║%n", tomorrow);
        System.out.println("╠══════════════════════════════════════════════════════════╣");

        if (appointments.isEmpty()) {
            System.out.println("║  No appointments found.                                  ║");
        } else {
            for (Appointment a : appointments) {
                String time  = a.startTime() != null ? a.startTime().format(timeFmt) : "all-day";
                String name  = a.patientName() != null ? a.patientName() : "(no title)";
                String phone = a.phoneNumber() != null ? a.phoneNumber() : "no phone";
                System.out.printf("║  %s  %-30s  %-12s  [%s]%n",
                        time, truncate(name, 30), truncate(phone, 12), a.status());
            }
        }

        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.printf ("║  Total: %-49s║%n", appointments.size());
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
}
