package in.org.projecteka.hiu.config;

import in.org.projecteka.hiu.ConsentManagerServiceProperties;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@AllArgsConstructor
public class ConfigController {
    private final ConsentManagerServiceProperties consentManagerServiceProperties   ;

    @GetMapping("/config")
    public Mono<ConfigResponse> getConfig() {
        return Mono.create(monoSink -> {
            var consentManagerConfig = new ConfigResponse.ConsentManagerConfig(consentManagerServiceProperties.getSuffix());
            monoSink.success(new ConfigResponse(new ConfigResponse.ConsentManagerConfig[]{consentManagerConfig}));
        });
    }
}
