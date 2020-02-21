package in.org.projecteka.hiu.dataflow.cryptohelper;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;

import javax.crypto.KeyAgreement;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;

public class CryptoHelper {
    public static String generateRandomKey() {
        byte[] salt = new byte[32];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        return getBase64String(salt);
    }

    public static byte [] savePublicKey (PublicKey key) throws Exception
    {
        ECPublicKey eckey = (ECPublicKey)key;
        return eckey.getQ().getEncoded();
    }

    public static byte [] savePrivateKey (PrivateKey key) throws Exception
    {
        ECPrivateKey eckey = (ECPrivateKey)key;
        return eckey.getD().toByteArray();
    }

    private static String getSHA(String input) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] textBytes = input.getBytes("iso-8859-1");
        md.update(textBytes, 0, textBytes.length);
        byte[] sha1hash = md.digest();
        return convertToHex(sha1hash);
    }

    private static String convertToHex(byte[] data) {
        StringBuilder buf = new StringBuilder();
        for (byte b : data) {
            int halfbyte = (b >>> 4) & 0x0F;
            int two_halfs = 0;
            do {
                buf.append((0 <= halfbyte) && (halfbyte <= 9) ? (char) ('0' + halfbyte) : (char) ('a' + (halfbyte - 10)));
                halfbyte = b & 0x0F;
            } while (two_halfs++ < 1);
        }
        return buf.toString();
    }

    public static KeyPair generateKeyPair() throws NoSuchProviderException, NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        Security.addProvider(new BouncyCastleProvider());
        KeyPairGenerator kpgen = KeyPairGenerator.getInstance("ECDH", "BC");
        kpgen.initialize(new ECGenParameterSpec("prime192v1"), new SecureRandom());
        return kpgen.generateKeyPair();
    }

    public static String getBase64String(byte[] value){

        return new String(org.bouncycastle.util.encoders.Base64.encode(value));
    }

    public static byte[] getBytesForBase64String(String value){
        return org.bouncycastle.util.encoders.Base64.decode(value);
    }

    private static PrivateKey loadPrivateKey (byte [] data) throws Exception
    {
        ECParameterSpec params = ECNamedCurveTable.getParameterSpec("prime192v1");
        ECPrivateKeySpec prvkey = new ECPrivateKeySpec(new BigInteger(data), params);
        KeyFactory kf = KeyFactory.getInstance("ECDH", "BC");
        return kf.generatePrivate(prvkey);
    }

    private static PublicKey loadPublicKey (byte [] data) throws Exception
    {
        KeyFactory ecKeyFac = KeyFactory.getInstance("EC", "BC");
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(data);
        PublicKey publicKey2 = ecKeyFac.generatePublic(x509EncodedKeySpec);
        return publicKey2;
    }

    private static String doECDH (byte[] dataPrv, byte[] dataPub) throws Exception
    {
        KeyAgreement ka = KeyAgreement.getInstance("ECDH", "BC");
        ka.init(loadPrivateKey(dataPrv));
        ka.doPhase(loadPublicKey(dataPub), true);
        byte [] secret = ka.generateSecret();
        return getBase64String(secret);
    }

    public static String decrypt(String receiverPrivateKey,
                                 String senderPublicKey,
                                 String randomKeySender,
                                 String randomKeyReceiver,
                                 String encryptedMessage) throws Exception {
        String sharedKey = doECDH(getBytesForBase64String(receiverPrivateKey)
                , getBytesForBase64String(senderPublicKey));
        var sharedSecret = getSHA(sharedKey + randomKeySender + randomKeyReceiver);
        AESWrapper aesWrapper = new AESWrapper();
        return aesWrapper.decodeAndDecrypt(encryptedMessage, sharedSecret);
    }

}
