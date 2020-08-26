package in.org.projecteka.hiu.common.heartbeat;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import in.org.projecteka.hiu.DatabaseProperties;
import in.org.projecteka.hiu.common.heartbeat.model.HeartbeatResponse;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.TimeoutException;

import static in.org.projecteka.hiu.Error.of;
import static in.org.projecteka.hiu.common.heartbeat.model.Status.DOWN;
import static in.org.projecteka.hiu.common.heartbeat.model.Status.UP;
import static java.lang.String.format;
import static java.time.LocalDateTime.now;
import static java.time.ZoneOffset.UTC;
import static reactor.core.publisher.Mono.just;


@AllArgsConstructor
public class Heartbeat {
    private static final Logger logger = LoggerFactory.getLogger(Heartbeat.class);
    public static final String SERVICE_DOWN = "Service Down";
    private final RabbitMQOptions rabbitMQOptions;
    private final DatabaseProperties databaseProperties;
    private final CacheHealth cacheHealth;

    public Mono<HeartbeatResponse> getStatus() {
        try {
            if (cacheHealth.isUp() && isRabbitMQUp() && isPostgresUp()) {
                return just(HeartbeatResponse.builder().timeStamp(now(UTC)).status(UP).build());
            }
            return just(HeartbeatResponse.builder().timeStamp(now(UTC)).status(DOWN).error(of(SERVICE_DOWN)).build());
        } catch (IOException | TimeoutException e) {
            logger.error(format("Heartbeat is not healthy with failure: %s", e.getMessage()), e);
            return just(HeartbeatResponse.builder().timeStamp(now(UTC)).status(DOWN).error(of(SERVICE_DOWN)).build());
        }
    }

    private boolean isRabbitMQUp() throws IOException, TimeoutException {
        var factory = new ConnectionFactory();
        factory.setHost(rabbitMQOptions.getHost());
        factory.setPort(rabbitMQOptions.getPort());
        factory.setUsername(rabbitMQOptions.getUsername());
        factory.setPassword(rabbitMQOptions.getPassword());
        try (Connection connection = factory.newConnection()) {
            return connection.isOpen();
        }
    }

    private boolean isPostgresUp() throws IOException {
        return checkConnection(databaseProperties.getHost(), databaseProperties.getPort());
    }

    private boolean checkConnection(String host, int port) throws IOException {
        SocketAddress socketAddress = new InetSocketAddress(host, port);
        try (Socket socket = new Socket()) {
            socket.connect(socketAddress);
            return socket.isConnected();
        }
    }
}
