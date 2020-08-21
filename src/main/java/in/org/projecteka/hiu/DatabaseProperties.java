package in.org.projecteka.hiu;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "hiu.database")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@AllArgsConstructor
public class DatabaseProperties {
    private String host;
    private int port;
    private String schema;
    private String user;
    private String password;
    private int poolSize;
    private boolean replicaReadEnabled;
    private Replica replica;

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
