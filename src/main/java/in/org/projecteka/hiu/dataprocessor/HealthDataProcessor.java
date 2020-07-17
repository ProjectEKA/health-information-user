package in.org.projecteka.hiu.dataprocessor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.HealthInformationClient;
import in.org.projecteka.hiu.common.Gateway;
import in.org.projecteka.hiu.consent.ConsentRepository;
import in.org.projecteka.hiu.dataflow.DataFlowRepository;
import in.org.projecteka.hiu.dataflow.Decryptor;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestKeyMaterial;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import in.org.projecteka.hiu.dataflow.model.Entry;
import in.org.projecteka.hiu.dataflow.model.HealthInfoStatus;
import in.org.projecteka.hiu.dataprocessor.model.BundleContext;
import in.org.projecteka.hiu.dataprocessor.model.DataAvailableMessage;
import in.org.projecteka.hiu.dataprocessor.model.DataContext;
import in.org.projecteka.hiu.dataprocessor.model.HealthInfoNotificationRequest;
import in.org.projecteka.hiu.dataprocessor.model.HiStatus;
import in.org.projecteka.hiu.dataprocessor.model.Notification;
import in.org.projecteka.hiu.dataprocessor.model.Notifier;
import in.org.projecteka.hiu.dataprocessor.model.ProcessedEntry;
import in.org.projecteka.hiu.dataprocessor.model.SessionStatus;
import in.org.projecteka.hiu.dataprocessor.model.StatusNotification;
import in.org.projecteka.hiu.dataprocessor.model.StatusResponse;
import in.org.projecteka.hiu.dataprocessor.model.Type;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static java.util.stream.Collectors.joining;

public class HealthDataProcessor {
    public static final String MEDIA_APPLICATION_FHIR_JSON = "application/fhir+json";
    public static final String MEDIA_APPLICATION_FHIR_XML = "application/fhir+xml";
    private static final Logger logger = LoggerFactory.getLogger(HealthDataProcessor.class);
    public static final String COULDN_T_RECEIVE_DATA = "Couldn't receive data";
    private final HealthDataRepository healthDataRepository;
    private final DataFlowRepository dataFlowRepository;
    private final Decryptor decryptor;
    private final HealthInformationClient healthInformationClient;
    private final Gateway gateway;
    private final HiuProperties hiuProperties;
    private final ConsentRepository consentRepository;
    private final FhirContext fhirContext = FhirContext.forR4();
    private final List<HITypeResourceProcessor> resourceProcessors = new ArrayList<>();

    public HealthDataProcessor(HealthDataRepository healthDataRepository,
                               DataFlowRepository dataFlowRepository,
                               Decryptor decryptor,
                               List<HITypeResourceProcessor> hiTypeResourceProcessors,
                               HealthInformationClient healthInformationClient,
                               Gateway gateway,
                               HiuProperties hiuProperties,
                               ConsentRepository consentRepository) {
        this.healthDataRepository = healthDataRepository;
        this.dataFlowRepository = dataFlowRepository;
        this.decryptor = decryptor;
        this.healthInformationClient = healthInformationClient;
        this.resourceProcessors.addAll(hiTypeResourceProcessors);
        this.gateway = gateway;
        this.hiuProperties = hiuProperties;
        this.consentRepository = consentRepository;
    }

    public void process(DataAvailableMessage message) {
        DataContext context = createDataContext(message);
        if (context != null && context.getNotifiedData() != null) {
            processEntries(context);
        } else {
            // TODO: this should never happen, unless someone sends empty response.
            // TODO: Noop. this can be a valid scenario.
        }
    }

    private void processEntries(DataContext context) {
        try {
            updateDataProcessStatus(context, "", HealthInfoStatus.PROCESSING, null);
            String transactionId = context.getTransactionId();
            DataFlowRequestKeyMaterial keyMaterial = dataFlowRepository.getKeys(transactionId).block();
            List<String> dataErrors = new ArrayList<>();
            List<StatusResponse> statusResponses = new ArrayList<>();
            logger.info("Received data from HIP. Number of entries: %d", context.getNotifiedData().getEntries().size());
            context.getNotifiedData().getEntries().forEach(entry -> {
                var entryToProcess = entry;
                String dataPartNumber = context.getDataPartNumber();
                if (!hasContent(entry)) {
                    var healthInformation = healthInformationClient.informationFrom(entry.getLink()).block();
                    if (healthInformation == null) {
                        dataErrors.add("Health Information not found");
                        healthDataRepository.insertErrorFor(transactionId, dataPartNumber).block();
                        statusResponses.add(getStatusResponse(entry, HiStatus.ERRORED, COULDN_T_RECEIVE_DATA));
                        return;
                    }
                    entryToProcess = Entry.builder()
                            .content(healthInformation.getContent())
                            .checksum(entry.getChecksum())
                            .media(entry.getMedia())
                            .build();
                }
                var result = processEntryContent(context, entryToProcess, keyMaterial);
                if (result.hasErrors()) {
                    dataErrors.addAll(result.getErrors());
                    healthDataRepository.insertErrorFor(transactionId, dataPartNumber).block();
                    statusResponses.add(getStatusResponse(entry, HiStatus.ERRORED, COULDN_T_RECEIVE_DATA));
                    return;
                }
                context.addTrackedResources(result.getTrackedResources());
                healthDataRepository.insertDataFor(transactionId, dataPartNumber, result.getResource(), result.latestResourceDate()).block();
                statusResponses.add(getStatusResponse(entry, HiStatus.OK, "Data received successfully"));
            });

            if (!dataErrors.isEmpty()) {
                var errors = dataErrors.stream().map("[ERROR]"::concat).collect(joining());
                var allErrors = "[ERROR]".concat(errors);
                logger.error("Error occurred while processing data from HIP. Transaction id: %s. Errors: %s",
                        context.getTransactionId(), allErrors);
                updateDataProcessStatus(context, allErrors, HealthInfoStatus.ERRORED, context.latestResourceDate());
                notifyHealthInfoStatus(context, statusResponses, SessionStatus.FAILED);
            } else {
                updateDataProcessStatus(context, "", HealthInfoStatus.SUCCEEDED, context.latestResourceDate());
                notifyHealthInfoStatus(context, statusResponses, SessionStatus.TRANSFERRED);
            }
        } catch (Exception ex) {
            logger.error("Error occurred while processing data from HIP. Transaction id: %s.", context.getTransactionId());
            logger.error(ex.getMessage(), ex);
        }
    }

    private StatusResponse getStatusResponse(Entry entry, HiStatus hiStatus, String msg) {
        return StatusResponse.builder()
                .careContextReference(entry.getCareContextReference())
                .hiStatus(hiStatus)
                .description(msg)
                .build();
    }

    private void notifyHealthInfoStatus(DataContext context,
                                        List<StatusResponse> statusResponses,
                                        SessionStatus sessionStatus) {
        String consentId = dataFlowRepository.getConsentId(context.getTransactionId()).block();
        String hipId = consentRepository.getHipId(consentId).block();
        HealthInfoNotificationRequest healthInfoNotificationRequest =
                getHealthInfoNotificationRequest(context, statusResponses, sessionStatus, consentId, hipId);
        String token = gateway.token().block();
        String consentManagerId = fetchCMId(healthInfoNotificationRequest.getNotification().getConsentId());
        healthInformationClient.notifyHealthInfo(healthInfoNotificationRequest, token, consentManagerId).block();
    }

    private HealthInfoNotificationRequest getHealthInfoNotificationRequest(DataContext context,
                                                                           List<StatusResponse> statusResponses,
                                                                           SessionStatus sessionStatus,
                                                                           String consentId,
                                                                           String hipId) {
        return HealthInfoNotificationRequest.builder()
                .requestId(UUID.randomUUID())
                .timestamp(LocalDateTime.now(ZoneOffset.UTC))
                .notification(Notification.builder()
                        .consentId(consentId)
                        .transactionId(context.getTransactionId())
                        .doneAt(LocalDateTime.now(ZoneOffset.UTC))
                        .notifier(Notifier.builder()
                                .type(Type.HIU)
                                .id(hiuProperties.getId())
                                .build())
                        .statusNotification(StatusNotification.builder()
                                .sessionStatus(sessionStatus)
                                .hipId(hipId)
                                .statusResponses(statusResponses)
                                .build())
                        .build())
                .build();
    }

    private String fetchCMId(String consentId) {
        return consentRepository.getConsentMangerId(consentId).block();
    }

    private void updateDataProcessStatus(DataContext context, String allErrors, HealthInfoStatus status, LocalDateTime latestResourceDate) {
        dataFlowRepository.updateDataFlowWithStatus(context.getTransactionId(),
                context.getDataPartNumber(),
                allErrors,
                status,
                latestResourceDate).block();
    }

    private DataContext createDataContext(DataAvailableMessage message) {
        Path dataFilePath = Paths.get(message.getPathToFile());
        try (InputStream inputStream = Files.newInputStream(dataFilePath)) {
            var objectMapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .configure(WRITE_DATES_AS_TIMESTAMPS, false)
                    .configure(FAIL_ON_UNKNOWN_PROPERTIES, false);

            DataNotificationRequest dataNotificationRequest = objectMapper.readValue(inputStream,
                    DataNotificationRequest.class);
            return DataContext.builder()
                    .notifiedData(dataNotificationRequest)
                    .dataFilePath(dataFilePath)
                    .dataPartNumber(message.getPartNumber())
                    .trackedResources(new ArrayList<>())
                    .build();
        } catch (Exception e) {
            logger.error("Could not create context from data file path", e);
            throw new RuntimeException(e);
        }
    }

    private ProcessedEntry processEntryContent(DataContext context,
                                               Entry entry,
                                               DataFlowRequestKeyMaterial keyMaterial) {
        logger.info("Process entry {}", entry);
        ProcessedEntry result = new ProcessedEntry();
        var mayBeParser = getEntryParser(entry.getMedia());

        return mayBeParser.map(parser -> {
            String decryptedContent;
            try {
                decryptedContent = decryptor.decrypt(context.getKeyMaterial(), keyMaterial, entry.getContent());
            } catch (Exception e) {
                logger.error("Error while decrypting {exception}", e);
                result.getErrors().add("Could not decrypt content");
                return result;
            }
            Bundle bundle = (Bundle) parser.parseResource(decryptedContent);
            if (!isValidBundleType(bundle)) {
                result.addError("Can not process entry content, invalid envelope." +
                        "Entry content is either not a FHIR Bundle type COLLECTION or DOCUMENT. " +
                        "For Document bundle type (e.g Discharge Summary), the first entry must be composition.");
                return result;
            }
            Function<ResourceType, HITypeResourceProcessor> resourceProcessor = this::identifyResourceProcessor;
            BundleContext bundleContext = new BundleContext(bundle, resourceProcessor);
            try {
                logger.info("Processing bundle id:" + bundle.getId());
                bundle.getEntry().forEach(bundleEntry -> {
                    ResourceType resourceType = bundleEntry.getResource().getResourceType();
                    logger.info("bundle entry resource type:  {}", resourceType);
                    HITypeResourceProcessor processor = identifyResourceProcessor(resourceType);
                    if (processor != null) {
                        processor.process(bundleEntry.getResource(), context, bundleContext, null);
                    }
                });
                result.setEncoded(parser.encodeResourceToString(bundle));
                result.addTrackedResources(bundleContext.getTrackedResources(), bundleContext.getBundleDate());
                return result;
            } catch (Exception e) {
                logger.error("Could not process bundle {exception}", e);
                result.getErrors().add(String.format("Could not process bundle with id: %s, error-message: %s",
                        bundle.getId(), e.getMessage()));
                return result;
            }
        }).orElseGet(() -> {
            result.addError("Can't process entry content. Unknown media type.");
            return result;
        });
    }

    private HITypeResourceProcessor identifyResourceProcessor(ResourceType resourceType) {
        return resourceProcessors.stream().filter(p -> p.supports(resourceType)).findAny().orElse(null);
    }

    private boolean isValidBundleType(Bundle bundle) {
        Bundle.BundleType bundleType = bundle.getType();
        if (bundleType.equals(Bundle.BundleType.COLLECTION)) {
            return true;
        }
        if (!bundleType.equals(Bundle.BundleType.DOCUMENT)) {
            return false;
        }
        if (bundle.getEntry().isEmpty()) {
            return false;
        }
        Bundle.BundleEntryComponent firstEntry = bundle.getEntry().get(0);
        return firstEntry.getResource().getResourceType().equals(ResourceType.Composition);
    }

    private Optional<IParser> getEntryParser(String media) {
        if (media.equalsIgnoreCase(MEDIA_APPLICATION_FHIR_JSON)) {
            return Optional.of(fhirContext.newJsonParser());
        }
        if (media.equalsIgnoreCase(MEDIA_APPLICATION_FHIR_XML)) {
            return Optional.of(fhirContext.newXmlParser());
        }
        return Optional.empty();
    }

    private boolean hasContent(Entry entry) {
        return (entry.getContent() != null) && !entry.getContent().isBlank();
    }
}
