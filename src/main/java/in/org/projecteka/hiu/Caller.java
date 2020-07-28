package in.org.projecteka.hiu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Optional;

@AllArgsConstructor
@Getter
@Builder
public class Caller {
    private final String username;
    private final Boolean isServiceAccount;
    private final String role;
    private final boolean verified;
  
    public Optional<String> getRole() {
        return Optional.ofNullable(role);
    }
}
