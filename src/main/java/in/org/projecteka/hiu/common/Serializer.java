package in.org.projecteka.hiu.common;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;

public final class Serializer {

    private static final Logger logger = LoggerFactory.getLogger(Serializer.class);

    private static final ObjectMapper mapper =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .configure(WRITE_DATES_AS_TIMESTAMPS, false);

    private Serializer() {

    }

    @SneakyThrows
    public static <T> String from(T data) {
        try {
            return mapper.writeValueAsString(data);
        } catch (IOException e) {
            logger.error("Can not serialize data", e);
            return null;
        }
    }

    @SneakyThrows
    public static <T> T to(String value, Class<T> type) {
        try {
            return mapper.readValue(value.getBytes(), type);
        } catch (IOException e) {
            logger.error("Can not deserialize data", e);
            return null;
        }
    }

    public static <T> Optional<T> to(byte[] source, Class<T> type) {
        try {
            return ofNullable(mapper.readValue(source, type));
        } catch (IOException e) {
            logger.error("Can not deserialize data", e);
            return empty();
        }
    }
}
