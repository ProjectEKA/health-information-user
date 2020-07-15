package in.org.projecteka.hiu.common.heartbeat;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import in.org.projecteka.hiu.DatabaseProperties;
import in.org.projecteka.hiu.common.heartbeat.model.HeartbeatResponse;
import in.org.projecteka.hiu.common.heartbeat.model.Status;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeoutException;

import static in.org.projecteka.hiu.Error.serviceDownError;


@AllArgsConstructor
public class Heartbeat {
    private final RabbitMQOptions rabbitMQOptions;
    private final DatabaseProperties databaseProperties;

    public Mono<HeartbeatResponse> getStatus() {
        try {
            return (isRabbitMQUp() && isPostgresUp())
                    ? Mono.just(HeartbeatResponse.builder()
                    .timeStamp(LocalDateTime.now(ZoneOffset.UTC))
                    .status(Status.UP)
                    .build())
                    : Mono.just(HeartbeatResponse.builder()
                    .timeStamp(LocalDateTime.now(ZoneOffset.UTC))
                    .status(Status.DOWN)
                    .error(serviceDownError("Service Down"))
                    .build());
        } catch (IOException | TimeoutException e) {
            return Mono.just(HeartbeatResponse.builder()
                    .timeStamp(LocalDateTime.now(ZoneOffset.UTC))
                    .status(Status.DOWN)
                    .error(serviceDownError("Service Down"))
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

    private boolean isPostgresUp() throws IOException {
        return checkConnection(databaseProperties.getHost(),databaseProperties.getPort());
    }

    private boolean checkConnection(String host, int port) throws IOException {
        boolean isAlive;
        SocketAddress socketAddress = new InetSocketAddress(host, port);
        Socket socket = new Socket();
        socket.connect(socketAddress);
        isAlive = socket.isConnected();
        socket.close();
        return isAlive;
    }
}
