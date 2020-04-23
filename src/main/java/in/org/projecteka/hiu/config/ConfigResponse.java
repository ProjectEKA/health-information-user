package in.org.projecteka.hiu.config;

import lombok.Value;

@Value
class ConfigResponse {
    ConsentManagerConfig[] consentManagers;

    @Value
    static class ConsentManagerConfig {
        String userIdSuffix;
    }
}
