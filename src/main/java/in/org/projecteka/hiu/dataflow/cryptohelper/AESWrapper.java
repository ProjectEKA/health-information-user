package in.org.projecteka.hiu.dataflow.cryptohelper;

import org.bouncycastle.util.encoders.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.Key;
import java.security.spec.KeySpec;

public class AESWrapper {
    private static String IV = "IV_VALUE_16_BYTE";
    private static String SALT = "SALT_VALUE";

    public String decodeAndDecrypt(String encrypted, String password) throws Exception {
        byte[] decodedValue = Base64.decode(getBytes(encrypted));
        Cipher c = getCipher(Cipher.DECRYPT_MODE, password);
        byte[] decValue = c.doFinal(decodedValue);
        return new String(decValue);
    }

    private byte[] getBytes(String str) throws UnsupportedEncodingException {
        return str.getBytes("UTF-8");
    }

    private Cipher getCipher(int mode, String password) throws Exception {
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        byte[] iv = getBytes(IV);
        c.init(mode, generateKey(password), new IvParameterSpec(iv));
        return c;
    }

    private Key generateKey(String sharedKey) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        char[] password = sharedKey.toCharArray();
        byte[] salt = getBytes(SALT);

        KeySpec spec = new PBEKeySpec(password, salt, 65536, 128);
        SecretKey tmp = factory.generateSecret(spec);
        byte[] encoded = tmp.getEncoded();
        return new SecretKeySpec(encoded, "AES");
    }
}
