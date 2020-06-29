package in.org.projecteka.hiu.dataprocessor.model;

import java.util.ArrayList;
import java.util.List;

public class ProcessedResource {
    private final List<String> errors = new ArrayList<>();
    private String encodedResource;

    public List<String> getErrors() {
        return errors;
    }

    public void addError(String errorMessage) {
        errors.add(errorMessage);
    }

    public void setEncoded(String resource) {
        encodedResource = resource;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public String getResource() {
        return encodedResource;
    }
}
