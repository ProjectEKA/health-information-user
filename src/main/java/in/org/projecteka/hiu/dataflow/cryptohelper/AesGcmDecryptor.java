package in.org.projecteka.hiu.dataflow.cryptohelper;

import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import java.nio.charset.Charset;
import java.security.SecureRandom;

public class AesGcmDecryptor {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    public static int MacBitSize = 128;
    public static String decrypt(byte[] encryptedBytes, byte[] key, byte[] iv) {
        String decryptedData = "";
        try {
            GCMBlockCipher cipher = new GCMBlockCipher(new AESEngine());
            AEADParameters parameters =
                    new AEADParameters(new KeyParameter(key), MacBitSize, iv, null);

            cipher.init(false, parameters);
            byte[] plainBytes = new byte[cipher.getOutputSize(encryptedBytes.length)];
            int retLen = cipher.processBytes
                    (encryptedBytes, 0, encryptedBytes.length, plainBytes, 0);
            cipher.doFinal(plainBytes, retLen);

            decryptedData = new String(plainBytes, Charset.forName("UTF-8"));
        } catch (IllegalArgumentException | IllegalStateException |
                DataLengthException | InvalidCipherTextException ex) {
            System.out.println(ex.getMessage());
        }
        return decryptedData;
    }
}
