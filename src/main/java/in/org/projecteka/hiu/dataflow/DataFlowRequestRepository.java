package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestKeyMaterial;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import reactor.core.publisher.Mono;

public class DataFlowRequestRepository {
    private static final String INSERT_TO_DATA_FLOW_REQUEST = "INSERT INTO data_flow_request (transaction_id, " +
            "data_flow_request) VALUES ($1, $2)";
    private static final String INSERT_TO_DATA_FLOW_REQUEST_KEYS = "INSERT INTO data_flow_request_keys (transaction_id, " +
            "key_pairs) VALUES ($1, $2)";
    private PgPool dbClient;

    public DataFlowRequestRepository(PgPool pgPool) {
        this.dbClient = pgPool;
    }

    public Mono<Void> add(String transactionId, DataFlowRequest dataFlowRequest) {
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
}
