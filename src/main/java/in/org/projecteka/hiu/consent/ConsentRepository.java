package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.consent.model.ConsentArtefact;
import in.org.projecteka.hiu.consent.model.ConsentRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import lombok.SneakyThrows;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hiu.ClientError.dbOperationFailure;

public class ConsentRepository {
    private final String INSERT_CONSENT_ARTEFACT_QUERY = "INSERT INTO " +
            "consent_artefact (consent_artefact) VALUES ($1)";
    private PgPool dbClient;

    public ConsentRepository(PgPool pgPool) {
        this.dbClient = pgPool;
    }

    @SneakyThrows
    public Mono<Void> insert(ConsentRequest consentRequest) {
        final String INSERT_CONSENT_REQUEST_QUERY = "INSERT INTO " +
                "consent_request (consent_request) VALUES ($1)";
        return Mono.create(monoSink ->
                dbClient.preparedQuery(
                        INSERT_CONSENT_REQUEST_QUERY,
                        Tuple.of(JsonObject.mapFrom(consentRequest)),
                        handler -> {
                            if (handler.failed())
                                monoSink.error(new Exception("Failed to insert to consent request"));
                            else
                                monoSink.success();
                        }
                )
        );
    }

    @SneakyThrows
    public Mono<ConsentRequest> get(String consentRequestId) {
        final String SELECT_CONSENT_REQUEST_QUERY = "SELECT consent_request " +
                "FROM consent_request WHERE consent_request ->> 'id' = $1";
        return Mono.create(monoSink ->
                dbClient.preparedQuery(
                        SELECT_CONSENT_REQUEST_QUERY,
                        Tuple.of(consentRequestId),
                        handler -> {
                            if (handler.failed())
                                monoSink.error(dbOperationFailure("Failed to fetch consent request"));
                            else {
                                RowSet<Row> rows = handler.result();
                                ConsentRequest consentRequest = null;
                                for (Row row : rows) {
                                    JsonObject consentRequestJson = (JsonObject) row.getValue("consent_request");
                                    consentRequest = consentRequestJson.mapTo(ConsentRequest.class);
                                }
                                monoSink.success(consentRequest);
                            }
                        }
                )
        );
    }

    public Mono<Void> insertConsentArtefact(ConsentArtefact consentArtefact) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(
                        INSERT_CONSENT_ARTEFACT_QUERY,
                        Tuple.of(JsonObject.mapFrom(consentArtefact)),
                        handler -> {
                            if (handler.failed())
                                monoSink.error(dbOperationFailure("Failed to fetch consent request"));
                            else
                                monoSink.success();
                        }
                )
        );
    }
}
