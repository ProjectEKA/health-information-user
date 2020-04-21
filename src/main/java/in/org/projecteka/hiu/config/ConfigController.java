package in.org.projecteka.hiu.config;

import in.org.projecteka.hiu.ConsentManagerServiceProperties;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@AllArgsConstructor
public class ConfigController {
    private final ConsentManagerServiceProperties consentManagerServiceProperties;

    @GetMapping("/config")
    public Mono<ConfigResponse> getConfig() {
        return Mono.create(monoSink -> {
            var userIdSuffixes = consentManagerServiceProperties.getSuffixes();
            var consentManagersConfig = userIdSuffixes.stream()
                    .map(ConfigResponse.ConsentManagerConfig::new)
                    .toArray(ConfigResponse.ConsentManagerConfig[]::new);
            monoSink.success(new ConfigResponse(consentManagersConfig));
        });
    }
}
