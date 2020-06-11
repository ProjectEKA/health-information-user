package in.org.projecteka.hiu.common;

import in.org.projecteka.hiu.ClientError;

import java.util.HashMap;
import java.util.Map;

public class ErrorMappings {
    
    private static final Map<Integer, Throwable> codeErrorMapping = new HashMap<>();

    private ErrorMappings() {}

    static {
        codeErrorMapping.put(1006, ClientError.patientNotFound());
    }

    public static Throwable get(Integer errorCode) {
        return codeErrorMapping.getOrDefault(errorCode, ClientError.unknownError());
    }
}