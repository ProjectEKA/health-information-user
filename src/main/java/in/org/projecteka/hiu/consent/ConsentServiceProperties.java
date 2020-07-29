package in.org.projecteka.hiu.consent;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "hiu.consentservice")
@Data
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@AllArgsConstructor
public class ConsentServiceProperties {
    private int consentRequestFromYears;
    private int consentExpiryInMonths;
    private long defaultPageSize;
    private int consentRequestDelay;
}
