package in.org.projecteka.hiu.clients;

import in.org.projecteka.hiu.ClientError;
import lombok.AllArgsConstructor;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Properties;

import static java.lang.String.format;
import static org.springframework.web.reactive.function.BodyInserters.fromFormData;

@AllArgsConstructor
public class CentralRegistryClient {
    private final WebClient.Builder builder;
    private final Logger logger = Logger.getLogger(CentralRegistryClient.class);

    public Mono<Token> getTokenFor(String clientId, String clientSecret) {
        return builder.build()
                .post()
                .uri("/realms/consent-manager/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(fromFormData(requestWith(clientId, clientSecret)))
                .retrieve()
                .onStatus(HttpStatus::isError, clientResponse -> clientResponse.bodyToMono(Properties.class)
                        .doOnNext(properties -> logger.error(properties.toString()))
                        .thenReturn(ClientError.authenticationFailed()))
                .bodyToMono(Properties.class)
                .map(properties -> new Token(format("Bearer %s", properties.getProperty("access_token"))));
    }

    private MultiValueMap<String, String> requestWith(String clientId, String clientSecret) {
        var formData = new LinkedMultiValueMap<String, String>();
        formData.add("grant_type", "client_credentials");
        formData.add("scope", "openid");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        return formData;
    }
}