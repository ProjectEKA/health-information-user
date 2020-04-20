package in.org.projecteka.hiu.user;

import lombok.Value;

@Value
public class Session {
    String accessToken;
    boolean isVerified;
}
