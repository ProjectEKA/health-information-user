package in.org.projecteka.hiu;

import in.org.projecteka.hiu.user.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Getter
@Builder
public class GatewayCaller {
    private String username;
    private Boolean isServiceAccount;
    private Role role;
    private boolean verified;
}