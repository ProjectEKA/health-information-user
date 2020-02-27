package in.org.projecteka.hiu.dataflow.cryptohelper;

import in.org.projecteka.hiu.dataflow.model.DataFlowRequestKeyMaterial;
import in.org.projecteka.hiu.dataflow.model.KeyMaterial;
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

import java.security.SecureRandom;
import java.security.Security;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyFactory;
import java.security.NoSuchProviderException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidAlgorithmParameterException;
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

    public byte [] getEncodedPublicKey(PublicKey key) throws Exception
    {
        ECPublicKey ecKey = (ECPublicKey)key;
        return ecKey.getQ().getEncoded(false);
    }

    public static byte [] getEncodedPrivateKey(PrivateKey key) throws Exception
    {
        ECPrivateKey ecKey = (ECPrivateKey)key;
        return ecKey.getD().toByteArray();
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
        PublicKey publicKey = ecKeyFac.generatePublic(x509EncodedKeySpec);
        return publicKey;
    }

    private String doECDH (byte[] dataPrv, byte[] dataPub) throws Exception
    {
        KeyAgreement ka = KeyAgreement.getInstance(CryptoHelper.ALGORITHM, CryptoHelper.PROVIDER);
        ka.init(loadPrivateKey(dataPrv));
        ka.doPhase(loadPublicKey(dataPub), true);
        byte [] secret = ka.generateSecret();
        return getBase64String(secret);
    }

    private byte [] generateAesKey(byte[] xorOfRandoms, String sharedKey ){
        byte[] salt = Arrays.copyOfRange(xorOfRandoms, 0, 20);
        HKDFBytesGenerator hkdfBytesGenerator = new HKDFBytesGenerator(new SHA256Digest());
        HKDFParameters hkdfParameters = new HKDFParameters(getBytesForBase64String(sharedKey), salt, null);
        hkdfBytesGenerator.init(hkdfParameters);
        byte[] aesKey = new byte[32];
        hkdfBytesGenerator.generateBytes(aesKey, 0, 32);
        return aesKey;
    }

    public String decrypt(KeyMaterial receivedKeyMaterial,
                          DataFlowRequestKeyMaterial savedKeyMaterial,
                          String encryptedMessage) throws Exception {
        var senderPublicKey = receivedKeyMaterial.getDhPublicKey().getKeyValue();
        var randomKeySender = receivedKeyMaterial.getNonce();
        String sharedKey = doECDH(getBytesForBase64String(savedKeyMaterial.getPrivateKey())
                , getBytesForBase64String(senderPublicKey));
        byte[] xorOfRandoms = xorOfRandom(randomKeySender, savedKeyMaterial.getRandomKey());
        var aesKey = generateAesKey(xorOfRandoms, sharedKey);
        return AesUtil.decryptData(getBytesForBase64String(encryptedMessage), aesKey, xorOfRandoms);
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
