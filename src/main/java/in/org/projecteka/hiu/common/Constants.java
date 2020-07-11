package in.org.projecteka.hiu.common;

public class Constants {
    // APIs
    private static final String CURRENT_VERSION = "/v1";
    public static final String PATH_CONSENT_REQUESTS_ON_INIT = CURRENT_VERSION + "/consent-requests/on-init";
    public static final String PATH_CONSENTS_HIU_NOTIFY = CURRENT_VERSION + "/consents/hiu/notify";
    public static final String PATH_CONSENTS_ON_FETCH = CURRENT_VERSION + "/consents/on-fetch";
    public static final String PATH_CONSENTS_ON_FIND = CURRENT_VERSION + "/patients/on-find";
    public static final String PATH_HEALTH_INFORMATION_HIU_ON_REQUEST = CURRENT_VERSION + "/health-information/hiu/on-request";
    public static final String PATH_HEARTBEAT = CURRENT_VERSION + "/heartbeat";
    public static final String PATH_PATIENTS_ON_FIND = CURRENT_VERSION + "/patients/on-find";
    public static final String X_CM_ID = "X-CM-ID";
    public static final String PATH_DATA_TRANSFER = "/data/notification";

    private Constants() {
    }
}