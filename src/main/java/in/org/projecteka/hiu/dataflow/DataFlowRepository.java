package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.consent.model.ConsentRequest;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestKeyMaterial;
import in.org.projecteka.hiu.dataflow.model.Entry;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import reactor.core.publisher.Mono;

import java.util.List;

import static in.org.projecteka.hiu.ClientError.dbOperationFailure;

public class DataFlowRepository {
    private static final String INSERT_TO_DATA_FLOW_REQUEST = "INSERT INTO data_flow_request (transaction_id, " +
            "data_flow_request) VALUES ($1, $2)";
    private static final String INSERT_TO_DATA_FLOW_RESPONSE = "INSERT INTO data_flow_response (transaction_id, " +
            "data_flow_response) VALUES ($1, $2)";
    private static final String INSERT_TO_DATA_FLOW_REQUEST_KEYS = "INSERT INTO data_flow_request_keys (transaction_id, " +
            "key_pairs) VALUES ($1, $2)";
    private static final String GET_KEY_FOR_ID = "SELECT key_pairs " +
            "FROM data_flow_request_keys WHERE transaction_id = $1";;
    private PgPool dbClient;

    public DataFlowRepository(PgPool pgPool) {
        this.dbClient = pgPool;
    }

    private static JsonArray reformatJsonArray(List<Entry> entries) {
        JsonArray entryElements = new JsonArray();
        for (var entry : entries) {
            JsonObject jsonObject = JsonObject.mapFrom(entry);
            entryElements.add(jsonObject);
        }
        return entryElements;
    }

    public Mono<Void> addDataRequest(String transactionId, DataFlowRequest dataFlowRequest) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(
                        INSERT_TO_DATA_FLOW_REQUEST,
                        Tuple.of(transactionId, JsonObject.mapFrom(dataFlowRequest)),
                        handler -> {
                            if (handler.failed())
                                monoSink.error(new Exception("Failed to insert to data flow request"));
                            else
                                monoSink.success();
                        })
        );
    }

    public Mono<Void> addDataResponse(String transactionId, List<Entry> entries) {
        return Mono.create(monoSink ->
                {
                    dbClient.preparedQuery(
                            INSERT_TO_DATA_FLOW_RESPONSE,
                            Tuple.of(transactionId, reformatJsonArray(entries)),
                            handler -> {
                                if (handler.failed())
                                    monoSink.error(new Exception("Failed to insert to data flow response"));
                                else
                                    monoSink.success();
                            });
                }
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
                            }
                        }
                )
        );
    }
}
