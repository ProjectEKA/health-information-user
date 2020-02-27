package in.org.projecteka.hiu.dataprocessor;

import io.vertx.pgclient.PgPool;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class HealthDataRepository {
    private PgPool dbClient;

    public void insertHealthData(String transactionId, String dataPartNumber, String resource) {

    }
}
