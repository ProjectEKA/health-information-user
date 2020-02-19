package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.consent.model.ConsentArtefact;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import in.org.projecteka.hiu.consent.model.ConsentRequest;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.stream.StreamSupport;

import static in.org.projecteka.hiu.ClientError.dbOperationFailure;

@AllArgsConstructor
public class ConsentRepository {
    private final String INSERT_CONSENT_ARTEFACT_QUERY = "INSERT INTO " +
            "consent_artefact (consent_request_id, consent_artefact, consent_artefact_id, status, date_created)" +
            " VALUES ($1, $2, $3, $4, $5)";
    private final String UPDATE_CONSENT_ARTEFACT_STATUS_QUERY = "UPDATE " +
            "consent_artefact set status=$1, date_modified=$2 where consent_artefact_id=$3";
    private PgPool dbClient;

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
                        }));
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
                                StreamSupport.stream(handler.result().spliterator(), false)
                                        .map(row -> (JsonObject) row.getValue("consent_request"))
                                        .map(json -> json.mapTo(ConsentRequest.class))
                                        .forEach(monoSink::success);
                            }
                        }));
    }

    public Mono<Void> insertConsentArtefact(ConsentArtefact consentArtefact,
                                            ConsentStatus status,
                                            String consentRequestId) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(
                        INSERT_CONSENT_ARTEFACT_QUERY,
                        Tuple.of(consentRequestId,
                                JsonObject.mapFrom(consentArtefact),
                                consentArtefact.getConsentId(),
                                status.toString(),
                                LocalDateTime.now()),
                        handler -> {
                            if (handler.failed())
                                monoSink.error(dbOperationFailure("Failed to insert consent artefact"));
                            else
                                monoSink.success();
                        }));
    }

    public Mono<Void> updateStatus(ConsentArtefactReference consentArtefactReference) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(
                        UPDATE_CONSENT_ARTEFACT_STATUS_QUERY,
                        Tuple.of(consentArtefactReference.getStatus().toString(),
                                LocalDateTime.now(),
                                consentArtefactReference.getId()),
                        handler -> {
                            if (handler.failed())
                                monoSink.error(dbOperationFailure("Failed to update consent artefact status"));
                            else
                                monoSink.success();
                        }));
    }
}
