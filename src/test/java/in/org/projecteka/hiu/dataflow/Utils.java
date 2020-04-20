package in.org.projecteka.hiu.dataflow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Date;

public class Utils {
    public static Date toDate(String date) {
        ZoneId utc = ZoneId.of("UTC");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd['T'HH:mm[:ss][.SSSX][X]]")
                .withZone(utc);

        TemporalAccessor temporalAccessor = formatter.parseBest(date, ZonedDateTime::from, LocalDateTime::from, LocalDate::from);
        if (temporalAccessor instanceof ZonedDateTime)
            return Date.from(((ZonedDateTime) temporalAccessor).toInstant());
        if (temporalAccessor instanceof LocalDateTime)
            return Date.from(((LocalDateTime) temporalAccessor).atZone(utc).toInstant());
        return Date.from(((LocalDate) temporalAccessor).atStartOfDay(utc).toInstant());
    }
}