package in.org.projecteka.hiu.common.heartbeat;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import in.org.projecteka.hiu.DatabaseProperties;
import in.org.projecteka.hiu.common.heartbeat.model.HeartbeatResponse;
import in.org.projecteka.hiu.common.heartbeat.model.Status;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

import static in.org.projecteka.hiu.Error.serviceDownError;


@AllArgsConstructor
public class Heartbeat {
    private RabbitMQOptions rabbitMQOptions;
    private DatabaseProperties databaseProperties;

    public Mono<HeartbeatResponse> getStatus() {
        try {
            return (isRabbitMQUp() && isPostgresUp())
                    ? Mono.just(HeartbeatResponse.builder()
                    .timeStamp(Instant.now().toString())
                    .status(Status.UP)
                    .build())
                    : Mono.just(HeartbeatResponse.builder()
                    .timeStamp(Instant.now().toString())
                    .status(Status.DOWN)
                    .error(serviceDownError("Service Down"))
                    .build());
        } catch (IOException | TimeoutException | InterruptedException e) {
            return Mono.just(HeartbeatResponse.builder()
                    .timeStamp(Instant.now().toString())
                    .status(Status.DOWN)
                    .error(serviceDownError(e.getMessage()))
                    .build());
        }

    }

    private boolean isRabbitMQUp() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitMQOptions.getHost());
        factory.setPort(rabbitMQOptions.getPort());
        Connection connection = factory.newConnection();
        return connection.isOpen();
    }

    private boolean isPostgresUp() throws IOException, InterruptedException {
        String cmd = String.format("pg_isready -h %s -p %s", databaseProperties.getHost(), databaseProperties.getPort());
        Runtime run = Runtime.getRuntime();
        Process pr = run.exec(cmd);
        int exitValue = pr.waitFor();
        return exitValue == 0;
    }
}
