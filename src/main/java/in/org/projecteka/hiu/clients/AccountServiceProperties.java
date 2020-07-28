package in.org.projecteka.hiu.clients;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

import java.util.List;

@ConfigurationProperties(prefix = "hiu.accountservice")
@AllArgsConstructor
@ConstructorBinding
@Getter
public class AccountServiceProperties {
        private final boolean usingUnsecureSSL;
        private final String url;
        private final List<String> identifiers;
        private final int expiryInMinutes;

}
