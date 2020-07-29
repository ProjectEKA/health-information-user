package in.org.projecteka.hiu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.Optional;

@AllArgsConstructor
@Value
@Builder
public class Caller {
    String username;
    Boolean isServiceAccount;
    String role;
    boolean verified;
  
    public Optional<String> getRole() {
        return Optional.ofNullable(role);
    }
}
