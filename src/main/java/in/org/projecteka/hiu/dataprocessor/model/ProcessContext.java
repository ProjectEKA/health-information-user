package in.org.projecteka.hiu.dataprocessor.model;

import org.hl7.fhir.r4.model.ResourceType;

import java.util.Date;
import java.util.function.Supplier;

public class ProcessContext {
    private Supplier<Date> rootContextDate;
    private final Supplier<String> contextResourceId;
    private final Supplier<ResourceType> contextResourceType;

    public ProcessContext(Supplier<Date> rootContextDate, Supplier<String> contextResourceId, Supplier<ResourceType> contextResourceType) {
        this.rootContextDate = rootContextDate;
        this.contextResourceId = contextResourceId;
        this.contextResourceType = contextResourceType;
    }

    public Date getContextDate() {
        return rootContextDate.get();
    }

    public String getContextResourceId() {
        return contextResourceId.get();
    }

    public ResourceType getContextResourceType() {
        return contextResourceType.get();
    }
}
