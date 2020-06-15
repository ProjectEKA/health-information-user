package in.org.projecteka.hiu;

import in.org.projecteka.hiu.user.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
@Getter
@Builder
public class GatewayCaller {
    private String username;
    private Boolean isServiceAccount;
    List<Role> roles;
    private boolean verified;
}