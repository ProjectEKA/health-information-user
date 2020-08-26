package in.org.projecteka.hiu.common;

public class Constants {
    // APIs
    private static final String CURRENT_VERSION = "/v0.5";
    public static final String PATH_CONSENT_REQUESTS_ON_INIT = CURRENT_VERSION + "/consent-requests/on-init";
    public static final String PATH_CONSENTS_HIU_NOTIFY = CURRENT_VERSION + "/consents/hiu/notify";
    public static final String PATH_CONSENTS_ON_FETCH = CURRENT_VERSION + "/consents/on-fetch";
    public static final String PATH_CONSENTS_ON_FIND = CURRENT_VERSION + "/patients/on-find";
    public static final String PATH_HEALTH_INFORMATION_HIU_ON_REQUEST = CURRENT_VERSION + "/health-information/hiu/on-request";
    public static final String PATH_HEARTBEAT = CURRENT_VERSION + "/heartbeat";
    public static final String PATH_PATIENTS_ON_FIND = CURRENT_VERSION + "/patients/on-find";
    public static final String X_CM_ID = "X-CM-ID";
    public static final String PATH_DATA_TRANSFER = "/data/notification";
    public static final String EMPTY_STRING = "";
    public static final String APP_PATH_PATIENT_CONSENT_REQUEST = "/v1/patient/consent-request";
    public static final String APP_PATH_HIU_CONSENT_REQUESTS = "/v1/hiu/consent-requests";
    public static final String PATIENT_REQUESTED_PURPOSE_CODE = "PATRQT";
    public static final String API_PATH_FETCH_PATIENT_HEALTH_INFO = "/v1/patient/health-information/fetch";
    public static final String API_PATH_GET_INFO_FOR_SINGLE_CONSENT_REQUEST = "/health-information/fetch/{consent-request-id}";
    public static final String API_PATH_GET_ATTACHMENT = "/health-information/fetch/{consent-request-id}/attachments/{file-name}";
    public static final String CM_API_PATH_GET_ATTACHMENT = "/v1/patient/health-information/fetch/{consent-request-id}/attachments/{file-name}";
    public static final String API_PATH_GET_HEALTH_INFO_STATUS = "/v1/patient/health-information/status";
    public static final String VALIDATE_TOKEN = "/v1/account/token";
    public static final String INTERNAL_PATH_PATIENT_CARE_CONTEXT_INFO = "/internal/patient/hip/data-transfer-status";
    public static final String PATH_CONSENT_REQUEST_ON_STATUS = CURRENT_VERSION + "/consent-requests/on-status";

    public static final String STATUS = "status";
    public static final String DELIMITER = "@";

    private Constants() {
    }

    public static String getCmSuffix(String patientId) {
        return patientId.split(DELIMITER)[1];
    }
}
