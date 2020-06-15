package in.org.projecteka.hiu.user;

import java.util.Map;
import java.util.Optional;

public enum Role {
    DOCTOR,
    ADMIN,
    GATEWAY;

    public static Optional<Role> valueOfIgnoreCase(String mayBeRole) {
        var role = Map.of(DOCTOR.name().toLowerCase(), DOCTOR,
                ADMIN.name().toLowerCase(), ADMIN,
                GATEWAY.name().toLowerCase(), GATEWAY);
        return mayBeRole == null
                ? Optional.empty()
                : Optional.ofNullable(role.getOrDefault(mayBeRole.toLowerCase(), null));
    }
}
