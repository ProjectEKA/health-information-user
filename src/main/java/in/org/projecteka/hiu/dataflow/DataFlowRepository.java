package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import in.org.projecteka.hiu.dataflow.model.Entry;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class DataFlowRepository {
    private static final String INSERT_TO_DATA_FLOW_REQUEST = "INSERT INTO data_flow_request (transaction_id, " +
            "data_flow_request) VALUES ($1, $2)";
    private static final String INSERT_HEALTH_INFORMATION = "INSERT INTO health_information " +
            "(transaction_id, health_information) VALUES ($1, $2)";
    private static final String SELECT_CONSENT_IDS_FROM_CONSENT_ARTIFACT = "SELECT consent_artefact_id, " +
            "consent_artefact -> 'hip' ->> 'id' as hipId, consent_artefact -> 'hip' ->> 'name' as hipName, " +
            "consent_artefact -> 'requester' ->> 'name' as requester FROM " +
            "consent_artefact WHERE consent_request_id=$1";
    private static final String SELECT_TRANSACTION_IDS_FROM_DATA_FLOW_REQUEST = "SELECT transaction_id FROM " +
            "data_flow_request WHERE data_flow_request -> 'consent' ->> 'id' = $1";
    private PgPool dbClient;

    public DataFlowRepository(PgPool pgPool) {
        this.dbClient = pgPool;
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

    public Mono<Void> insertHealthInformation(String transactionId, Entry entry) {
        return Mono.create(monoSink ->
                dbClient.preparedQuery(
                        INSERT_HEALTH_INFORMATION,
                        Tuple.of(transactionId, JsonObject.mapFrom(entry)),
                        handler -> {
                            if (handler.failed())
                                monoSink.error(new Exception("Failed to insert health information"));
                            else
                                monoSink.success();
                        })
        );
    }

    public Mono<List<Map<String, String>>> getConsentDetails(String consentRequestId) {
        return Mono.create(monoSink -> dbClient.preparedQuery(SELECT_CONSENT_IDS_FROM_CONSENT_ARTIFACT,
                Tuple.of(consentRequestId),
                handler -> {
                    if (handler.failed()) {
                        monoSink.error(new Exception("Failed to get consent id from consent request Id"));
                    } else {
                        RowSet<Row> results = handler.result();
                        List<Map<String, String>> consentDetails = new ArrayList<>();
                        for (Row row : results) {
                            Map<String, String> map = new HashMap<>();
                            map.put("consentId", row.getString(0));
                            map.put("hipId", row.getString(1));
                            map.put("hipName", row.getString(2));
                            map.put("requester", row.getString(3));
                            consentDetails.add(map);
                        }
                        monoSink.success(consentDetails);
                    }
                }));
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


}
