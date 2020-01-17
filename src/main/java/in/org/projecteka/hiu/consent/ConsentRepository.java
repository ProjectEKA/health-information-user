package in.org.projecteka.hiu.consent;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.consent.model.ConsentRequestDetails;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import lombok.SneakyThrows;
import reactor.core.publisher.Mono;

public class ConsentRepository {
    private PgPool dbClient;

    public ConsentRepository(PgPool pgPool) {
        this.dbClient = pgPool;
    }

    @SneakyThrows
    public Mono<Void> insertToConsentRequest(
            String consentRequestId,
            ConsentRequestDetails consentRequestDetails) {
        final String INSERT_CONSENT_REQUEST_QUERY = "INSERT INTO consent_request (consent_request_id, consent_request_details) VALUES ($1, $2)";
        final String consentDetails = new ObjectMapper().writeValueAsString(consentRequestDetails);
        return Mono.create(monoSink ->
                dbClient.preparedQuery(
                        INSERT_CONSENT_REQUEST_QUERY,
                        Tuple.of(consentRequestId, consentDetails),
                        handler -> {
                            if (handler.failed())
                                monoSink.error(new Exception("Failed to insert to consent request"));
                            else
                                monoSink.success();
                        })
        );
    }

}
