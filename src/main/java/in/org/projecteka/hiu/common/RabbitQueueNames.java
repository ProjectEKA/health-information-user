package in.org.projecteka.hiu.common;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@AllArgsConstructor
public class RabbitQueueNames {
    private final String queuePrefix;

    public String getDataFlowRequestQueue(){
        return addPrefix("data-flow-request-queue");
    }

    public String getDataFlowProcessQueue(){
        return addPrefix("data-flow-process-queue");
    }

    public String getDataFlowDeleteQueue(){
        return addPrefix("data-flow-delete-queue");
    }

    public String getHealthInfoQueue(){
        return addPrefix("health-info-queue");
    }

    public String getHIUDeadLetterQueue(){
        return addPrefix("hiu-dead-letter-queue");
    }

    private String addPrefix(String queueName){
        if(StringUtils.isEmpty(queuePrefix)){
            return queueName;
        }
        return String.format("%s-%s", queuePrefix, queueName);
    }
}
