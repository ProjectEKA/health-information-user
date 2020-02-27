package in.org.projecteka.hiu.dataprocessor.model;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Resource;

import java.util.ArrayList;
import java.util.List;

public class ProcessedResource {
    private List<String> errors = new ArrayList<>();
    private Resource resource;

    public List<String> getErrors() {
        return errors;
    }

    public void addError(String errorMessage) {
        errors.add(errorMessage);
    }

    public void addResource(Resource resource) {
        this.resource = resource;
    }

    public boolean hasErrors() {
        return errors.isEmpty();
    }

    public Resource getResource() {
        return this.resource;
    }
}
