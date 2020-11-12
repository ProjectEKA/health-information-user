package in.org.projecteka.hiu.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@Builder
@ConfigurationProperties(prefix = "hiu.authorization")
@AllArgsConstructor
@ConstructorBinding
@Getter
public class IDPProperties {
    private final String externalIdpCertPath;
    private final String externalIdpClientId;
    private final String externalIdpClientSecret;
    private final String externalIdpAuthURL;
}
