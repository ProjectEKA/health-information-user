package in.org.projecteka.hiu;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Optional;

@AllArgsConstructor
@Getter
public class Caller {
    private String userName;
    private Boolean isServiceAccount;
    private String role;

    public Optional<String> getRole() {
        return Optional.ofNullable(role);
    }
}
