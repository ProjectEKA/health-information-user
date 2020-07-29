package in.org.projecteka.hiu.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConfigurationProperties(prefix = "hiu.cache-method")
@Getter
@AllArgsConstructor
@ConstructorBinding
public class CacheMethodProperty {
    private final String methodName;
}
