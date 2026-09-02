package com.dep.integration.transaction.hook.fiserv;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;

import javax.xml.transform.stream.StreamSource;

import com.dep.integration.transaction.hook.fiserv.dto.FiservRequest;
import com.dep.integration.transaction.hook.fiserv.dto.common.CbsApiException;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi.AccountTransactionHistoryResponse;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi.BillPayHistoryResponse;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi.ExtensionResponseBase;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi.Output;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi.ResponseBase;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi.TransactionHistoryInquiryResponse;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.envelope.ObjectFactory;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.envelope.Envelope;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.intellect.commons.loggers.ApplicationLogger;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FiservApiClient {

    private static final String JAXB2_CONTEXT_FACTORY_PROPERTY = "javax.xml.bind.JAXBContextFactory";
    private static final String JAXB2_GLASSFISH_JAXB_CONTEXT_FACTORY = "com.sun.xml.bind.v2.JAXBContextFactory";

    private static final String JAXB4_CONTEXT_FACTORY_PROPERTY = "jakarta.xml.bind.JAXBContextFactory";
    private static final String JAXB4_GLASSFISH_JAXB_CONTEXT_FACTORY = "org.glassfish.jaxb.runtime.v2.JAXBContextFactory";

    private static final String JAXB_CONTEXT_PATH = String.join(":",
            "com.dep.integration.transaction.hook.fiserv.dto.jaxb.arrays",
            "com.dep.integration.transaction.hook.fiserv.dto.jaxb.billpayhistory",
            "com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi",
            "com.dep.integration.transaction.hook.fiserv.dto.jaxb.envelope",
            "com.dep.integration.transaction.hook.fiserv.dto.jaxb.messages"
    );

    private static final Logger LOG = LogManager.getLogger(FiservApiClient.class);
    private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private static final XmlMapper XML_OBJECT_MAPPER = new XmlMapper();

    static {
        System.setProperty(JAXB2_CONTEXT_FACTORY_PROPERTY, JAXB2_GLASSFISH_JAXB_CONTEXT_FACTORY);
        System.setProperty(JAXB4_CONTEXT_FACTORY_PROPERTY, JAXB4_GLASSFISH_JAXB_CONTEXT_FACTORY);
    }

    private boolean isTestMode = false;

    private final HttpClient httpClient;
    protected final String baseUri;
    protected final Duration readTimeout;
    private final ApplicationLogger logger = ApplicationLogger.getInstance("FiservApiClient");
    private final JAXBContext jaxbContext;

    public FiservApiClient(HttpClient httpClient, String baseUri, Duration readTimeout) {
        this(httpClient, baseUri, readTimeout, false);
    }

    public FiservApiClient(HttpClient httpClient, String baseUri, Duration readTimeout, boolean isTestMode) {
        this.httpClient = httpClient;
        this.baseUri = baseUri;
        this.readTimeout = readTimeout;
        this.isTestMode = isTestMode;
        this.jaxbContext = initJAXBContext();
    }

    private JAXBContext initJAXBContext() {
        try {
            return JAXBContext.newInstance(
                    JAXB_CONTEXT_PATH,
                    FiservApiClient.class.getClassLoader()
            );
        } catch (JAXBException e) {
            throw new IllegalStateException("Unable to initialize Fiserv JAXB context", e);
        }
    }

    private Marshaller createMarshaller() {
        try {
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            jaxbMarshaller.setProperty("org.glassfish.jaxb.namespacePrefixMapper", new NSPrefixMapper());
            return jaxbMarshaller;
        } catch (JAXBException e) {
            throw new IllegalStateException("Unable to initialize Fiserv JAXB marshaller", e);
        }
    }

    protected HttpRequest generateHttpRequest(String requestSoapXml, FiservRequest depRequest) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(baseUri))
                .timeout(readTimeout)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "http://www.opensolutions.com/CoreApi/ICoreApiService/SubmitRequest")
                .POST(HttpRequest.BodyPublishers.ofString(requestSoapXml));
        return requestBuilder.build();
    }

    public FiservResponse getTransactions(
            FiservRequest depRequest,
            Object cbsRequest
    ) throws CbsApiException {
        try {
            String requestSoapXml = generateSoapXml(cbsRequest);

            HttpRequest httpRequest = generateHttpRequest(requestSoapXml, depRequest);

            String requestLogMessage = "Fiserv HttpRequest : " + httpRequest
                    + " with headers: " + httpRequest.headers().map()
                    + " with body: " + requestSoapXml;
            logger.logInfo(requestLogMessage);
            if (isTestMode) {
                LOG.info(requestLogMessage);
            }

            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            String responseLogMessage = "Fiserv HttpResponse : " + response + " with body: " + response.body();
            logger.logInfo(responseLogMessage);
            if (isTestMode) {
                LOG.info(responseLogMessage);
            }

            String responseBody = response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throwCBS_FAULT(responseBody);
            }

            Envelope responseEnvelope = parseSoapEnvelope(responseBody);
            return createFiservResponse(responseEnvelope, responseBody);

        } catch (IOException e) {
            throw new RuntimeException("Unable to call Fiserv API", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while calling Fiserv API", e);
        } catch (JAXBException e) {
            throw new RuntimeException("Unable to parse Fiserv SOAP XML", e);
        }
    }

    private String generateSoapXml(Object cbsRequest) {
        try {
            StringWriter writer = new StringWriter();
            Marshaller marshaller = createMarshaller();
            marshaller.marshal(rootElement(cbsRequest), writer);
            return writer.toString();
        } catch (JAXBException e) {
            throw new RuntimeException("Unable to generate Fiserv SOAP XML", e);
        }
    }

    private Object rootElement(Object cbsRequest) {
        if (cbsRequest instanceof Envelope envelope) {
            return new ObjectFactory()
                    .createEnvelope(envelope);
        }
        return cbsRequest;
    }

    private Envelope parseSoapEnvelope(String responseBody) throws JAXBException {
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        JAXBElement<Envelope> envelopeElement = unmarshaller.unmarshal(
                new StreamSource(new StringReader(responseBody)),
                Envelope.class
        );
        return envelopeElement.getValue();
    }

    private FiservResponse createFiservResponse(Envelope responseEnvelope, String responseBody) throws CbsApiException{
        Output output = responseEnvelope.getBody().getSubmitRequestResponse().getSubmitRequestResult().getOutput();
        if (output == null || output.getUserAuthentication() == null) {
            throw new IllegalStateException("Unexpected response from banking host.");
        }
        if ( !Boolean.TRUE.equals(output.getUserAuthentication().isWasSuccessful())) {
            throwCBS_ERROR_AUTH(responseBody);
        }

        AccountTransactionHistoryResponse accountTransactionHistoryResponse = null;
        TransactionHistoryInquiryResponse transactionHistoryInquiryResponse = null;
        BillPayHistoryResponse billPayHistoryResponse = null;

        if (output.getResponses() != null) {
            for (ResponseBase response : output.getResponses().getResponseBase()) {
                if (response == null) {
                    continue;
                }
                if (!Boolean.TRUE.equals(response.isWasSuccessful())) {
                    throwCBS_ERROR(responseBody);
                }
                if (response instanceof AccountTransactionHistoryResponse typedResponse) {
                    accountTransactionHistoryResponse = typedResponse;
                }
                else if (response instanceof TransactionHistoryInquiryResponse typedResponse) {
                    transactionHistoryInquiryResponse = typedResponse;
                }
            }
        }

        if (output.getExtensionResponses() != null) {
            for (Object anyType : output.getExtensionResponses().getAnyType()) {
                Object value = unwrapJaxbElement(anyType);
                if (value instanceof ExtensionResponseBase response && !Boolean.TRUE.equals(response.isWasSuccessful())) {
                    throwCBS_ERROR_EXT(responseBody);
                }
                if (value instanceof BillPayHistoryResponse typedResponse) {
                    billPayHistoryResponse = typedResponse;
                }
            }
        }

        return new FiservResponse(
                accountTransactionHistoryResponse,
                billPayHistoryResponse,
                transactionHistoryInquiryResponse
        );
    }

    private Object unwrapJaxbElement(Object value) {
        if (value instanceof JAXBElement<?> element) {
            return element.getValue();
        }
        return value;
    }

    private void throwCBS_FAULT(String responseBody) throws CbsApiException {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("responseBody is empty");
        }
        try {
            JsonNode responseNode = XML_OBJECT_MAPPER.readTree(responseBody);
            JsonNode faultNode = childByLocalName(
                    childByLocalName(responseNode, "Body"),
                    "Fault");
            if (faultNode != null && !faultNode.isNull()) {
                String responseBodyJson = JSON_OBJECT_MAPPER.writeValueAsString(faultNode);
                throw new CbsApiException("Passing Fault to iTurmeric flow", responseBodyJson, "CBS_FAULT");
            }
            else {
                throw new IllegalStateException("Unexpected Error from banking host.");
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void throwCBS_ERROR_EXT(String responseBody) throws CbsApiException {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("responseBody is empty");
        }
        try {
            JsonNode responseNode = XML_OBJECT_MAPPER.readTree(responseBody);
            JsonNode extensionResponsesNode = childByLocalName(
                    childByLocalName(
                            childByLocalName(
                                    childByLocalName(
                                            childByLocalName(responseNode, "Body"),
                                            "SubmitRequestResponse"
                                    ),
                                    "SubmitRequestResult"
                            ),
                            "Output"
                    ),
                    "ExtensionResponses"
            );
            if (extensionResponsesNode != null && !extensionResponsesNode.isNull()) {
                String responseBodyJson = JSON_OBJECT_MAPPER.writeValueAsString(extensionResponsesNode);
                throw new CbsApiException("Passing ExtensionResponses to iTurmeric flow", responseBodyJson, "CBS_ERROR_EXT");
            }
            else {
                throw new IllegalStateException("Unexpected response from banking host.");
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void throwCBS_ERROR(String responseBody) throws CbsApiException {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("responseBody is empty");
        }
        try {
            JsonNode responseNode = XML_OBJECT_MAPPER.readTree(responseBody);
            JsonNode responsesNode = childByLocalName(
                    childByLocalName(
                            childByLocalName(
                                    childByLocalName(
                                            childByLocalName(responseNode, "Body"),
                                            "SubmitRequestResponse"
                                    ),
                                    "SubmitRequestResult"
                            ),
                            "Output"
                    ),
                    "Responses"
            );
            if (responsesNode != null && !responsesNode.isNull()) {
                String responseBodyJson = JSON_OBJECT_MAPPER.writeValueAsString(responsesNode);
                throw new CbsApiException("Passing Responses to iTurmeric flow", responseBodyJson, "CBS_ERROR");
            }
            else {
                throw new IllegalStateException("Unexpected response from banking host.");
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void throwCBS_ERROR_AUTH(String responseBody) throws CbsApiException {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("responseBody is empty");
        }
        try {
            JsonNode responseNode = XML_OBJECT_MAPPER.readTree(responseBody);
            JsonNode userAuthenticationNode = childByLocalName(
                    childByLocalName(
                            childByLocalName(
                                    childByLocalName(
                                            childByLocalName(responseNode, "Body"),
                                            "SubmitRequestResponse"
                                    ),
                                    "SubmitRequestResult"
                            ),
                            "Output"
                    ),
                    "UserAuthentication"
            );

            if (userAuthenticationNode != null && !userAuthenticationNode.isNull()) {
                String responseBodyJson = JSON_OBJECT_MAPPER.writeValueAsString(userAuthenticationNode);
                throw new CbsApiException("Passing UserAuthentication to iTurmeric flow", responseBodyJson, "CBS_ERROR_AUTH");
            }
            else {
                throw new IllegalStateException("Unexpected response from banking host.");

            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode childByLocalName(JsonNode node, String localName) {
        if (node == null  || !node.isObject()) {
            return null;
        }
        JsonNode directChild = node.get(localName);
        if (directChild != null) {
            return directChild;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (localName.equals(localName(field.getKey()))) {
                return field.getValue();
            }
        }
        return null;
    }

    private String localName(String fieldName) {
        int prefixSeparator = fieldName.indexOf(':');
        if (prefixSeparator >= 0) {
            return fieldName.substring(prefixSeparator + 1);
        }
        return fieldName;
    }

}
