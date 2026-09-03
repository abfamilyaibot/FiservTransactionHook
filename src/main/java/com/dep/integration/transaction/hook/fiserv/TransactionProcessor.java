package com.dep.integration.transaction.hook.fiserv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.dep.integration.transaction.hook.fiserv.dto.common.CbsApiException;
import com.dep.integration.transaction.hook.fiserv.dto.common.EndpointAttributes;
import com.dep.integration.transaction.hook.fiserv.dto.common.Error;
import com.dep.integration.transaction.hook.fiserv.dto.common.Response;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public abstract class TransactionProcessor {

   protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
       .setSerializationInclusion(JsonInclude.Include.NON_NULL)
       .registerModule(new JavaTimeModule())
       .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
       .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

   protected boolean isTestMode = false;

   protected abstract String process(String depRequestJson, EndpointAttributes endpointAttributes);

   protected Error apiErrorResponseJson(Exception e) {
      if (e instanceof CbsApiException cbsApiException && 
         cbsApiException.responseBodyJson() != null && !cbsApiException.responseBodyJson().isBlank()) {
         return new Error("CBS_ERROR", cbsApiException.responseBodyJson());
      }
      return new Error("OF5005", e.getMessage());
   }

   protected String serializeResponse(Response response) {
      try {
         return OBJECT_MAPPER.writeValueAsString(response);
      } catch (JsonProcessingException e) {
         throw new IllegalStateException("Unable to serialize response", e);
      }
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
