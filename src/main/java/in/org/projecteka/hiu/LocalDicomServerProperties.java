package in.org.projecteka.hiu;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "hiu.dicomserver")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocalDicomServerProperties {
    private String url;
    private String user;
    private String password;
}
