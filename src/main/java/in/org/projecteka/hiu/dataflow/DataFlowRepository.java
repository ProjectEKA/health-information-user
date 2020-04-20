package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestKeyMaterial;
import in.org.projecteka.hiu.dataflow.model.HealthInfoStatus;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.apache.log4j.Logger;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static in.org.projecteka.hiu.ClientError.dbOperationFailure;
import static java.lang.String.format;

public class DataFlowRepository {
    private static final String INSERT_TO_DATA_FLOW_REQUEST = "INSERT INTO data_flow_request (transaction_id, " +
            "consent_artefact_id, data_flow_request) VALUES ($1, $2, $3)";
    private static final String INSERT_TO_DATA_FLOW_REQUEST_KEYS = "INSERT INTO data_flow_request_keys " +
            "(transaction_id, " +
            "key_pairs) VALUES ($1, $2)";
    private static final String GET_KEY_FOR_ID = "SELECT key_pairs FROM data_flow_request_keys WHERE transaction_id =" +
            " $1";
    private static final String SELECT_TRANSACTION_IDS_FROM_DATA_FLOW_REQUEST = "SELECT transaction_id FROM " +
            "data_flow_request WHERE consent_artefact_id = $1";
    private static final String INSERT_HEALTH_DATA_AVAILABILITY = "INSERT INTO data_flow_parts (transaction_id, " +
            "part_number, status) VALUES ($1, $2, $3)";
    private static final String SELECT_DATA_FLOW_REQUEST_FOR_TRANSACTION =
            "SELECT  ca.consent_request_id, dfr.data_flow_request " +
                    "FROM data_flow_request dfr " +
                    "INNER JOIN consent_artefact ca ON dfr.consent_artefact_id=ca.consent_artefact_id " +
                    "WHERE dfr.transaction_id=$1";
    private static final String UPDATE_HEALTH_DATA_AVAILABILITY = "UPDATE data_flow_parts SET status = $1, errors = " +
            "$2 WHERE transaction_id = $3 AND part_number = $4";
    private static final String SELECT_CONSENT_ID = "SELECT consent_artefact_id FROM data_flow_request WHERE " +
            "transaction_id = $1";

    private static final Logger logger = Logger.getLogger(DataFlowRepository.class);
    private final PgPool dbClient;

    public DataFlowRepository(PgPool pgPool) {
        this.dbClient = pgPool;
    }

    public Mono<Void> addDataRequest(String transactionId, String consentId, DataFlowRequest dataFlowRequest) {
        return Mono.create(monoSink -> dbClient.preparedQuery(INSERT_TO_DATA_FLOW_REQUEST)
                .execute(Tuple.of(transactionId, consentId, JsonObject.mapFrom(dataFlowRequest)),
                        handler -> {
                            if (handler.failed()) {
                                monoSink.error(dbOperationFailure("Failed to insert to data flow request"));
                                return;
                            }
                            monoSink.success();
                        }));
    }

    public Mono<Void> addKeys(String transactionId, DataFlowRequestKeyMaterial dataFlowRequestKeyMaterial) {
        return Mono.create(monoSink -> dbClient.preparedQuery(INSERT_TO_DATA_FLOW_REQUEST_KEYS)
                .execute(Tuple.of(transactionId, JsonObject.mapFrom(dataFlowRequestKeyMaterial)),
                        handler -> {
                            if (handler.failed()) {
                                monoSink.error(dbOperationFailure("Failed to insert to data flow request"));
                                return;
                            }
                            monoSink.success();
                        }));
    }

    public Mono<DataFlowRequestKeyMaterial> getKeys(String transactionId) {
        return Mono.create(monoSink -> dbClient.preparedQuery(GET_KEY_FOR_ID)
                .execute(Tuple.of(transactionId),
                        handler -> {
                            if (handler.failed()) {
                                monoSink.error(dbOperationFailure("Failed to fetch encryption keys"));
                                return;
                            }
                            var iterator = handler.result().iterator();
                            if (!iterator.hasNext()) {
                                logger.error(format("Could not found encryption keys for %s", transactionId));
                                monoSink.error(dbOperationFailure("Failed to fetch encryption keys"));
                                return;
                            }
                            var row = iterator.next();
                            var keyPairsJson = (JsonObject) row.getValue("key_pairs");
                            monoSink.success(keyPairsJson.mapTo(DataFlowRequestKeyMaterial.class));
                        }));
    }

    public Mono<String> getTransactionId(String consentArtefactId) {
        return Mono.create(monoSink -> dbClient.preparedQuery(SELECT_TRANSACTION_IDS_FROM_DATA_FLOW_REQUEST)
                .execute(Tuple.of(consentArtefactId),
                        handler -> {
                            if (handler.failed()) {
                                monoSink.error(dbOperationFailure("Failed to get transaction Id from consent Id"));
                                return;
                            }
                            var iterator = handler.result().iterator();
                            if (!iterator.hasNext()) {
                                logger.error(format("No transaction id found for consent artefact %s",
                                        consentArtefactId));
                                monoSink.success();
                                return;
                            }
                            monoSink.success(iterator.next().getString(0));
                        }));
    }

    public Mono<String> getConsentId(String transactionId) {
        return Mono.create(monoSink -> dbClient.preparedQuery(SELECT_CONSENT_ID)
                .execute(Tuple.of(transactionId),
                        handler -> {
                            if (handler.failed()) {
                                monoSink.error(dbOperationFailure("Failed to get consent Id from transaction Id"));
                                return;
                            }
                            var iterator = handler.result().iterator();
                            if (!iterator.hasNext()) {
                                logger.error(format("Could not find consent artefact id for %s", transactionId));
                                monoSink.error(dbOperationFailure("Failed to get consent Id from transaction Id"));
                                return;
                            }
                            monoSink.success(iterator.next().getString(0));
                        }));
    }


    public Mono<Map<String, Object>> retrieveDataFlowRequest(String transactionId) {
        return Mono.create(monoSink -> dbClient.preparedQuery(SELECT_DATA_FLOW_REQUEST_FOR_TRANSACTION)
                .execute(Tuple.of(transactionId),
                        handler -> {
                            if (handler.failed()) {
                                monoSink.error(
                                        dbOperationFailure("Failed to identify data flow request for transaction Id"));
                                return;
                            }
                            var iterator = handler.result().iterator();
                            if (!iterator.hasNext()) {
                                monoSink.error(
                                        dbOperationFailure("Failed to identify data flow request for transaction Id"));
                                return;
                            }
                            Row row = iterator.next();
                            Map<String, Object> flowRequestTransaction = new HashMap<>();
                            flowRequestTransaction.put("consentRequestId", row.getString("consent_request_id"));
                            var jsonObject = (JsonObject) row.getValue("data_flow_request");
                            flowRequestTransaction.put("dataFlowRequest",
                                    jsonObject.mapTo(DataFlowRequest.class));
                            monoSink.success(flowRequestTransaction);
                        }));
    }

    public Mono<Void> insertDataPartAvailability(String transactionId, int partNumber, HealthInfoStatus status) {
        return Mono.create(monoSink -> dbClient.preparedQuery(INSERT_HEALTH_DATA_AVAILABILITY)
                .execute(Tuple.of(transactionId, String.valueOf(partNumber), status.toString()),
                        handler -> {
                            if (handler.failed()) {
                                monoSink.error(dbOperationFailure("Failed to insert health data availability"));
                                return;
                            }
                            monoSink.success();
                        }));
    }

    public Mono<Void> updateDataFlowWithStatus(String transactionId, String dataPartNumber, String allErrors,
                                               HealthInfoStatus status) {
        return Mono.create(monoSink -> dbClient.preparedQuery(UPDATE_HEALTH_DATA_AVAILABILITY)
                .execute(Tuple.of(status.toString(), allErrors, transactionId, dataPartNumber),
                        handler -> {
                            if (handler.failed()) {
                                monoSink.error(dbOperationFailure("Failed to update health data availability"));
                                return;
                            }
                            monoSink.success();
                        }));
    }
}
