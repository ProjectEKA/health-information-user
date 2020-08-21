package in.org.projecteka.hiu;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "hiu.database")
@Getter
@Setter
@AllArgsConstructor
public class DatabaseProperties {
    private final String host;
    private final int port;
    private final String schema;
    private final String user;
    private final String password;
    private final int poolSize;
    private final boolean replicaReadEnabled;
    private final Replica replica;

    public Replica getReplica() {
        return replica != null && replicaReadEnabled
                ? replica
                : new Replica(host, port, user, password, getReadPoolSize());
    }

    private int getReadPoolSize() {
        return poolSize / 2 + poolSize % 2;
    }

    public int getPoolSize() {
        return replica != null && replicaReadEnabled
                ? poolSize
                : poolSize / 2;
    }
}
