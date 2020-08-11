package in.org.projecteka.hiu.common.heartbeat;

import in.org.projecteka.hiu.common.CacheMethodProperty;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;

import java.util.function.BooleanSupplier;


@AllArgsConstructor
public class CacheHealth {
    private final CacheMethodProperty cacheMethodProperty;
    private final ReactiveRedisConnectionFactory redisConnectionFactory;
    private static final Logger logger = LoggerFactory.getLogger(CacheHealth.class);
    public static final String GUAVA = "guava";

    public boolean isUp() {
        BooleanSupplier checkRedis = () -> {
            try (ReactiveRedisConnection ignored = redisConnectionFactory.getReactiveConnection()) {
                return true;
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return false;
            }
        };
        return cacheMethodProperty.getMethodName().equalsIgnoreCase(GUAVA) || checkRedis.getAsBoolean();
    }
}
