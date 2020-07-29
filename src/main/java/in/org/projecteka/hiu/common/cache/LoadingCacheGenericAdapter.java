package in.org.projecteka.hiu.common.cache;

import com.google.common.cache.LoadingCache;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

import static reactor.core.publisher.Mono.fromCallable;
import static reactor.core.publisher.Mono.fromRunnable;

@AllArgsConstructor
public class LoadingCacheGenericAdapter<T> implements CacheAdapter<String, T> {
    private final LoadingCache<String, T> loadingCache;
    private final T fallbackValue;

    @Override
    public Mono<T> get(String key) {
        return fromCallable(() -> loadingCache.get(key).equals(fallbackValue) ? null : loadingCache.get(key));
    }

    @Override
    public Mono<Void> put(String key, T value) {
        return fromRunnable(() -> loadingCache.put(key, value));
    }

    @Override
    public Mono<Void> invalidate(String key) {
        return fromRunnable(() -> loadingCache.invalidate(key));
    }

    @Override
    public Mono<Boolean> exists(String key) {
        return fromCallable(() -> loadingCache.getIfPresent(key) != fallbackValue);
    }
}
