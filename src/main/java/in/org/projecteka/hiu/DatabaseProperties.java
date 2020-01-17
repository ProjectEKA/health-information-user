package in.org.projecteka.hiu;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "hiu.database")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@AllArgsConstructor
public class DatabaseProperties {
    private String host;
    private int port;
    private String schema;
    private String user;
    private String password;
    private int poolSize;
}
