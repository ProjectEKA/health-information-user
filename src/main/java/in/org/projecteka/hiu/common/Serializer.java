package in.org.projecteka.hiu.common;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Serializer {

    private static final Logger logger = LoggerFactory.getLogger(Serializer.class);

    private static final ObjectMapper mapper =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Serializer() {

    }

    @SneakyThrows
    public static <T> String from(T data) {
        return mapper.writeValueAsString(data);
    }

    @SneakyThrows
    public static <T> T to(String value, Class<T> type) {
        try {
            return mapper.readValue(value.getBytes(), type);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }
}
