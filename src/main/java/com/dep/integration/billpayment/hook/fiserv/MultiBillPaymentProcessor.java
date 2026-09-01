package com.dep.integration.billpayment.hook.fiserv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.dep.integration.billpayment.hook.fiserv.dto.common.CbsApiException;
import com.dep.integration.billpayment.hook.fiserv.dto.common.EndpointAttributes;
import com.dep.integration.billpayment.hook.fiserv.dto.common.Error;
import com.dep.integration.billpayment.hook.fiserv.dto.MultiBillRequestDetail;
import com.dep.integration.billpayment.hook.fiserv.dto.common.MultiBillResponseDetail;
import com.dep.integration.billpayment.hook.fiserv.dto.common.Request;
import com.dep.integration.billpayment.hook.fiserv.dto.common.Response;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public abstract class MultiBillPaymentProcessor {

   private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
       .setSerializationInclusion(JsonInclude.Include.NON_NULL)
       .registerModule(new JavaTimeModule())
       .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
       .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

   protected boolean isTestMode = false;

   public String process(String depRequestJson, EndpointAttributes endpointAttributes) {
      Request depRequest = deserializeRequest(depRequestJson);

      ApiClient api = createApiClient(endpointAttributes);
      var responseDetails = new ArrayList<MultiBillResponseDetail>();
      for (MultiBillRequestDetail detail : depRequest.multiBillRequest().multiBillRequestDetails()) {
         responseDetails.add(validateAndProcessDetail(api, depRequest, detail));
      }

      Response response = new Response(responseDetails);
      return serializeResponse(response);
   }

   public String asyncProcess(String depRequestJson, EndpointAttributes endpointAttributes) {
      Request depRequest = deserializeRequest(depRequestJson);

      ApiClient api = createApiClient(endpointAttributes);
      List<MultiBillRequestDetail> details = depRequest.multiBillRequest().multiBillRequestDetails();
      if (details.isEmpty()) {
         return serializeResponse(new Response(List.of()));
      }

      ExecutorService executorService = Executors.newFixedThreadPool(details.size());
      ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
      try {
         List<CompletableFuture<MultiBillResponseDetail>> responseDetailFutures = details.stream()
                 .map(detail -> CompletableFuture.supplyAsync(
                         () -> validateAndprocessDetailWithContextClassLoader(api, depRequest, detail, contextClassLoader),
                         executorService
                 ))
                 .toList();

         List<MultiBillResponseDetail> responseDetails = responseDetailFutures.stream()
             .map(CompletableFuture::join)
             .toList();

         Response response = new Response(responseDetails);
         return serializeResponse(response);
      } finally {
         executorService.shutdown();
      }
   }

   private MultiBillResponseDetail validateAndprocessDetailWithContextClassLoader(
           ApiClient api,
           Request depRequest,
           MultiBillRequestDetail detail,
           ClassLoader contextClassLoader
   ) {
      Thread currentThread = Thread.currentThread();
      ClassLoader originalClassLoader = currentThread.getContextClassLoader();
      try {
         currentThread.setContextClassLoader(contextClassLoader);
         return validateAndProcessDetail(api, depRequest, detail);
      } finally {
         currentThread.setContextClassLoader(originalClassLoader);
      }
   }

   protected abstract ApiClient createApiClient(EndpointAttributes endpointAttributes);

   protected abstract Request deserializeRequest(String depRequestJson);

   private MultiBillResponseDetail validateAndProcessDetail(ApiClient apiClient,
                                                            Request depRequest,
                                                            MultiBillRequestDetail detail) {
      if ( !isImmediatePayment(detail) && !isScheduledPayment(detail) && !isRecurringPayment(detail) ) {
         return new MultiBillResponseDetail(
                 null,
                 null,
                 new Error("Invalid_ScheduleType", "Invalid Schedule Type")
         );
      }
      return processDetail(apiClient, depRequest, detail);
   }

   protected abstract MultiBillResponseDetail processDetail(
       ApiClient apiClient,
       Request depRequest,
       MultiBillRequestDetail detail
   );

   protected boolean isImmediatePayment(MultiBillRequestDetail detail) {
      return "1".equals(detail.scheduleType());
   }

   protected boolean isScheduledPayment(MultiBillRequestDetail detail) {
      return "2".equals(detail.scheduleType());
   }

   protected boolean isRecurringPayment(MultiBillRequestDetail detail) {
      return "3".equals(detail.scheduleType());
   }

   protected Error apiErrorResponseJson(Exception e) {
      if (e instanceof CbsApiException cbsApiException && 
         cbsApiException.responseBodyJson() != null && !cbsApiException.responseBodyJson().isBlank()) {
         return new Error("CBS_ERROR", cbsApiException.responseBodyJson());
      }
      return new Error("OF5005", e.getMessage());
   }

   private String serializeResponse(Response response) {
      try {
         return OBJECT_MAPPER.writeValueAsString(response);
      } catch (JsonProcessingException e) {
         throw new IllegalStateException("Unable to serialize response", e);
      }
   }

    protected String normalizeServerUri(String serverUri) {
      if (serverUri == null || serverUri.isBlank()) {
         throw new IllegalArgumentException("Endpoint serverUri is required");
      }

      String normalized = serverUri.strip();
      while (normalized.endsWith("/")) {
         normalized = normalized.substring(0, normalized.length() - 1);
      }
      return normalized;
   }

   protected SSLContext createSslContext(EndpointAttributes endpointAttributes) {
      try {
         KeyStore keyStore = loadKeyStore(
             endpointAttributes.keystorePath(),
             endpointAttributes.keystorePassword(),
             keyStoreType(endpointAttributes.keystoreType())
         );

         KeyManagerFactory keyManagerFactory =
             KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
         keyManagerFactory.init(keyStore, password(endpointAttributes.keystorePassword()));

         TrustManagerFactory trustManagerFactory =
             TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
         trustManagerFactory.init(trustStore(endpointAttributes));

         SSLContext sslContext = SSLContext.getInstance("TLS");
         sslContext.init(
             keyManagerFactory.getKeyManagers(),
             trustManagerFactory.getTrustManagers(),
             null
         );
         return sslContext;
      } catch (IOException | GeneralSecurityException e) {
         throw new IllegalStateException("Unable to load endpoint keystore", e);
      }
   }

   private KeyStore trustStore(EndpointAttributes endpointAttributes)
       throws IOException, GeneralSecurityException {
      if (endpointAttributes.truststorePath() == null || endpointAttributes.truststorePath().isBlank()) {
         return null;
      }

      return loadKeyStore(
          endpointAttributes.truststorePath(),
          endpointAttributes.truststorePassword(),
          keyStoreType(endpointAttributes.truststoreType())
      );
   }

   private KeyStore loadKeyStore(
       String path,
       String password,
       String type
   ) throws IOException, GeneralSecurityException {
      KeyStore keyStore = KeyStore.getInstance(type);
      try (var inputStream = Files.newInputStream(Path.of(path))) {
         keyStore.load(inputStream, password(password));
      }
      return keyStore;
   }

   private char[] password(String value) {
      if (value == null) {
         return new char[0];
      }
      return value.toCharArray();
   }

   private String keyStoreType(String configuredType) {
      if (configuredType == null || configuredType.isBlank()) {
         return KeyStore.getDefaultType();
      }
      return configuredType;
   }
}
