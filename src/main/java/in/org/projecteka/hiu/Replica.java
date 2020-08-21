package in.org.projecteka.hiu;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@Setter
public class Replica {
    private String host;
    private int port;
    private String user;
    private String password;
    private int poolSize;
}
