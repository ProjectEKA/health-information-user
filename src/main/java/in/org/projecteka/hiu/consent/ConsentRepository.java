package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.consent.model.ConsentArtefact;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import in.org.projecteka.hiu.consent.model.ConsentRequest;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

import static in.org.projecteka.hiu.ClientError.dbOperationFailure;

@AllArgsConstructor
public class ConsentRepository {
    private static final String SELECT_CONSENT_IDS_FROM_CONSENT_ARTIFACT = "SELECT consent_artefact_id, " +
            "consent_artefact -> 'hip' ->> 'id' as hipId, consent_artefact -> 'hip' ->> 'name' as hipName, " +
            "consent_artefact -> 'requester' ->> 'name' as requester, status FROM " +
            "consent_artefact WHERE consent_request_id=$1";
    private static final String INSERT_CONSENT_ARTEFACT_QUERY = "INSERT INTO " +
            "consent_artefact (consent_request_id, consent_artefact, consent_artefact_id, status, date_created)" +
            " VALUES ($1, $2, $3, $4, $5)";
    private static final String UPDATE_CONSENT_ARTEFACT_STATUS_QUERY = "UPDATE " +
            "consent_artefact set status=$1, date_modified=$2 where consent_artefact_id=$3";
    private static final String INSERT_CONSENT_REQUEST_QUERY = "INSERT INTO " +
            "consent_request (consent_request, consent_request_id) VALUES ($1, $2)";
    private static final String SELECT_CONSENT_REQUEST_QUERY = "SELECT consent_request " +
            "FROM consent_request WHERE consent_request ->> 'id' = $1";
    private static final String SELECT_CONSENT_ARTEFACT_QUERY = "SELECT consent_artefact FROM consent_artefact WHERE " +
            "consent_artefact_id = $1 AND status = $2";
    private static final String CONSENT_REQUEST_BY_REQUESTER_ID =
            "SELECT consent_request FROM consent_request where consent_request ->> 'requesterId' = $1 ORDER BY " +
                    "date_created DESC";
    private static final String UPDATE_CONSENT_REQUEST_QUERY = "UPDATE " +
            "consent_request set consent_request = $1 where consent_request_id = $2";
    private static final String SELECT_HIP_ID_FOR_A_CONSENT = "SELECT consent_artefact -> 'hip' ->> 'id' as hipId " +
            "FROM consent_artefact WHERE consent_artefact_id=$1";
    private PgPool dbClient;

    @SneakyThrows
    public Mono<Void> insert(ConsentRequest consentRequest) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(
                        INSERT_CONSENT_REQUEST_QUERY,
                        Tuple.of(JsonObject.mapFrom(consentRequest), consentRequest.getId()),
                        handler -> {
                            if (handler.failed())
                                monoSink.error(new Exception("Failed to insert to consent request"));
                            else
                                monoSink.success();
                        }));
    }

    @SneakyThrows
    public Mono<ConsentRequest> get(String consentRequestId) {
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
                                monoSink.success();
                            }
                        }));
    }

    @SneakyThrows
    public Mono<ConsentArtefact> getConsent(String consentId, ConsentStatus status) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(
                        SELECT_CONSENT_ARTEFACT_QUERY,
                        Tuple.of(consentId, status.toString()),
                        handler -> {
                            if (handler.failed())
                                monoSink.error(dbOperationFailure("Failed to fetch consent artefact"));
                            else {
                                RowSet<Row> results = handler.result();
                                if (results.iterator().hasNext()) {
                                    Row row = results.iterator().next();
                                    JsonObject artefact = (JsonObject) row.getValue("consent_artefact");
                                    ConsentArtefact consentArtefact = artefact.mapTo(ConsentArtefact.class);
                                    monoSink.success(consentArtefact);
                                } else {
                                    monoSink.success(null);
                                }
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

    public Mono<Void> updateStatus(ConsentArtefactReference consentArtefactReference,
                                   ConsentStatus status,
                                   Date timestamp) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(
                        UPDATE_CONSENT_ARTEFACT_STATUS_QUERY,
                        Tuple.of(status.toString(),
                                convertToLocalDateTime(timestamp),
                                consentArtefactReference.getId()),
                        handler -> {
                            if (handler.failed())
                                monoSink.error(dbOperationFailure("Failed to update consent artefact status"));
                            else
                                monoSink.success();
                        }));
    }

    public Flux<Map<String, String>> getConsentDetails(String consentRequestId) {
        return Flux.create(fluxSink -> dbClient.preparedQuery(SELECT_CONSENT_IDS_FROM_CONSENT_ARTIFACT,
                Tuple.of(consentRequestId),
                handler -> {
                    if (handler.failed()) {
                        fluxSink.error(new Exception("Failed to get consent id from consent request Id"));
                    } else {
                        StreamSupport.stream(handler.result().spliterator(), false)
                                .map(this::toConsentDetail)
                                .forEach(fluxSink::next);
                        fluxSink.complete();
                    }
                }));
    }

    private Map<String, String> toConsentDetail(Row row) {
        Map<String, String> map = new HashMap<>();
        map.put("consentId", row.getString(0));
        map.put("hipId", row.getString(1));
        map.put("hipName", row.getString(2));
        map.put("requester", row.getString(3));
        map.put("status", row.getString(4));
        return map;
    }

    public Flux<ConsentRequest> requestsFrom(String requesterId) {
        return Flux.create(fluxSink -> dbClient.preparedQuery(
                CONSENT_REQUEST_BY_REQUESTER_ID,
                Tuple.of(requesterId),
                handler -> {
                    if (handler.failed())
                        fluxSink.error(dbOperationFailure("Failed to fetch consent requests"));
                    else {
                        StreamSupport.stream(handler.result().spliterator(), false)
                                .map(row -> (JsonObject) row.getValue("consent_request"))
                                .map(json -> json.mapTo(ConsentRequest.class))
                                .forEach(fluxSink::next);
                        fluxSink.complete();
                    }
                }));
    }

    public Mono<Void> updateConsent(String requestId, ConsentRequest consentRequest) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(
                        UPDATE_CONSENT_REQUEST_QUERY,
                        Tuple.of(JsonObject.mapFrom(consentRequest),
                                requestId),
                        handler -> {
                            if (handler.failed()) {
                                monoSink.error(dbOperationFailure("Failed to update consent request status"));
                                return;
                            }
                            monoSink.success();
                        }));
    }

    public Mono<String> getHipId(String consentId){
        return Mono.create(monoSink -> dbClient.preparedQuery(SELECT_HIP_ID_FOR_A_CONSENT,
                Tuple.of(consentId),
                handler -> {
                    if (handler.failed()) {
                        monoSink.error(new Exception("Failed to get hip Id from consent Id"));
                    } else {
                        monoSink.success(handler.result().iterator().next().getString(0));
                    }
                }));
    }

    private LocalDateTime convertToLocalDateTime(Date date) {
        if (date != null) {
            return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        return null;
    }
}
