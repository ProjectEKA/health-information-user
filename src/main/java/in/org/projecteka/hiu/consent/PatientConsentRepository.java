package in.org.projecteka.hiu.consent;


import in.org.projecteka.hiu.consent.model.DateRange;
import in.org.projecteka.hiu.dataflow.model.PatientDataRequestDetail;
import in.org.projecteka.hiu.dataflow.model.PatientDataRequestMapping;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static in.org.projecteka.hiu.ClientError.dbOperationFailure;
import static in.org.projecteka.hiu.common.Serializer.to;

@AllArgsConstructor
public class PatientConsentRepository {

    private static final Logger logger = LoggerFactory.getLogger(PatientConsentRepository.class);

    private static final String INSERT_PATIENT_CONSENT_REQUEST = "INSERT INTO " +
            "patient_consent_request (data_request_id, hip_id, patient_id) VALUES ($1, $2, $3)";

    private static final String UPDATE_PATIENT_CONSENT_REQUEST = "UPDATE " +
            "patient_consent_request SET consent_request_id=$2, date_modified=$3 WHERE data_request_id=$1";

    private static final String SELECT_CONSENT_ARTEFACT_ID_AND_STATUS = "SELECT " +
            "ca.consent_artefact_id, ca.consent_request_id, pcr.date_created, ca.consent_artefact -> 'permission' -> 'dateRange' as date_range " +
            "FROM patient_consent_request pcr " +
            "left outer join consent_artefact ca on pcr.consent_request_id::text=ca.consent_request_id " +
            "WHERE pcr.hip_id=$1 AND pcr.patient_id=$2 order by pcr.date_created desc limit 5";

    private static final String SELECT_LAST_RESOURCE_DATE = "SELECT dfp.latest_res_date, dfp.status, dfp.part_number " +
            "FROM data_flow_parts dfp " +
            "left outer join data_flow_request dfr on dfp.transaction_id=dfr.transaction_id " +
            "WHERE dfr.consent_artefact_id=$1 order by dfp.part_number desc";

    private static final String SELECT_CONSENT_REQ_IDS = "SELECT consent_request_id, data_request_id, hip_id FROM patient_consent_request " +
            "WHERE data_request_id IN (%s)";

    private static final String DELETE_FROM_PATIENT_CONSENT_REQUEST = "DELETE FROM patient_consent_request " +
            "WHERE patient_id=$1 RETURNING consent_request_id::text";

    private static final String DELETE_FROM_CONSENT_REQUEST = "DELETE FROM consent_request " +
            "WHERE consent_request_id IN (%s) RETURNING consent_request_id";

    private static final String DELETE_FROM_CONSENT_ARTEFACT = "DELETE FROM consent_artefact " +
            "WHERE consent_request_id IN (%s) RETURNING consent_artefact_id";

    private static final String DELETE_FROM_DATA_FLOW_REQUEST = "DELETE FROM data_flow_request " +
            "WHERE consent_artefact_id IN (%s) RETURNING transaction_id";

    private static final String DELETE_FROM_HEALTH_INFORMATION = "DELETE FROM health_information " +
            "WHERE transaction_id IN (%s) RETURNING transaction_id";

    private static final String DELETE_FROM_DATA_FLOW_PARTS = "DELETE FROM data_flow_parts " +
            "WHERE transaction_id IN (%s) RETURNING transaction_id";

    private static final String DELETE_FROM_DATA_FLOW_REQUEST_KEYS = "DELETE FROM data_flow_request_keys " +
            "WHERE transaction_id IN (%s)";

    private static final String SELECT_LATEST_RESOURCE_BY_CC_FOR_PATIENT_IN_HIP =
                "SELECT hi.care_context_reference as care_context_reference, " +
                    "dfr.consent_artefact_id as consent_artefact_id, MAX(dfp.latest_res_date) as max_res_date " +
                "FROM patient_consent_request pcr " +
                    "JOIN consent_artefact ca ON ca.consent_request_id=pcr.consent_request_id::text " +
                    "JOIN data_flow_request dfr ON dfr.consent_artefact_id=ca.consent_artefact_id " +
                    "JOIN data_flow_parts dfp on dfp.transaction_id=dfr.transaction_id " +
                    "JOIN health_information hi ON hi.transaction_id=dfp.transaction_id " +
                "WHERE pcr.patient_id=$1 AND pcr.hip_id=$2 AND dfp.status in ('SUCCEEDED', 'PARTIAL') " +
                    "GROUP BY care_context_reference, dfr.consent_artefact_id";



    private final PgPool dbClient;

    public Mono<List<Map<String, Object>>> getDataFlowParts(String consentArtefactId) {
        return Mono.create(monoSink -> dbClient.preparedQuery(SELECT_LAST_RESOURCE_DATE)
                .execute(Tuple.of(consentArtefactId),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to fetch data flow parts"));
                            } else {
                                List<Map<String, Object>> dataFlowPartsDetails = new ArrayList<>();
                                handler.result().forEach(row -> dataFlowPartsDetails.add(toDataFlowPartsDetail(row)));
                                monoSink.success(dataFlowPartsDetails);
                            }
                        }));
    }

    public Mono<List<Map<String, Object>>> getConsentDetails(String hipId, String requesterId) {
        return Mono.create(monoSink -> dbClient.preparedQuery(SELECT_CONSENT_ARTEFACT_ID_AND_STATUS)
                .execute(Tuple.of(hipId, requesterId),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to get consent details from consent artefact table"));
                            } else {
                                List<Map<String, Object>> consentDetails = new ArrayList<>();
                                handler.result().forEach(row -> consentDetails.add(toConsentDetail(row)));
                                monoSink.success(consentDetails);
                            }
                        }));
    }

    private Map<String, Object> toDataFlowPartsDetail(Row row) {
        Map<String, Object> map = new HashMap<>();
        map.put("latestResourceDate", row.getLocalDateTime("latest_res_date"));
        map.put("status", row.getString("status"));
        map.put("dataFlowPartNumber", "part_number");
        return map;
    }

    private Map<String, Object> toConsentDetail(Row row) {
        Map<String, Object> map = new HashMap<>();
        map.put("consentArtefactId", row.getString("consent_artefact_id"));
        map.put("dateCreated", row.getLocalDateTime("date_created"));
        map.put("consentRequestId", row.getString("consent_request_id"));
        if (row.getValue("date_range") == null) {
            map.put("dateRange", null);
        } else {
            map.put("dateRange", to(row.getValue("date_range").toString(), DateRange.class));
        }
        return map;
    }

    public Mono<Void> insertPatientConsentRequest(UUID dataRequestId, String hipId, String requesterId) {
        return Mono.create(monoSink -> dbClient.preparedQuery(INSERT_PATIENT_CONSENT_REQUEST)
                .execute(Tuple.of(dataRequestId, hipId, requesterId),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to insert to patient consent request"));
                                return;
                            }
                            monoSink.success();
                        }));
    }


    public Mono<Void> updatePatientConsentRequest(UUID dataRequestId, UUID consentRequestId, LocalDateTime now) {
        return Mono.create(monoSink -> dbClient.preparedQuery(UPDATE_PATIENT_CONSENT_REQUEST)
                .execute(Tuple.of(dataRequestId, consentRequestId, now),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to update patient consent request"));
                                return;
                            }
                            monoSink.success();
                        }));
    }

    public Mono<List<String>> deletePatientConsentRequestFor(String patientId) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(DELETE_FROM_PATIENT_CONSENT_REQUEST)
                        .execute(Tuple.of(patientId),
                                handler -> {
                                    if (handler.failed()) {
                                        logger.error(handler.cause().getMessage(), handler.cause());
                                        monoSink.error(new Exception("Failed to delete patient consent request"));
                                        return;
                                    } else {
                                        List<String> consentRequestIds = new ArrayList<>();
                                        handler.result().forEach(row -> consentRequestIds.add(row.getString("consent_request_id")));
                                        monoSink.success(consentRequestIds);
                                    }
                                }));
    }

    public Mono<List<String>> deleteConsentRequestFor(List<String> consentRequestIds) {
        if (consentRequestIds.isEmpty()) {
            return Mono.empty();
        }
        var generatedQuery = String.format(DELETE_FROM_CONSENT_REQUEST, joinByComma(consentRequestIds));
        return Mono.create(monoSink ->
                dbClient.preparedQuery(generatedQuery)
                        .execute(handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(new Exception("Failed to delete from consent request"));
                                return;
                            } else {
                                List<String> ids = new ArrayList<>();
                                handler.result().forEach(row -> ids.add(row.getString("consent_request_id")));
                                monoSink.success(ids);

                            }
                        }));
    }

    public Mono<List<String>> deleteConsentArteFactFor(List<String> consentArtefactIds) {
        if (consentArtefactIds.isEmpty()) {
            return Mono.empty();
        }
        var generatedQuery = String.format(DELETE_FROM_CONSENT_ARTEFACT, joinByComma(consentArtefactIds));
        return Mono.create(monoSink ->
                dbClient.preparedQuery(generatedQuery)
                        .execute(handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(new Exception("Failed to delete from consent artefact"));
                                return;
                            } else {
                                List<String> ids = new ArrayList<>();
                                handler.result().forEach(row -> ids.add(row.getString("consent_artefact_id")));
                                monoSink.success(ids);

                            }
                        }));
    }

    public Mono<List<String>> deleteDataFlowRequestFor(List<String> consentArtefactIds) {
        if (consentArtefactIds.isEmpty()) {
            return Mono.empty();
        }
        var generatedQuery = String.format(DELETE_FROM_DATA_FLOW_REQUEST, joinByComma(consentArtefactIds));
        return Mono.create(monoSink ->
                dbClient.preparedQuery(generatedQuery)
                        .execute(handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(new Exception("Failed to delete from data flow request"));
                                return;
                            } else {
                                List<String> ids = new ArrayList<>();
                                handler.result().forEach(row -> ids.add(row.getString("transaction_id")));
                                monoSink.success(ids);

                            }
                        }));
    }

    public Mono<List<String>> deleteHealthInformationFor(List<String> transactionIds) {
        if (transactionIds.isEmpty()) {
            return Mono.empty();
        }
        var generatedQuery = String.format(DELETE_FROM_HEALTH_INFORMATION, joinByComma(transactionIds));
        return Mono.create(monoSink ->
                dbClient.preparedQuery(generatedQuery)
                        .execute(handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(new Exception("Failed to delete from Health Information"));
                                return;
                            } else {
                                List<String> ids = new ArrayList<>();
                                handler.result().forEach(row -> ids.add(row.getString("transaction_id")));
                                monoSink.success(ids);

                            }
                        }));
    }

    public Mono<Void> deleteDataFlowRequestKeysFor(List<String> transactionIds) {
        if (transactionIds.isEmpty()) {
            return Mono.empty();
        }
        var generatedQuery = String.format(DELETE_FROM_DATA_FLOW_REQUEST_KEYS, joinByComma(transactionIds));
        return Mono.create(monoSink ->
                dbClient.preparedQuery(generatedQuery)
                        .execute(handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(new Exception("Failed to delete from data flow parts"));
                                return;
                            } else {
                                monoSink.success();

                            }
                        }));
    }

    public Mono<List<String>> deleteDataFlowPartsFor(List<String> transactionIds) {
        if (transactionIds.isEmpty()) {
            return Mono.empty();
        }
        var generatedQuery = String.format(DELETE_FROM_DATA_FLOW_PARTS, joinByComma(transactionIds));
        return Mono.create(monoSink ->
                dbClient.preparedQuery(generatedQuery)
                        .execute(handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(new Exception("Failed to delete from data flow request keys"));
                                return;
                            } else {
                                List<String> ids = new ArrayList<>();
                                handler.result().forEach(row -> ids.add(row.getString("transaction_id")));
                                monoSink.success(ids);

                            }
                        }));
    }

    public Flux<PatientDataRequestMapping> fetchConsentRequestIds(List<String> dataRequestIds) {
        var generatedQuery = String.format(SELECT_CONSENT_REQ_IDS, joinByComma(dataRequestIds));
        if (dataRequestIds.isEmpty()) {
            return Flux.empty();
        }
        return Flux.create(fluxSink -> dbClient.preparedQuery(generatedQuery)
                .execute(handler -> {
                    if (handler.failed()) {
                        logger.error(handler.cause().getMessage(), handler.cause());
                        fluxSink.error(dbOperationFailure("Failed to fetch consent request ids"));
                        return;
                    }
                    for (Row row : handler.result()) {
                        fluxSink.next(PatientDataRequestMapping.builder()
                                .consentRequestId(Objects.toString(row.getUUID("consent_request_id"), null))
                                .dataRequestId(row.getUUID("data_request_id").toString())
                                .hipId(row.getString("hip_id"))
                                .build());
                    }
                    fluxSink.complete();
                }));
    }

    private String joinByComma(List<String> list) {
        return String.join(", ", list.stream().map(e -> String.format("'%s'", e)).collect(Collectors.toList()));
    }

    public Mono<List<Map<String, Object>>> getLatestResourceDateByHipCareContext(String patientId, String hipId) {
        return Mono.create(monoSink -> dbClient.preparedQuery(SELECT_LATEST_RESOURCE_BY_CC_FOR_PATIENT_IN_HIP)
                .execute(Tuple.of(patientId, hipId),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to get consent details from consent artefact table"));
                            } else {
                                List<Map<String, Object>> ccDetails = new ArrayList<>();
                                handler.result().forEach(row -> {
                                    ccDetails.add(Map.of("careContextReference", row.getString("care_context_reference"),
                                            "consentArtefactId", row.getString("consent_artefact_id"),
                                            "lastResourceDate", row.getLocalDateTime("max_res_date")));
                                });
                                monoSink.success(ccDetails);
                            }
                        }));
    }

    private static final String SELECT_LATEST_DATA_REQUEST_FOR_PATIENT_BY_HIPS =
            "SELECT " +
               "pcr.date_created, pcr.data_request_id, pcr.hip_id " +
            "FROM patient_consent_request pcr " +
            "WHERE ROW(pcr.hip_id, pcr.date_created) = (SELECT " +
               "hip_id, max(date_created) " +
               "FROM patient_consent_request " +
               "WHERE patient_id=$1 and hip_id in (%s) " +
               "GROUP BY hip_id)";

    public Mono<List<PatientDataRequestDetail>> getLatestDataRequestsForPatient(String patientId, List<String> hipIds) {
        var generatedQuery = String.format(SELECT_LATEST_DATA_REQUEST_FOR_PATIENT_BY_HIPS, joinByComma(hipIds));
        return Mono.create(monoSink -> dbClient.preparedQuery(generatedQuery)
                .execute(Tuple.of(patientId),
                        handler -> {
                            if (handler.failed()) {
                                logger.error(handler.cause().getMessage(), handler.cause());
                                monoSink.error(dbOperationFailure("Failed to get data request details for patient"));
                            } else {
                                List<PatientDataRequestDetail> results = new ArrayList<>();
                                handler.result().forEach(row -> {
                                    results.add(PatientDataRequestDetail.builder()
                                            .patientDataRequestedAt(row.getLocalDateTime("date_created"))
                                            .hipId(row.getString("hip_id"))
                                            .dataRequestId(row.getUUID("data_request_id").toString())
                                            .build());
                                });
                                monoSink.success(results);
                            }
                        }));
    }
}

