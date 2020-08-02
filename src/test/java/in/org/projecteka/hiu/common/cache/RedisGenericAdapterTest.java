package in.org.projecteka.hiu.common.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.common.TestBuilders.string;
import static java.time.Duration.ofMinutes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static reactor.core.publisher.Mono.defer;
import static reactor.core.publisher.Mono.error;
import static reactor.core.publisher.Mono.just;
import static reactor.test.StepVerifier.create;

class RedisGenericAdapterTest {

    private static final int EXPIRATION_IN_MINUTES = 5;
    private static final int RETRY = 3;
    @Mock
    ReactiveRedisOperations<String, String> redisOperations;

    @Mock
    ReactiveValueOperations<String, String> valueOperations;

    private RedisGenericAdapter<String> genericAdapter;

    private RedisGenericAdapter<String> retryableAdapter;

    @BeforeEach
    public void init() {
        initMocks(this);
        genericAdapter = new RedisGenericAdapter<>(redisOperations, ofMinutes(EXPIRATION_IN_MINUTES), "with_prefix");
        retryableAdapter = new RedisGenericAdapter<>(redisOperations, ofMinutes(EXPIRATION_IN_MINUTES), "pre", RETRY);
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

    @Test
    void getAfterRetry() {
        var key = string();
        var value = string();
        when(redisOperations.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("pre_" + key)).thenAnswer(new Answer<Mono<String>>() {
            private int numberOfTimesCalled = 0;

            @Override
            public Mono<String> answer(InvocationOnMock invocation) {
                return defer(() -> {
                    if (numberOfTimesCalled++ == RETRY) {
                        return just(value);
                    }
                    return error(new Exception("Connection error"));
                });
            }
        });

        create(retryableAdapter.get(key))
                .assertNext(actualValue -> assertThat(actualValue).isEqualTo(value))
                .verifyComplete();
    }

    @Test
    void existsWithoutPrefixAfterRetry() {
        var key = string();
        when(redisOperations.hasKey("pre_" + key)).thenAnswer(new Answer<Mono<Boolean>>() {
            private int numberOfTimesCalled = 0;

            @Override
            public Mono<Boolean> answer(InvocationOnMock invocation) {
                return defer(() -> {
                    if (numberOfTimesCalled++ == RETRY) {
                        return just(true);
                    }
                    return error(new Exception("Connection error"));
                });
            }
        });

        create(retryableAdapter.exists(key))
                .assertNext(exist -> assertThat(exist).isTrue())
                .verifyComplete();
    }
}