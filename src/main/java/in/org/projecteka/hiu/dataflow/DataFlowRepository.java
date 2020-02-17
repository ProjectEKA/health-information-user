package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import in.org.projecteka.hiu.dataflow.model.Entry;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import reactor.core.publisher.Mono;

import java.util.List;

public class DataFlowRepository {
    private static final String INSERT_TO_DATA_FLOW_REQUEST = "INSERT INTO data_flow_request (transaction_id, " +
            "data_flow_request) VALUES ($1, $2)";
    private static final String INSERT_TO_DATA_FLOW_RESPONSE = "INSERT INTO data_flow_response (transaction_id, " +
            "data_flow_response) VALUES ($1, $2)";
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
}
