package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.dataflow.model.DataFlowRequestKeyMaterial;
import in.org.projecteka.hiu.dataflow.model.KeyMaterial;
import in.org.projecteka.hiu.dataflow.model.KeyStructure;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;

import static org.assertj.core.api.Assertions.assertThat;

public class DecryptorTest {
    private Decryptor decryptor;

    @BeforeEach
    public void init() {
        decryptor = new Decryptor();
       Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    public void shouldReturn32ByteRandomKey(){
        var randomKey = decryptor.generateRandomKey();
        var randomKeyByte = decryptor.getBytesForBase64String(randomKey);

        assertThat(randomKeyByte.length).isEqualTo(32);
    }

    @Test
    public void shouldGenerateKeyPairs() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        var keyPair = decryptor.generateKeyPair();
        assertThat(keyPair).isNotNull();
    }

    @Test
    public void shouldBeAbleToConvertKeyPairs() throws Exception {
        var keyPair = decryptor.generateKeyPair();
        assertThat(decryptor.getEncodedPublicKey(keyPair.getPublic())).isNotNull();
        assertThat(decryptor.getEncodedPrivateKey(keyPair.getPrivate())).isNotNull();
    }

    @Test
    public void shouldDecryptData() throws Exception {
        var hiuPrivateKey = "DrDDUf+HIXB59/ym4GxrM/TfeULHyiUVzHWkq9rFkJI=";
        var hiuRandomKey = "pk5xT1Xk+KUlf/LC1LZawKECPNvOvIzhZNEyIdh7oJE=";
        var encryptedString = "cMTT+FiiDMVXdK1nbBXmnNXP2doSbWQ11Sl8rs1d5SzVDA==";
        var senderPublicKey = "MIIBMTCB6gYHKoZIzj0CATCB3gIBATArBgcqhkjOPQEBAiB/////////////////////////////////////////7TBEBCAqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqYSRShRAQge0Je0Je0Je0Je0Je0Je0Je0Je0Je0Je0JgtenHcQyGQEQQQqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq0kWiCuGaG4oIa04B7dLHdI0UySPU1+bXxhsinpxaJ+ztPZAiAQAAAAAAAAAAAAAAAAAAAAFN753qL3nNZYEmMaXPXT7QIBCANCAARfpkcbh0Y6Z1xcck4D2pNKLQ2DwLOxI9bO2sy8zlbJ4391xJpwYNG2STnmP9cwz0+V74B3mbcykl5J1gsXtNe+";
        var senderRandomKey = "xXrM6PfCsBX0Q238uxZCP8YBpPXxsiZvbE++jX5GV5c=";
        KeyMaterial receivedKeyMaterial = KeyMaterial.builder()
                .dhPublicKey(KeyStructure.builder().keyValue(senderPublicKey).build())
                .nonce(senderRandomKey)
                .build();
        DataFlowRequestKeyMaterial savedKeyMaterial = DataFlowRequestKeyMaterial.builder()
                .randomKey(hiuRandomKey).privateKey(hiuPrivateKey)
                .build();
        assertThat(decryptor.decrypt(receivedKeyMaterial, savedKeyMaterial, encryptedString))
                .isEqualTo("\"This is a string\"");
    }


}
