package in.org.projecteka.hiu.clients;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConfigurationProperties(prefix = "hiu.accountservice")
@AllArgsConstructor
@ConstructorBinding
@Getter
public class AccountServiceProperties {
        private final boolean usingUnsecureSSL;
        private final String url;
        private final String clientId;
        private final String clientSecret;
        private final String hasAuthUrl;
        private final boolean hasBehindGateway;
}