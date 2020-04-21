package in.org.projecteka.hiu;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;


@Configuration
@ConfigurationProperties(prefix = "hiu.consentmanager")
@Data
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@AllArgsConstructor
public class ConsentManagerServiceProperties {
    private String url;
    private String suffixes;

    public List<String> getSuffixes(){
       return Arrays.asList(suffixes.split(" "));
    }
}
