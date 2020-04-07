package in.org.projecteka.hiu.dataprocessor;

import in.org.projecteka.hiu.dataflow.model.DataFlowRequest;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestKeyMaterial;
import in.org.projecteka.hiu.dataflow.model.Entry;
import in.org.projecteka.hiu.dataflow.model.KeyMaterial;
import in.org.projecteka.hiu.dataprocessor.model.HealthInfoNotificationRequest;
import org.jeasy.random.EasyRandom;

public class TestBuilders {
    private static final EasyRandom easyRandom = new EasyRandom();

    public static HealthInfoNotificationRequest.HealthInfoNotificationRequestBuilder healthInfoNotificationRequest() {
        return easyRandom.nextObject(HealthInfoNotificationRequest.HealthInfoNotificationRequestBuilder.class);
    }

    public static String string() {
        return easyRandom.nextObject(String.class);
    }
}
