package in.org.projecteka.hiu.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@AllArgsConstructor
public class User {
    private String username;
    private String password;
    private Role role;
}

