package in.org.projecteka.hiu;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "hiu.gatewayservice")
@Data
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@AllArgsConstructor
public class GatewayProperties {
    private String baseUrl;
    private int requestTimeout;
    private String clientId;
    private String clientSecret;
    private String jwkUrl;
    private int accessTokenExpiryInMinutes;
}
