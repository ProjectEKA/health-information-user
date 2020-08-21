package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.common.Serializer;
import in.org.projecteka.hiu.consent.model.ConsentArtefact;
import in.org.projecteka.hiu.consent.model.ConsentArtefactReference;
import in.org.projecteka.hiu.consent.model.ConsentRequest;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static in.org.projecteka.hiu.ClientError.consentArtefactNotFound;
import static in.org.projecteka.hiu.ClientError.consentRequestNotFound;
import static in.org.projecteka.hiu.ClientError.dbOperationFailure;
import static in.org.projecteka.hiu.common.Constants.STATUS;
import static in.org.projecteka.hiu.common.Serializer.from;
import static in.org.projecteka.hiu.common.Serializer.to;

@AllArgsConstructor
public class ConsentRepository {

    private static final Logger logger = LoggerFactory.getLogger(ConsentRepository.class);

    private final String CONSENT_REQUEST = "consent_request";
    private static final String SELECT_CONSENT_IDS_FROM_CONSENT_ARTIFACT = "SELECT consent_artefact_id, " +
            "consent_artefact -> 'hip' ->> 'id' as hipId, consent_artefact -> 'hip' ->> 'name' as hipName, " +
            "consent_artefact -> 'requester' ->> 'name' as requester, " +
            "consent_artefact -> 'permission' ->> 'dataEraseAt' as consentExpiryDate, status" +
            " FROM consent_artefact WHERE consent_request_id=$1";
    private static final String INSERT_CONSENT_ARTEFACT_QUERY = "INSERT INTO " +
            "consent_artefact (consent_request_id, consent_artefact, consent_artefact_id, status, date_created)" +
            " VALUES ($1, $2, $3, $4, $5)";
    private static final String UPDATE_CONSENT_ARTEFACT_STATUS_QUERY = "UPDATE " +
            "consent_artefact set status=$1, date_modified=$2 where consent_artefact_id=$3";
    /**
     * TODO: This query should be refactored. The status should be updated separately
     */
    private static final String INSERT_CONSENT_REQUEST_QUERY = "INSERT INTO " +
            "consent_request (consent_request, consent_request_id) VALUES ($1, $2)";
    /**
     * TODO: Should be refactored.
     * See notes in {@link #get(String)}
     */
    private static final String SELECT_CONSENT_REQUEST_QUERY = "SELECT consent_request " +
            "FROM consent_request WHERE consent_request_id = $1";
    private static final String SELECT_CONSENT_ARTEFACT_QUERY = "SELECT consent_artefact FROM consent_artefact WHERE " +
            "consent_artefact_id = $1 AND status = $2";
    private static final String CONSENT_REQUEST_BY_REQUESTER_ID =
            "SELECT consent_request, status, consent_request_id FROM consent_request " +
                    "where consent_request ->> 'requesterId' = $1 ORDER BY date_created DESC";
    private static final String SELECT_HIP_ID_FOR_A_CONSENT = "SELECT consent_artefact -> 'hip' ->> 'id' as hipId " +
            "FROM consent_artefact WHERE consent_artefact_id=$1";
    private static final String SELECT_PATIENT_ID_FOR_A_CONSENT = "SELECT consent_artefact -> 'patient' ->> 'id' as patientId " +
            "FROM consent_artefact WHERE consent_artefact_id=$1";
    private static final String SELECT_CONSENT_ID_FROM_REQUEST_ID = "SELECT consent_artefact_id from consent_artefact" +
            " WHERE " +
            "consent_request_id = $1";
    private static final String INSERT_GATEWAY_CONSENT_REQUEST = "INSERT INTO " +
            "consent_request (consent_request, gateway_request_id, status) VALUES ($1, $2, $3)";
    private static final String GATEWAY_CONSENT_REQUEST_STATUS = "SELECT status " +
            "FROM consent_request WHERE gateway_request_id=$1";

    private static final String UPDATE_GATEWAY_CONSENT_REQUEST_STATUS = "UPDATE consent_request " +
            "set consent_request_id=$1, status=$2, date_modified=$3 WHERE gateway_request_id=$4";

    private static final String UPDATE_CONSENT_REQUEST_STATUS = "UPDATE consent_request " +
            "set status=$2, date_modified=$3 WHERE consent_request_id=$1";

    private static final String SELECT_CONSENT_REQUEST_STATUS = "SELECT status FROM consent_request WHERE " +
            "consent_request_id = $1";
    private static final String SELECT_CM_ID_FOR_A_CONSENT = "SELECT consent_artefact -> 'consentManager' ->> 'id' as " +
            "consentManagerId FROM consent_artefact WHERE consent_artefact_id=$1";

    private final PgPool readWriteClient;
    private final PgPool readOnlyClient;

    @Deprecated
    public Mono<Void> insert(ConsentRequest consentRequest) {
        return Mono.create(monoSink -> readWriteClient.preparedQuery(INSERT_CONSENT_REQUEST_QUERY)
                .execute(Tuple.of(new JsonObject(from(consentRequest)), consentRequest.getId()),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to insert to consent request"));
                                return;
                            }
                            monoSink.success();
                        }));
    }

    /**
     * TODO refactor this method. The only purpose of the method is to check whether a ConsentRequest by
     * the CM assigned consentRequestId exists or not. We can return a count and avoid unnecessary record fetch
     * and deserialization
     *
     * @param consentRequestId
     * @return
     */
    public Mono<ConsentRequest> get(String consentRequestId) {
        return Mono.create(monoSink -> readOnlyClient.preparedQuery(SELECT_CONSENT_REQUEST_QUERY)
                .execute(Tuple.of(consentRequestId),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to fetch consent request"));
                                return;
                            }
                            var iterator = handler.result().iterator();
                            if (!iterator.hasNext()) {
                                monoSink.error(consentRequestNotFound());
                                return;
                            }
                            var object = iterator.next().getValue(CONSENT_REQUEST).toString();
                            monoSink.success(Serializer.to(object, ConsentRequest.class));
                        }));
    }

    public Mono<ConsentArtefact> getConsent(String consentId, ConsentStatus status) {
        return Mono.create(monoSink -> readOnlyClient.preparedQuery(SELECT_CONSENT_ARTEFACT_QUERY)
                .execute(Tuple.of(consentId, status.toString()),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to fetch consent artefact"));
                                return;
                            }
                            var iterator = handler.result().iterator();
                            if (!iterator.hasNext()) {
                                monoSink.error(consentArtefactNotFound());
                                return;
                            }
                            var consentArtefact = to(iterator.next().getValue("consent_artefact").toString(),
                                    ConsentArtefact.class);
                            monoSink.success(consentArtefact);
                        }));
    }

    public Mono<Void> insertConsentArtefact(ConsentArtefact consentArtefact,
                                            ConsentStatus status,
                                            String consentRequestId) {
        return Mono.create(monoSink -> readWriteClient.preparedQuery(INSERT_CONSENT_ARTEFACT_QUERY)
                .execute(Tuple.of(consentRequestId,
                        new JsonObject(from(consentArtefact)),
                        consentArtefact.getConsentId(),
                        status.toString(),
                        LocalDateTime.now()),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to insert consent artefact"));
                                return;
                            }
                            monoSink.success();
                        }));
    }

    public Mono<Void> updateStatus(ConsentArtefactReference consentArtefactReference,
                                   ConsentStatus status,
                                   LocalDateTime timestamp) {
        return Mono.create(monoSink -> readWriteClient.preparedQuery(UPDATE_CONSENT_ARTEFACT_STATUS_QUERY)
                .execute(Tuple.of(status.toString(),
                        timestamp,
                        consentArtefactReference.getId()),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to update consent artefact status"));
                                return;
                            }
                            monoSink.success();
                        }));
    }

    public Flux<Map<String, String>> getConsentDetails(String consentRequestId) {
        return Flux.create(fluxSink -> readOnlyClient.preparedQuery(SELECT_CONSENT_IDS_FROM_CONSENT_ARTIFACT)
                .execute(Tuple.of(consentRequestId),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                fluxSink.error(dbOperationFailure("Failed to get consent id from consent request Id"));
                                return;
                            }
                            for (Row row : handler.result()) {
                                fluxSink.next(toConsentDetail(row));
                            }
                            fluxSink.complete();
                        }));
    }

    private Map<String, String> toConsentDetail(Row row) {
        Map<String, String> map = new HashMap<>();
        map.put("consentId", row.getString(0));
        map.put("hipId", row.getString(1));
        map.put("hipName", row.getString(2));
        map.put("requester", row.getString(3));
        map.put("consentExpiryDate", row.getString(4));
        map.put(STATUS, row.getString(5));
        return map;
    }

    public Mono<String> getConsentArtefactId(String consentRequestId) {
        return Mono.create(monoSink -> readOnlyClient.preparedQuery(SELECT_CONSENT_ID_FROM_REQUEST_ID)
                .execute(Tuple.of(consentRequestId),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(new Exception("Failed to get consent artefact id from consent request " +
                                        "id"));
                                return;
                            }
                            monoSink.success(handler.result().iterator().next().getString(0));
                        }));
    }

    public Mono<String> getHipId(String consentId) {
        return Mono.create(monoSink -> readOnlyClient.preparedQuery(SELECT_HIP_ID_FOR_A_CONSENT)
                .execute(Tuple.of(consentId),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to get hip Id from consent Id"));
                                return;
                            }
                            var iterator = handler.result().iterator();
                            if (!iterator.hasNext()) {
                                monoSink.error(dbOperationFailure("Failed to get hip Id from consent Id"));
                                return;
                            }
                            monoSink.success(iterator.next().getString(0));
                        }));
    }

    public Mono<String> getPatientId(String consentArtefactId) {
        return Mono.create(monoSink -> readOnlyClient.preparedQuery(SELECT_PATIENT_ID_FOR_A_CONSENT)
                .execute(Tuple.of(consentArtefactId),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to get patient Id from consent Id"));
                                return;
                            }
                            var iterator = handler.result().iterator();
                            if (!iterator.hasNext()) {
                                monoSink.error(dbOperationFailure("Failed to get patient Id from consent Id"));
                                return;
                            }
                            monoSink.success(iterator.next().getString(0));
                        }));
    }


    public Mono<Void> insertConsentRequestToGateway(ConsentRequest consentRequest) {
        return Mono.create(monoSink -> readWriteClient.preparedQuery(INSERT_GATEWAY_CONSENT_REQUEST)
                .execute(Tuple.of(
                        new JsonObject(from(consentRequest)),
                        consentRequest.getId(),
                        ConsentStatus.POSTED.toString()),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to insert to consent request"));
                                return;
                            }
                            monoSink.success();
                        }));
    }

    public Mono<ConsentStatus> consentRequestStatus(String gatewayRequestId) {
        return Mono.create(monoSink -> readOnlyClient.preparedQuery(GATEWAY_CONSENT_REQUEST_STATUS)
                .execute(Tuple.of(gatewayRequestId),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to identify consent request"));
                                return;
                            }
                            var iterator = handler.result().iterator();
                            if (!iterator.hasNext()) {
                                monoSink.success();
                                return;
                            }
                            monoSink.success(ConsentStatus.valueOf(iterator.next().getString(STATUS)));
                        }));
    }

    public Mono<ConsentStatus> consentRequestStatusFor(String consentRequestId) {
        return Mono.create(monoSink -> readOnlyClient.preparedQuery(GATEWAY_CONSENT_REQUEST_STATUS)
                .execute(Tuple.of(consentRequestId),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to identify consent request"));
                                return;
                            }
                            var iterator = handler.result().iterator();
                            if (!iterator.hasNext()) {
                                monoSink.success();
                                return;
                            }
                            monoSink.success(ConsentStatus.valueOf(iterator.next().getString(STATUS)));
                        }));
    }

    public Mono<Void> updateConsentRequestStatus(String gatewayRequestId,
                                                 ConsentStatus status,
                                                 String consentRequestId) {
        return Mono.create(monoSink ->
                readWriteClient.preparedQuery(UPDATE_GATEWAY_CONSENT_REQUEST_STATUS)
                        .execute(Tuple.of(consentRequestId, status.toString(), LocalDateTime.now(), gatewayRequestId),
                                handler -> {
                                    if (handler.failed()) {
                                        logger.error(handler.cause().getMessage(), handler.cause());
                                        monoSink.error(dbOperationFailure("Failed to update consent request status"));
                                        return;
                                    }
                                    monoSink.success();
                                }));
    }

    public Flux<Map<String, Object>> requestsOf(String requesterId) {
        return Flux.create(fluxSink -> readOnlyClient.preparedQuery(CONSENT_REQUEST_BY_REQUESTER_ID)
                .execute(Tuple.of(requesterId),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                fluxSink.error(dbOperationFailure("Failed to fetch consent requests"));
                                return;
                            }
                            for (Row result : handler.result()) {
                                ConsentRequest consentRequest = to(
                                        result.getValue(CONSENT_REQUEST).toString(), ConsentRequest.class);
                                if (consentRequest == null) {
                                    continue;
                                }
                                Map<String, Object> resultMap = new HashMap<>();
                                resultMap.put("consentRequest", consentRequest);
                                resultMap.put(STATUS, ConsentStatus.valueOf(result.getString(STATUS)));
                                resultMap.put("consentRequestId", result.getString("consent_request_id"));
                                fluxSink.next(resultMap);
                            }
                            fluxSink.complete();
                        }));
    }

    public Mono<Void> updateConsentRequestStatus(ConsentStatus status, String consentRequestId) {
        return Mono.create(monoSink ->
                readWriteClient.preparedQuery(UPDATE_CONSENT_REQUEST_STATUS)
                        .execute(Tuple.of(consentRequestId, status.toString(), LocalDateTime.now()),
                                handler -> {
                                    if (handler.failed()) {
                                        logger.error(handler.cause().getMessage(), handler.cause());
                                        monoSink.error(dbOperationFailure("Failed to update consent request status"));
                                        return;
                                    }
                                    monoSink.success();
                                }));
    }

    public Mono<ConsentStatus> getConsentRequestStatus(String consentId) {
        return Mono.create(monoSink -> readOnlyClient.preparedQuery(SELECT_CONSENT_REQUEST_STATUS)
                .execute(Tuple.of(consentId),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to identify consent request"));
                                return;
                            }
                            var iterator = handler.result().iterator();
                            if (!iterator.hasNext()) {
                                monoSink.success();
                                return;
                            }
                            monoSink.success(ConsentStatus.valueOf(iterator.next().getString(STATUS)));
                        }));
    }

    public Mono<String> getConsentMangerId(String consentId) {
        return Mono.create(monoSink -> readOnlyClient.preparedQuery(SELECT_CM_ID_FOR_A_CONSENT)
                .execute(Tuple.of(consentId),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to get CM Id from consent Id"));
                                return;
                            }
                            var iterator = handler.result().iterator();
                            if (!iterator.hasNext()) {
                                monoSink.error(dbOperationFailure("Failed to get CM Id from consent Id"));
                                return;
                            }
                            monoSink.success(iterator.next().getString(0));
                        }));
    }
}
