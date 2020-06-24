package in.org.projecteka.hiu.dataprocessor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.org.projecteka.hiu.HiuProperties;
import in.org.projecteka.hiu.clients.HealthInformation;
import in.org.projecteka.hiu.clients.HealthInformationClient;
import in.org.projecteka.hiu.common.CentralRegistry;
import in.org.projecteka.hiu.consent.ConsentRepository;
import in.org.projecteka.hiu.dataflow.DataFlowRepository;
import in.org.projecteka.hiu.dataflow.Decryptor;
import in.org.projecteka.hiu.dataflow.model.DataFlowRequestKeyMaterial;
import in.org.projecteka.hiu.dataflow.model.DataNotificationRequest;
import in.org.projecteka.hiu.dataflow.model.Entry;
import in.org.projecteka.hiu.dataflow.model.HealthInfoStatus;
import in.org.projecteka.hiu.dataprocessor.model.DataAvailableMessage;
import in.org.projecteka.hiu.dataprocessor.model.DataContext;
import in.org.projecteka.hiu.dataprocessor.model.EntryStatus;
import in.org.projecteka.hiu.dataprocessor.model.HealthInfoNotificationRequest;
import in.org.projecteka.hiu.dataprocessor.model.HiStatus;
import in.org.projecteka.hiu.dataprocessor.model.Notification;
import in.org.projecteka.hiu.dataprocessor.model.Notifier;
import in.org.projecteka.hiu.dataprocessor.model.ProcessedResource;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

public class HealthDataProcessor {
    public static final String MEDIA_APPLICATION_FHIR_JSON = "application/fhir+json";
    public static final String MEDIA_APPLICATION_FHIR_XML = "application/fhir+xml";
    private static final Logger logger = LoggerFactory.getLogger(HealthDataProcessor.class);
    private final HealthDataRepository healthDataRepository;
    private final DataFlowRepository dataFlowRepository;
    private final Decryptor decryptor;
    private final HealthInformationClient healthInformationClient;
    private final CentralRegistry centralRegistry;
    private final HiuProperties hiuProperties;
    private final ConsentRepository consentRepository;
    private final FhirContext fhirContext = FhirContext.forR4();
    private final List<HITypeResourceProcessor> resourceProcessors = new ArrayList<>();

    public HealthDataProcessor(HealthDataRepository healthDataRepository,
                               DataFlowRepository dataFlowRepository,
                               Decryptor decryptor,
                               List<HITypeResourceProcessor> hiTypeResourceProcessors,
                               HealthInformationClient healthInformationClient,
                               CentralRegistry centralRegistry,
                               HiuProperties hiuProperties,
                               ConsentRepository consentRepository) {
        this.healthDataRepository = healthDataRepository;
        this.dataFlowRepository = dataFlowRepository;
        this.decryptor = decryptor;
        this.healthInformationClient = healthInformationClient;
        this.resourceProcessors.addAll(hiTypeResourceProcessors);
        this.centralRegistry = centralRegistry;
        this.hiuProperties = hiuProperties;
        this.consentRepository = consentRepository;
    }

    public void process(DataAvailableMessage message) {
        DataContext context = createContext(message);
        if (context != null && context.getNotifiedData() != null) {
            processEntries(context);
        } else {
            // TODO: this should never happen, unless someone sends empty response.
            // TODO: Noop. this can be a valid scenario.
        }
    }

    private void processEntries(DataContext context) {
        try {
            updateDataProcessStatus(context, "", HealthInfoStatus.PROCESSING);
            DataFlowRequestKeyMaterial keyMaterial = dataFlowRepository.getKeys(context.getTransactionId()).block();
            List<String> dataErrors = new ArrayList<>();
            List<StatusResponse> statusResponses = new ArrayList<>();
            context.getNotifiedData().getEntries().forEach(entry -> {
                ProcessedResource processedResource = new ProcessedResource();
                if (hasContent(entry)) {
                    processedResource = processEntryContent(context, entry, keyMaterial);
                } else {
                    HealthInformation healthInformation = healthInformationClient.getHealthInformationFor(entry.getLink())
                            .block();
                    try {
                        if (healthInformation != null) {
                            Entry healthInformationEntry = Entry.builder()
                                    .content(healthInformation.getContent())
                                    .checksum(entry.getChecksum())
                                    .media(entry.getMedia())
                                    .build();
                            processedResource = processEntryContent(context, healthInformationEntry, keyMaterial);
                        } else {
                            processedResource.addError("Health Information not found");
                        }
                    } catch (Exception e) {
                        processedResource.addError(e.getMessage());
                        logger.error(e.getMessage());
                    }
                }
                if (!processedResource.hasErrors()) {
                    String resource =
                            getEntryParser(entry.getMedia()).encodeResourceToString(processedResource.getResource());
                    healthDataRepository.insertHealthData(
                            context.getTransactionId(),
                            context.getDataPartNumber(),
                            resource,
                            EntryStatus.SUCCEEDED)
                            .block();

                    statusResponses.add(getStatusResponse(entry, HiStatus.OK, "Data received successfully"));
                } else {
                    dataErrors.addAll(processedResource.getErrors());
                    healthDataRepository.insertHealthData(
                            context.getTransactionId(),
                            context.getDataPartNumber(),
                            "",
                            EntryStatus.ERRORED)
                            .block();
                    statusResponses.add(getStatusResponse(entry, HiStatus.ERRORED, "Couldn't receive data"));
                }
            });

            if (!dataErrors.isEmpty()) {
                String allErrors = "[ERROR]".concat(String.join("[ERROR]", dataErrors));
                updateDataProcessStatus(context, allErrors, HealthInfoStatus.ERRORED);
                notifyHealthInfoStatus(context, statusResponses, SessionStatus.FAILED);
            } else {
                updateDataProcessStatus(context, "", HealthInfoStatus.SUCCEEDED);
                notifyHealthInfoStatus(context, statusResponses, SessionStatus.TRANSFERRED);
            }
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        } finally {
            logger.info("Done!");
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
        String token = centralRegistry.token().block();
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
                .timestamp(LocalDateTime.now())
                .notification(Notification.builder()
                        .consentId(consentId)
                        .transactionId(context.getTransactionId())
                        .doneAt(LocalDateTime.now())
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

    private void updateDataProcessStatus(DataContext context, String allErrors, HealthInfoStatus status) {
        dataFlowRepository.updateDataFlowWithStatus(context.getTransactionId(),
                context.getDataPartNumber(),
                allErrors,
                status).block();
    }

    private DataContext createContext(DataAvailableMessage message) {
        Path dataFilePath = Paths.get(message.getPathToFile());
        try (InputStream inputStream = Files.newInputStream(dataFilePath)) {
            var objectMapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .configure(WRITE_DATES_AS_TIMESTAMPS, false)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            DataNotificationRequest dataNotificationRequest = objectMapper.readValue(inputStream,
                    DataNotificationRequest.class);
            return DataContext.builder()
                    .notifiedData(dataNotificationRequest)
                    .dataFilePath(dataFilePath)
                    .dataPartNumber(message.getPartNumber())
                    .build();
        } catch (Exception e) {
            logger.error("Could not create context from data file path", e);
            throw new RuntimeException(e);
        }
    }


    private ProcessedResource processEntryContent(DataContext context, Entry entry,
                                                  DataFlowRequestKeyMaterial keyMaterial) {
        logger.info("Process entry {}", entry);
        ProcessedResource result = new ProcessedResource();
        IParser parser = getEntryParser(entry.getMedia());
        if (parser == null) {
            result.addError("Can't process entry content. Unknown media type.");
            return result;
        }
        String decryptedContent = null;
        try {
            decryptedContent = decryptor.decrypt(context.getKeyMaterial(),
                    keyMaterial,
                    entry.getContent());
        } catch (Exception e) {
            logger.error("Error while decrypting {exception}", e);
            result.getErrors().add("Could not decrypt content");
            return result;
        }
        Bundle bundle = (Bundle) parser.parseResource(decryptedContent);
        if (!isValidBundleType(bundle.getType())) {
            result.addError("Can not process entry content. Entry content is not a FHIR Bundle type COLLECTION or " +
                    "DOCUMENT");
            return result;
        }
        try {
            bundle.getEntry().forEach(bundleEntry -> {
                ResourceType resourceType = bundleEntry.getResource().getResourceType();
                logger.info("bundle entry resource type:  " + resourceType);
                HITypeResourceProcessor processor = identifyResourceProcessor(resourceType);
                if (processor != null) {
                    processor.process(bundleEntry.getResource(), context);
                }
            });
        } catch (Exception e) {
            logger.error("Could not process bundle {exception}", e);
            result.getErrors().add(String.format("Could not process bundle with id: %s, error-message: %s",
                    bundle.getId(), e.getMessage()));
            return result;
        }
        result.addResource(bundle);
        return result;
    }

    private HITypeResourceProcessor identifyResourceProcessor(ResourceType resourceType) {
        return resourceProcessors.stream().filter(p -> p.supports(resourceType)).findAny().orElse(null);
    }

    private boolean isValidBundleType(Bundle.BundleType type) {
        return type.equals(Bundle.BundleType.COLLECTION) || type.equals(Bundle.BundleType.DOCUMENT);
    }

    private IParser getEntryParser(String media) {
        if (media.equalsIgnoreCase(MEDIA_APPLICATION_FHIR_JSON)) {
            return fhirContext.newJsonParser();
        }
        if (media.equalsIgnoreCase(MEDIA_APPLICATION_FHIR_XML)) {
            return fhirContext.newXmlParser();
        }
        return null;
    }

    private boolean hasContent(Entry entry) {
        return (entry.getContent() != null) && !entry.getContent().isBlank();
    }
}
