package in.org.projecteka.hiu.common.cache;

import reactor.core.publisher.Mono;

public interface CacheAdapter<K, V> {
    Mono<V> get(K key);

    Mono<Void> put(K key, V value);

    Mono<Void> invalidate(K key);

    Mono<Boolean> exists(K key);
}
