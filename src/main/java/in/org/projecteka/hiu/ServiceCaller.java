package in.org.projecteka.hiu;

import in.org.projecteka.hiu.user.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
@Getter
@Builder
public class ServiceCaller {
    private final String clientId;
    List<Role> roles;
}