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
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.util.Pair;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static in.org.projecteka.hiu.common.Constants.CORRELATION_ID;
import static in.org.projecteka.hiu.dataflow.model.HealthInfoStatus.ERRORED;
import static in.org.projecteka.hiu.dataflow.model.HealthInfoStatus.PARTIAL;
import static java.util.stream.Collectors.joining;

public class HealthDataProcessor {
    public static final String MEDIA_APPLICATION_FHIR_JSON = "application/fhir+json";
    public static final String MEDIA_APPLICATION_FHIR_XML = "application/fhir+xml";
    private static final Logger logger = LoggerFactory.getLogger(HealthDataProcessor.class);
    private static final String COULD_NOT_RECEIVE_DATA = "Couldn't receive data";
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
            logger.info(String.format(
                    "Received data from HIP for transaction: %s. Number of entries: %d. Trying to process data.",
                    context.getTransactionId(), context.getNumberOfEntries()));
            updateDataProcessStatus(context, "", HealthInfoStatus.PROCESSING, null);
            String transactionId = context.getTransactionId();
            DataFlowRequestKeyMaterial keyMaterial = blockPublisher(dataFlowRepository.getKeys(transactionId));
            List<String> dataErrors = new ArrayList<>();
            List<StatusResponse> statusResponses = new ArrayList<>();
            context.getNotifiedData().getEntries().forEach(entry -> {
                var entryToProcess = entry;
                String dataPartNumber = context.getDataPartNumber();
                if (!hasContent(entry)) {
                    var healthInformation = blockPublisher(healthInformationClient.informationFrom(entry.getLink()));
                    if (healthInformation == null) {
                        dataErrors.add("Health Information not found");
                        blockPublisher(healthDataRepository
                                .insertErrorFor(transactionId, dataPartNumber, entryToProcess.getCareContextReference()));
                        statusResponses.add(getStatusResponse(entry, HiStatus.ERRORED, COULD_NOT_RECEIVE_DATA));
                        return;
                    }
                    entryToProcess = Entry.builder()
                            .content(healthInformation.getContent())
                            .checksum(entry.getChecksum())
                            .media(entry.getMedia())
                            .careContextReference(entry.getCareContextReference())
                            .build();
                }
                var result = processEntryContent(context, entryToProcess, keyMaterial);
                if (result.hasErrors()) {
                    dataErrors.addAll(result.getErrors());
                    blockPublisher(healthDataRepository
                            .insertErrorFor(transactionId, dataPartNumber, entryToProcess.getCareContextReference()));
                    statusResponses.add(getStatusResponse(entry, HiStatus.ERRORED, COULD_NOT_RECEIVE_DATA));
                    return;
                }
                context.addTrackedResources(result.getTrackedResources());
                Optional<Pair<String, String>> originIdAndName = identifyOrigin(result.getOrigins());
                String originId = originIdAndName.isPresent() ? originIdAndName.get().getFirst() : context.getHipId();
                blockPublisher(healthDataRepository.insertDataFor(transactionId,
                        dataPartNumber,
                        result.getResource(),
                        result.latestResourceDate(),
                        entryToProcess.getCareContextReference(),
                        result.getUniqueResourceId(),
                        result.getDocumentType(),
                        originId));
                statusResponses.add(getStatusResponse(entry, HiStatus.OK, "Data received successfully"));
            });

            var status = dataErrors.size() == context.getNumberOfEntries() ? HealthInfoStatus.ERRORED : PARTIAL;

            if (!dataErrors.isEmpty()) {
                var errors = dataErrors.stream().map("[ERROR]"::concat).collect(joining());
                var allErrors = "[ERROR]".concat(errors);
                logger.error("Error occurred while processing data from HIP. Transaction id: {}. Errors: {}",
                        context.getTransactionId(), allErrors);
                updateDataProcessStatus(context, allErrors, status, context.latestResourceDate());
                notifyHealthInfoStatus(context, statusResponses, SessionStatus.FAILED);
            } else {
                updateDataProcessStatus(context, "", HealthInfoStatus.SUCCEEDED, context.latestResourceDate());
                notifyHealthInfoStatus(context, statusResponses, SessionStatus.TRANSFERRED);
            }
        } catch (Exception ex) {
            logger.error("Error occurred while processing data from HIP. Transaction id: {}.", context.getTransactionId());
            logger.error(ex.getMessage(), ex);
            updateDataProcessStatus(context, ex.getMessage(), ERRORED, context.latestResourceDate());
        }
    }

    private <T> T blockPublisher(Mono<T> publisher) {
        // block() clears the context, we should put correlationId back again in context.
        // https://github.com/reactor/reactor-core/issues/1667

        String correlationId = MDC.get(CORRELATION_ID);
        T result = publisher.block();
        MDC.put(CORRELATION_ID, correlationId);
        return result;
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
        HealthInfoNotificationRequest healthInfoNotificationRequest =
                getHealthInfoNotificationRequest(context, statusResponses, sessionStatus);
        String token = blockPublisher(gateway.token());
        String consentManagerId = fetchCMId(healthInfoNotificationRequest.getNotification().getConsentId());
        blockPublisher(healthInformationClient.notifyHealthInfo(healthInfoNotificationRequest, token, consentManagerId));
    }

    private HealthInfoNotificationRequest getHealthInfoNotificationRequest(DataContext context,
                                                                           List<StatusResponse> statusResponses,
                                                                           SessionStatus sessionStatus) {
        return HealthInfoNotificationRequest.builder()
                .requestId(UUID.randomUUID())
                .timestamp(LocalDateTime.now(ZoneOffset.UTC))
                .notification(Notification.builder()
                        .consentId(context.getConsentId())
                        .transactionId(context.getTransactionId())
                        .doneAt(LocalDateTime.now(ZoneOffset.UTC))
                        .notifier(Notifier.builder()
                                .type(Type.HIU)
                                .id(hiuProperties.getId())
                                .build())
                        .statusNotification(StatusNotification.builder()
                                .sessionStatus(sessionStatus)
                                .hipId(context.getHipId())
                                .statusResponses(statusResponses)
                                .build())
                        .build())
                .build();
    }

    private String fetchCMId(String consentId) {
        return blockPublisher(consentRepository.getConsentMangerId(consentId));
    }

    private void updateDataProcessStatus(DataContext context, String allErrors, HealthInfoStatus status, LocalDateTime latestResourceDate) {
        blockPublisher(dataFlowRepository.updateDataFlowWithStatus(context.getTransactionId(),
                context.getDataPartNumber(),
                allErrors,
                status,
                latestResourceDate));
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
            String consentId = blockPublisher(dataFlowRepository.getConsentId(dataNotificationRequest.getTransactionId()));
            String hipId = blockPublisher(consentRepository.getHipId(consentId));
            return DataContext.builder()
                    .notifiedData(dataNotificationRequest)
                    .dataFilePath(dataFilePath)
                    .dataPartNumber(message.getPartNumber())
                    .trackedResources(new ArrayList<>())
                    .hipId(hipId)
                    .consentId(consentId)
                    .build();
        } catch (Exception e) {
            logger.error("Could not create context from data file path", e);
            throw new RuntimeException(e);
        }
    }

    private ProcessedEntry processEntryContent(DataContext context,
                                               Entry entry,
                                               DataFlowRequestKeyMaterial keyMaterial) {
        logger.info("Process entry for care-context: {}", entry.getCareContextReference());
        var mayBeParser = getEntryParser(entry.getMedia());

        return mayBeParser.map(parser -> {
            ProcessedEntry result = new ProcessedEntry();
            String decryptedContent;
            try {
                decryptedContent = decryptor.decrypt(context.getKeyMaterial(), keyMaterial, entry.getContent());
            } catch (Exception e) {
                logger.error("Error while decrypting {exception}", e);
                result.addError("Could not read encrypted content from file");
                return result;
            }
            Bundle bundle = parser.parseResource(Bundle.class, decryptedContent);
            if (!isValidBundleType(bundle)) {
                result.addError("Can not process entry content, invalid envelope." +
                        "Entry content is either not a FHIR Bundle type COLLECTION or DOCUMENT. " +
                        "For Document bundle type (e.g Discharge Summary), the first entry must be composition.");
                return result;
            }
            Function<ResourceType, HITypeResourceProcessor> resourceProcessor = this::identifyResourceProcessor;
            BundleContext bundleContext = new BundleContext(bundle, resourceProcessor);
            try {
                logger.info("Processing bundle id: {}", bundle.getId());
                bundle.getEntry().forEach(bundleEntry -> {
                    ResourceType resourceType = bundleEntry.getResource().getResourceType();
                    logger.info("bundle entry resource type:  {}", resourceType);
                    HITypeResourceProcessor processor = identifyResourceProcessor(resourceType);
                    if (processor != null) {
                        processor.process(bundleEntry.getResource(), context, bundleContext, null);
                    }
                });
                result.setEncoded(parser.encodeResourceToString(bundle));
                result.setUniqueResourceId(bundleContext.getBundleUniqueId());
                result.setDocumentType(bundleContext.getDocumentType());
                result.setOrigins(bundleContext.getOrigins());
                result.addTrackedResources(bundleContext.getTrackedResources(), bundleContext.getBundleDate());
                return result;
            } catch (Exception e) {
                logger.error("Could not process bundle {exception}", e);
                result.addError(String.format("Could not process bundle with id: %s, error-message: %s",
                        bundle.getId(), e.getMessage()));
                return result;
            }
        }).orElseGet(() -> {
            ProcessedEntry result = new ProcessedEntry();
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

    private Optional<Identifier> getAffinityDomainIdentifier(List<String> domains, Organization organization) {
        if (!organization.hasIdentifier()) {
            return Optional.empty();
        }
        if (domains.isEmpty()) {
            return Optional.empty();
        }
        return organization.getIdentifier().stream().filter(identifier -> identifier.hasSystem())
                .filter(identifier -> domains.stream().anyMatch(domain -> identifier.getSystem().toUpperCase().contains(domain.toUpperCase())))
                .findFirst();
    }

    private List<String> getHFRAffinityDomains() {
        Optional<String> hfrAffinityDomains = Optional.ofNullable(hiuProperties.getHfrAffinityDomains());
        if (hfrAffinityDomains.isPresent()) {
            return Arrays.asList(hfrAffinityDomains.get().split(","));
        }
        return Collections.emptyList();
    }


    private Optional<Pair<String, String>> identifyOrigin(List<Organization> origins) {
        if ((origins == null) || origins.isEmpty()) {
            return Optional.empty();
        }
        List<String> hfrAffinityDomains = getHFRAffinityDomains();
        List<Organization> domainOrgList = origins.stream().filter(
                    org -> org.hasIdentifier() && getAffinityDomainIdentifier(hfrAffinityDomains, org).isPresent())
                .collect(Collectors.toList());
        if (domainOrgList.isEmpty()) {
            return Optional.empty();
        }
        Organization organization = domainOrgList.get(0);
        Optional<Identifier> identifier = getAffinityDomainIdentifier(hfrAffinityDomains, organization);
        return  identifier.isPresent() ? Optional.of(Pair.of(identifier.get().getValue(), organization.getName())) : Optional.empty();
    }
}
