package in.org.projecteka.hiu.user;

public enum Role {
    DOCTOR,
    ADMIN,
    GATEWAY;

    public static Role valueOfIgnoreCase(String mayBeRole) {
        return  mayBeRole.equalsIgnoreCase(GATEWAY.name()) ? GATEWAY : null;
    }
}
