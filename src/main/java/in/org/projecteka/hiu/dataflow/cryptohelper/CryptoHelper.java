package in.org.projecteka.hiu.dataflow.cryptohelper;

import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;

import javax.crypto.KeyAgreement;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

public class CryptoHelper {

    public static final String ALGORITHM = "ECDH";
    public static final String CURVE = "curve25519";
    public static final String PROVIDER = BouncyCastleProvider.PROVIDER_NAME;

    public CryptoHelper(){
        Security.addProvider(new BouncyCastleProvider());
    }

    public String generateRandomKey() {
        byte[] salt = new byte[32];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        return getBase64String(salt);
    }

    public byte [] savePublicKey (PublicKey key) throws Exception
    {
        ECPublicKey eckey = (ECPublicKey)key;
        return eckey.getQ().getEncoded(false);
    }

    public static byte [] savePrivateKey (PrivateKey key) throws Exception
    {
        ECPrivateKey eckey = (ECPrivateKey)key;
        return eckey.getD().toByteArray();
    }

    public KeyPair generateKeyPair() throws NoSuchProviderException, NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        Security.addProvider(new BouncyCastleProvider());
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(CryptoHelper.ALGORITHM, CryptoHelper.PROVIDER);
        X9ECParameters ecParameters = CustomNamedCurves.getByName(CryptoHelper.CURVE);
        ECParameterSpec ecSpec=new ECParameterSpec(ecParameters.getCurve(), ecParameters.getG(),
                ecParameters.getN(), ecParameters.getH(), ecParameters.getSeed());

        keyPairGenerator.initialize(ecSpec, new SecureRandom());
        return keyPairGenerator.generateKeyPair();
    }

    private PrivateKey loadPrivateKey (byte [] data) throws Exception
    {
        X9ECParameters ecP = CustomNamedCurves.getByName(CryptoHelper.CURVE);
        ECParameterSpec params=new ECParameterSpec(ecP.getCurve(), ecP.getG(),
                ecP.getN(), ecP.getH(), ecP.getSeed());
        ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(new BigInteger(data), params);
        KeyFactory kf = KeyFactory.getInstance(CryptoHelper.ALGORITHM, CryptoHelper.PROVIDER);
        return kf.generatePrivate(privateKeySpec);
    }

    private PublicKey loadPublicKey (byte [] data) throws Exception
    {
        KeyFactory ecKeyFac = KeyFactory.getInstance(CryptoHelper.ALGORITHM, CryptoHelper.PROVIDER);
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(data);
        PublicKey publicKey2 = ecKeyFac.generatePublic(x509EncodedKeySpec);
        return publicKey2;
    }

    private String doECDH (byte[] dataPrv, byte[] dataPub) throws Exception
    {
        KeyAgreement ka = KeyAgreement.getInstance(CryptoHelper.ALGORITHM, CryptoHelper.PROVIDER);
        ka.init(loadPrivateKey(dataPrv));
        ka.doPhase(loadPublicKey(dataPub), true);
        byte [] secret = ka.generateSecret();
        return getBase64String(secret);
    }

    public String decrypt(String receiverPrivateKey,
                          String senderPublicKey,
                          String randomKeySender,
                          String randomKeyReceiver,
                          String encryptedMessage) throws Exception {
        String sharedKey = doECDH(getBytesForBase64String(receiverPrivateKey)
                , getBytesForBase64String(senderPublicKey));
        byte[] xorOfRandoms = xorOfRandom(randomKeySender, randomKeyReceiver);
        byte[] salt = Arrays.copyOfRange(xorOfRandoms, 0, 20);
        byte[] iv = Arrays.copyOfRange(xorOfRandoms, xorOfRandoms.length - 12, xorOfRandoms.length);

        HKDFBytesGenerator hkdfBytesGenerator = new HKDFBytesGenerator(new SHA256Digest());
        HKDFParameters hkdfParameters = new HKDFParameters(getBytesForBase64String(sharedKey), salt, null);
        hkdfBytesGenerator.init(hkdfParameters);
        byte[] aesKey = new byte[32];
        hkdfBytesGenerator.generateBytes(aesKey, 0, 32);

        return AesGcmDecryptor.decrypt(getBytesForBase64String(encryptedMessage), aesKey, iv);
    }

    private byte [] xorOfRandom(String randomKeySender, String randomKeyReceiver)
    {
        byte[] randomSender = getBytesForBase64String(randomKeySender);
        byte[] randomReceiver = getBytesForBase64String(randomKeyReceiver);

        byte[] out = new byte[randomSender.length];
        for (int i = 0; i < randomSender.length; i++) {
            out[i] = (byte) (randomSender[i] ^ randomReceiver[i%randomReceiver.length]);
        }
        return out;
    }

    public String getBase64String(byte[] value){

        return new String(org.bouncycastle.util.encoders.Base64.encode(value));
    }

    public byte[] getBytesForBase64String(String value){
        return org.bouncycastle.util.encoders.Base64.decode(value);
    }

}
