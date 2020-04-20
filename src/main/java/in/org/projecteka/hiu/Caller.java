package in.org.projecteka.hiu;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Optional;

@AllArgsConstructor
@Getter
public class Caller {
    private final String username;
    private final Boolean isServiceAccount;
    private final String role;

    public Optional<String> getRole() {
        return Optional.ofNullable(role);
    }
}
