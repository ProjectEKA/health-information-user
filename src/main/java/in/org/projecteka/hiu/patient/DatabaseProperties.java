package in.org.projecteka.hiu.patient;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "hiu.database")
@Data
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
