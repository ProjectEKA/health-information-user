package in.org.projecteka.hiu.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConditionalOnProperty(value = "hiu.cache-method", havingValue = "redis")
@ConfigurationProperties(prefix = "hiu.redis")
@Getter
@AllArgsConstructor
@ConstructorBinding
public class RedisOptions {
    private final String host;
    private final int port;
    private final String password;
    private final boolean keepAliveEnabled;
    private final int retry;
    private final boolean useDefaultClientConfig;

    public boolean useDefaultClientConfig() {
        return useDefaultClientConfig;
    }
}
