package in.org.projecteka.hiu.common.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveValueOperations;

import static in.org.projecteka.hiu.common.TestBuilders.string;
import static java.time.Duration.ofMinutes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static reactor.core.publisher.Mono.just;
import static reactor.test.StepVerifier.create;

class RedisGenericAdapterTest {

    public static final int EXPIRATION_IN_MINUTES = 5;
    @Mock
    ReactiveRedisOperations<String, String> redisOperations;

    @Mock
    ReactiveValueOperations<String, String> valueOperations;

    private RedisGenericAdapter<String> genericAdapter;

    @BeforeEach
    public void init() {
        initMocks(this);
        genericAdapter = new RedisGenericAdapter<>(redisOperations, ofMinutes(EXPIRATION_IN_MINUTES), "with_prefix");
    }

    @Test
    void get() {
        var key = string();
        var value = string();
        when(redisOperations.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("with_prefix_" + key)).thenReturn(just(value));

        create(genericAdapter.get(key))
                .assertNext(actualValue -> assertThat(actualValue).isEqualTo(value))
                .verifyComplete();
    }

    @Test
    void put() {
        var key = string();
        var value = string();
        var expiration = ofMinutes(EXPIRATION_IN_MINUTES);
        when(redisOperations.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set("with_prefix_" + key, value, expiration)).thenReturn(just(true));

        create(genericAdapter.put(key, value)).verifyComplete();
    }

    @Test
    void invalidate() {
        String key = string();
        when(redisOperations.expire("with_prefix_" + key, ofMinutes(0))).thenReturn(just(true));

        create(genericAdapter.invalidate(key)).verifyComplete();
    }

    @Test
    void exists() {
        var key = string();
        when(redisOperations.hasKey("with_prefix_" + key)).thenReturn(just(true));

        create(genericAdapter.exists(key))
                .assertNext(exist -> assertThat(exist).isTrue())
                .verifyComplete();
    }

    @Test
    void existsWithoutPrefix() {
        var key = string();
        when(redisOperations.hasKey(key)).thenReturn(just(true));
        var stringAdapter = new RedisGenericAdapter<>(redisOperations, ofMinutes(EXPIRATION_IN_MINUTES), null);

        create(stringAdapter.exists(key))
                .assertNext(exist -> assertThat(exist).isTrue())
                .verifyComplete();
    }
}