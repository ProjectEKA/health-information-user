package in.org.projecteka.hiu.consent;

import java.util.Base64;

public class TokenUtils {
    public static String decode(String authorizationHeader) {
        Base64.Decoder decoder = Base64.getDecoder();
        return new String(decoder.decode(authorizationHeader));
    }

    public static String encodeHIUId(String id){
        Base64.Encoder encoder = Base64.getEncoder();
        return new String(encoder.encode(id.getBytes()));

    }
}
