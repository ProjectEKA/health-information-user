package in.org.projecteka.hiu.dataprocessor.model;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class ProcessedEntry {
    private final List<String> errors = new ArrayList<>();
    private String encodedResource;
    private List<TrackedResourceReference> trackedResources = new ArrayList<>();
    private LocalDateTime contextDate;

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

    public void addTrackedResources(List<TrackedResourceReference> trackedResources, Date contextDate) {
        this.trackedResources.clear();
        this.trackedResources.addAll(trackedResources);
        this.contextDate = (contextDate != null) ?
                contextDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : null;
    }

    public List<TrackedResourceReference> getTrackedResources() {
        return trackedResources;
    }

    public LocalDateTime latestResourceDate() {
        if (trackedResources == null || trackedResources.isEmpty()) {
            return contextDate;
        }
        List<LocalDateTime> dateTimes = trackedResources.stream()
                .map(res -> res.getLocalDateTime())
                .filter(resDate -> resDate != null).collect(Collectors.toList());
        return dateTimes.isEmpty() ?  null : dateTimes.stream().max(LocalDateTime::compareTo).get();
    }
}
