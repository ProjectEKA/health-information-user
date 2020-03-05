package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestKeyMaterial;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static in.org.projecteka.hiu.ClientError.dbOperationFailure;

public class DataFlowRepository {
    private static final String INSERT_TO_DATA_FLOW_REQUEST = "INSERT INTO data_flow_request (transaction_id, " +
            "consent_artefact_id, data_flow_request) VALUES ($1, $2, $3)";
    private static final String INSERT_TO_DATA_FLOW_REQUEST_KEYS = "INSERT INTO data_flow_request_keys (transaction_id, " +
            "key_pairs) VALUES ($1, $2)";
    private static final String GET_KEY_FOR_ID = "SELECT key_pairs " +
            "FROM data_flow_request_keys WHERE transaction_id = $1";;
    private static final String SELECT_TRANSACTION_IDS_FROM_DATA_FLOW_REQUEST = "SELECT transaction_id FROM " +
            "data_flow_request WHERE data_flow_request -> 'consent' ->> 'id' = $1";
    private static final String INSERT_HEALTH_DATA_AVAILABILITY = "INSERT INTO data_flow_parts (transaction_id, " +
            "part_number) VALUES ($1, $2)";
    private static final String SELECT_DATA_FLOW_REQUEST_FOR_TRANSACTION =
            "SELECT  ca.consent_request_id, dfr.data_flow_request " +
            "FROM data_flow_request dfr " +
            "INNER JOIN consent_artefact ca ON dfr.consent_artefact_id=ca.consent_artefact_id " +
            "WHERE dfr.transaction_id=$1";

    private PgPool dbClient;

    public DataFlowRepository(PgPool pgPool) {
        this.dbClient = pgPool;
    }

    public Mono<Void> addDataRequest(String transactionId, String consentId, DataFlowRequest dataFlowRequest) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(
                        INSERT_TO_DATA_FLOW_REQUEST,
                        Tuple.of(transactionId, consentId, JsonObject.mapFrom(dataFlowRequest)),
                        handler -> {
                            if (handler.failed())
                                monoSink.error(new Exception("Failed to insert to data flow request"));
                            else
                                monoSink.success();
                        })
        );
    }

    public Mono<Void> addKeys(String transactionId, DataFlowRequestKeyMaterial dataFlowRequestKeyMaterial) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(
                        INSERT_TO_DATA_FLOW_REQUEST_KEYS,
                        Tuple.of(transactionId, JsonObject.mapFrom(dataFlowRequestKeyMaterial)),
                        handler -> {
                            if (handler.failed())
                                monoSink.error(new Exception("Failed to insert to data flow request"));
                            else
                                monoSink.success();
                        })
        );
    }

    public Mono<DataFlowRequestKeyMaterial> getKeys(String transactionId) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(
                        GET_KEY_FOR_ID,
                        Tuple.of(transactionId),
                        handler -> {
                            if (handler.failed())
                                monoSink.error(dbOperationFailure("Failed to fetch consent request"));
                            else {
                                RowSet<Row> rows = handler.result();
                                DataFlowRequestKeyMaterial keyPairs = null;
                                for (Row row : rows) {
                                    JsonObject keyPairsJson = (JsonObject) row.getValue("key_pairs");
                                    keyPairs = keyPairsJson.mapTo(DataFlowRequestKeyMaterial.class);
                                }
                                monoSink.success(keyPairs);
                            }}));
    }

    public Mono<String> getTransactionId(String consentArtefactId) {
        return Mono.create(monoSink -> dbClient.preparedQuery(SELECT_TRANSACTION_IDS_FROM_DATA_FLOW_REQUEST,
                Tuple.of(consentArtefactId),
                handler -> {
                    if (handler.failed()) {
                        monoSink.error(new Exception("Failed to get transaction Id from consent Id"));
                    } else {
                        monoSink.success(handler.result().iterator().next().getString(0));
                    }
                }));
    }


    public Mono<Map<String, Object>> retrieveDataFlowRequest(String transactionId) {
        return Mono.create(monoSink -> dbClient.preparedQuery(SELECT_DATA_FLOW_REQUEST_FOR_TRANSACTION,
                Tuple.of(transactionId),
                handler -> {
                    if (handler.failed()) {
                        monoSink.error(new Exception("Failed to identify data flow request for transaction Id"));
                    } else {
                        RowSet<Row> results = handler.result();
                        if (results.iterator().hasNext()) {
                            Row row = results.iterator().next();
                            Map<String, Object> flowRequestTransaction = new HashMap<>();
                            flowRequestTransaction.put("consentRequestId", row.getString("consent_request_id"));
                            JsonObject jsonObject = (JsonObject) row.getValue("data_flow_request");
                            flowRequestTransaction.put("dataFlowRequest", jsonObject.mapTo(DataFlowRequest.class));
                            monoSink.success(flowRequestTransaction);
                        } else {
                            monoSink.error(new Exception("Failed to identify data flow request for transaction Id"));
                        }
                    }
                }));
    }

    public Mono<Boolean> insertDataPartAvailability(String transactionId, int partNumber) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(
                        INSERT_HEALTH_DATA_AVAILABILITY,
                        Tuple.of(transactionId, String.valueOf(partNumber)),
                        handler -> {
                            if (handler.failed())
                                monoSink.error(new Exception("Failed to insert health data availability"));
                            else
                                monoSink.success(true);
                        })
        );
    }
}
