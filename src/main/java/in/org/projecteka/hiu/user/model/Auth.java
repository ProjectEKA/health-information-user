package in.org.projecteka.hiu.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@AllArgsConstructor
@Builder
public class Auth {
    String transactionId;
    AuthMode mode;
    Meta meta;
}
