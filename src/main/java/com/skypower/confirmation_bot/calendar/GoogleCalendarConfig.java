package com.skypower.confirmation_bot.calendar;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.FileInputStream;
import java.time.ZoneId;
import java.util.List;

@Configuration
public class GoogleCalendarConfig {

    @Bean
    public ZoneId appZoneId(@org.springframework.beans.factory.annotation.Value("${app.timezone:America/Sao_Paulo}") String timezone) {
        return ZoneId.of(timezone);
    }

    @Bean
    @Lazy
    public Calendar googleCalendar(GoogleCalendarProperties props) throws Exception {
        GoogleCredentials credentials = GoogleCredentials
                .fromStream(new FileInputStream(props.getCredentialsPath()))
                .createScoped(List.of(CalendarScopes.CALENDAR));

        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        )
        .setApplicationName(props.getApplicationName())
        .build();
    }
}
