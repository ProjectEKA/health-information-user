package in.org.projecteka.hiu.valuseset;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;

@Component
public class ValueSetResource implements InitializingBean {
    @Value("${hiu.valueSets}")
    private Resource valueSetsResource;
    private String valueSetDef;

    @Override
    public void afterPropertiesSet() throws Exception {
        valueSetDef = IOUtils.toString(valueSetsResource.getInputStream(), Charset.defaultCharset());
    }

    public String getValueSetDefinition() {
        return valueSetDef;
    }


}
