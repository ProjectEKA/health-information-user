package in.org.projecteka.hiu.dataflow;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Utils {
    public static LocalDateTime toDate(String date) {
        String pattern = "yyyy-MM-dd['T'HH[:mm][:ss][.SSS]]";
        return LocalDateTime.parse(date, DateTimeFormatter.ofPattern(pattern));
    }
}