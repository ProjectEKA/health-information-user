package in.org.projecteka.hiu.common.cache;

import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static java.lang.String.format;
import static java.time.Duration.ofMinutes;

@AllArgsConstructor
public class RedisGenericAdapter<T> implements CacheAdapter<String, T> {
    private final ReactiveRedisOperations<String, T> redisOperations;
    private final Duration expiration;
    private final String prefix;

    private String prefixThe(String key) {
        return StringUtils.hasText(prefix) ? format("%s_%s", prefix, key) : key;
    }

    @Override
    public Mono<T> get(String key) {
        return redisOperations.opsForValue().get(prefixThe(key));
    }

    @Override
    public Mono<Void> put(String key, T value) {
        return redisOperations.opsForValue().set(prefixThe(key), value, expiration).then();
    }

    @Override
    public Mono<Void> invalidate(String key) {
        return redisOperations.expire(prefixThe(key), ofMinutes(0)).then();
    }

    @Override
    public Mono<Boolean> exists(String key) {
        return redisOperations.hasKey(prefixThe(key));
    }
}
