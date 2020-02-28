package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestKeyMaterial;
import in.org.projecteka.hiu.dataflow.model.Entry;
import in.org.projecteka.hiu.dataflow.model.KeyMaterial;
import org.jeasy.random.EasyRandom;

public class TestBuilders {
    private static final EasyRandom easyRandom = new EasyRandom();

    public static DataFlowRequest.DataFlowRequestBuilder dataFlowRequest() {
        return easyRandom.nextObject(DataFlowRequest.DataFlowRequestBuilder.class);
    }

    public static Entry.EntryBuilder entry() {
        return easyRandom.nextObject(Entry.EntryBuilder.class);
    }

    public static KeyMaterial.KeyMaterialBuilder keyMaterial() {
        return easyRandom.nextObject(KeyMaterial.KeyMaterialBuilder.class);
    }

    public static DataFlowRequestKeyMaterial.DataFlowRequestKeyMaterialBuilder dataFlowRequestKeyMaterial() {
        return easyRandom.nextObject(DataFlowRequestKeyMaterial.DataFlowRequestKeyMaterialBuilder.class);
    }
}
