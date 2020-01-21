package in.org.projecteka.hiu.consent;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.org.projecteka.hiu.consent.model.ConsentRequest;
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
    public Mono<Void> insert(ConsentRequest consentRequest) {
        final String INSERT_CONSENT_REQUEST_QUERY = "INSERT INTO " +
                "consent_request (consent_request) VALUES ($1)";
        final String consentRequestData =
                new ObjectMapper().writeValueAsString(consentRequest);
        return Mono.create(monoSink ->
                dbClient.preparedQuery(
                        INSERT_CONSENT_REQUEST_QUERY,
                        Tuple.of(consentRequestData),
                        handler -> {
                            if (handler.failed())
                                monoSink.error(new Exception("Failed to insert to consent request"));
                            else
                                monoSink.success();
                        }
                )
        );
    }
}
