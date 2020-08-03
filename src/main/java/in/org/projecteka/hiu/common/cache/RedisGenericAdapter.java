package in.org.projecteka.hiu.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

import static java.lang.String.format;
import static java.time.Duration.ofMinutes;
import static java.time.LocalDateTime.now;
import static reactor.core.publisher.Mono.defer;

public class RedisGenericAdapter<T> implements CacheAdapter<String, T> {
    private static final Logger logger = LoggerFactory.getLogger(RedisGenericAdapter.class);
    public static final String RETRIED_AT = "retried at {}";
    private final ReactiveRedisOperations<String, T> redisOperations;
    private final Duration expiration;
    private final String prefix;
    private final int retry;

    public RedisGenericAdapter(ReactiveRedisOperations<String, T> redisOperations,
                               Duration expiration,
                               String prefix,
                               int retry) {
        this.redisOperations = redisOperations;
        this.expiration = expiration;
        this.prefix = prefix;
        this.retry = retry;
    }

    public RedisGenericAdapter(ReactiveRedisOperations<String, T> redisOperations,
                               Duration expiration,
                               String prefix) {
        this.redisOperations = redisOperations;
        this.expiration = expiration;
        this.prefix = prefix;
        retry = 0;
    }

    private String prefixThe(String key) {
        return StringUtils.hasText(prefix) ? format("%s_%s", prefix, key) : key;
    }

    private <U> Mono<U> retryable(Mono<U> producer) {
        return defer(() -> producer)
                .doOnError(error -> logger.error(error.getMessage(), error))
                .retryWhen(Retry
                        .backoff(retry, Duration.ofMillis(100)).jitter(0d)
                        .doAfterRetry(rs -> logger.error(RETRIED_AT, now()))
                        .onRetryExhaustedThrow((spec, rs) -> rs.failure()));
    }

    @Override
    public Mono<T> get(String key) {
        return retryable(redisOperations.opsForValue().get(prefixThe(key)));
    }

    @Override
    public Mono<Void> put(String key, T value) {
        return retryable(redisOperations.opsForValue().set(prefixThe(key), value, expiration)).then();
    }

    @Override
    public Mono<Void> invalidate(String key) {
        return retryable(redisOperations.expire(prefixThe(key), ofMinutes(0))).then();
    }

    @Override
    public Mono<Boolean> exists(String key) {
        return retryable(redisOperations.hasKey(prefixThe(key)));
    }
}
