package in.org.projecteka.hiu.consent;

import in.org.projecteka.hiu.consent.model.DateRange;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
            "ca.status, ca.consent_artefact_id, ca.consent_request_id, pcr.date_created, ca.consent_artefact -> 'permission' -> 'dateRange' as date_range " +
            "FROM patient_consent_request pcr " +
            "left outer join consent_artefact ca on pcr.consent_request_id::text=ca.consent_request_id " +
            "WHERE pcr.hip_id=$1 AND pcr.patient_id=$2 order by pcr.date_created desc limit 5";

    private static final String SELECT_LAST_RESOURCE_DATE = "SELECT dfp.latest_res_date, dfp.status, dfp.part_number " +
            "FROM data_flow_parts dfp " +
            "left outer join data_flow_request dfr on dfp.transaction_id=dfr.transaction_id " +
            "WHERE dfr.consent_artefact_id=$1";

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
        map.put("status", row.getString("data_flow_parts_status"));
        map.put("dataFlowPartNumber", "part_number");
        return map;
    }

    private Map<String, Object> toConsentDetail(Row row) {
        Map<String, Object> map = new HashMap<>();
        map.put("consentArtefactId", row.getString("consent_artefact_id"));
        map.put("status", row.getString("status"));
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
}

