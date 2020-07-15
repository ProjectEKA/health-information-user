package in.org.projecteka.hiu.dataprocessor.model;

import lombok.Getter;
import org.hl7.fhir.r4.model.ResourceType;

import java.time.LocalDateTime;

@Getter
public class TrackedResourceReference {
    private ResourceType resourceType;
    private String resourceId;
    private LocalDateTime localDateTime;
    private String title;

    public TrackedResourceReference(ResourceType resourceType, String resourceId, LocalDateTime localDateTime, String title) {
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.localDateTime = localDateTime;
        this.title = title;
    }
}
