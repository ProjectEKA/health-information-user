package in.org.projecteka.hiu.dataflow.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataFlowRequestKeyMaterial {
    private String privateKey;
    private String publicKey;
    private String randomKey;
}
