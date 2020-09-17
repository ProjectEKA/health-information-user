package in.org.projecteka.hiu.user.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = AuthModeDeserializer.class)
public enum AuthMode {
    MOBILE_OTP,
    AADHAAR_OTP,
    INVALID_AUTHMODE,
    DEMOGRAPHICS,
    MOBILE_APP;

    public static AuthMode fromText(String authMode) {
        if (authMode.equals("MOBILE_OTP")
                || authMode.equals("AADHAAR_OTP")
                || authMode.equals("MOBILE_APP")
                || authMode.equals("DEMOGRAPHICS")) {
            return AuthMode.valueOf(authMode);
        } else {
            return AuthMode.INVALID_AUTHMODE;
        }
    }
}
