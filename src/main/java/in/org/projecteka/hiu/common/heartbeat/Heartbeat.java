package in.org.projecteka.hiu.common.heartbeat;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import in.org.projecteka.hiu.DatabaseProperties;
import in.org.projecteka.hiu.common.heartbeat.model.HeartbeatResponse;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.TimeoutException;

import static in.org.projecteka.hiu.Error.of;
import static in.org.projecteka.hiu.common.heartbeat.model.Status.DOWN;
import static in.org.projecteka.hiu.common.heartbeat.model.Status.UP;
import static java.time.LocalDateTime.now;
import static java.time.ZoneOffset.UTC;
import static reactor.core.publisher.Mono.just;


@AllArgsConstructor
public class Heartbeat {
    public static final String SERVICE_DOWN = "Service Down";
    private final RabbitMQOptions rabbitMQOptions;
    private final DatabaseProperties databaseProperties;

    public Mono<HeartbeatResponse> getStatus() {
        try {
            return (isRabbitMQUp() && isPostgresUp())
                   ? just(HeartbeatResponse.builder().timeStamp(now(UTC)).status(UP).build())
                   : just(HeartbeatResponse.builder().timeStamp(now(UTC)).status(DOWN).error(of(SERVICE_DOWN)).build());
        } catch (IOException | TimeoutException e) {
            return just(HeartbeatResponse.builder().timeStamp(now(UTC)).status(DOWN).error(of(SERVICE_DOWN)).build());
        }
    }

    private boolean isRabbitMQUp() throws IOException, TimeoutException {
        var factory = new ConnectionFactory();
        factory.setHost(rabbitMQOptions.getHost());
        factory.setPort(rabbitMQOptions.getPort());
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
