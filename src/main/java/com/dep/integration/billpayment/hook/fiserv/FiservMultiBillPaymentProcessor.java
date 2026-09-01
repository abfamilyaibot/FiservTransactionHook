package com.dep.integration.billpayment.hook.fiserv;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import com.dep.integration.billpayment.hook.fiserv.dto.common.CbsApiException;
import com.dep.integration.billpayment.hook.fiserv.dto.common.EndpointAttributes;
import com.dep.integration.billpayment.hook.fiserv.dto.MultiBillRequestDetail;
import com.dep.integration.billpayment.hook.fiserv.dto.common.Error;
import com.dep.integration.billpayment.hook.fiserv.dto.common.MultiBillResponseDetail;
import com.dep.integration.billpayment.hook.fiserv.dto.common.Request;
import com.dep.integration.billpayment.hook.fiserv.dto.FiservRequest;
import com.dep.integration.billpayment.hook.fiserv.dto.jaxb.arrays.ArrayOfanyType;
import com.dep.integration.billpayment.hook.fiserv.dto.jaxb.coreapi.BillPayMaintenanceRequest;
import com.dep.integration.billpayment.hook.fiserv.dto.jaxb.coreapi.Input;
import com.dep.integration.billpayment.hook.fiserv.dto.jaxb.coreapi.SubmitRequest;
import com.dep.integration.billpayment.hook.fiserv.dto.jaxb.coreapi.TransactionInput;
import com.dep.integration.billpayment.hook.fiserv.dto.jaxb.coreapi.UAInput;
import com.dep.integration.billpayment.hook.fiserv.dto.jaxb.envelope.Body;
import com.dep.integration.billpayment.hook.fiserv.dto.jaxb.envelope.Envelope;
import com.dep.integration.billpayment.hook.fiserv.dto.jaxb.billpaymaintenance.ArrayOfBillPayAllotments;
import com.dep.integration.billpayment.hook.fiserv.dto.jaxb.billpaymaintenance.BillPayAllotments;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class FiservMultiBillPaymentProcessor extends MultiBillPaymentProcessor {

   protected static final ObjectMapper FISERV_OBJECT_MAPPER = new ObjectMapper()
       .setSerializationInclusion(JsonInclude.Include.NON_NULL)
       .registerModule(new JavaTimeModule())
       .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
       .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
   private static final DatatypeFactory XML_DATATYPE_FACTORY = createDatatypeFactory();

   public FiservMultiBillPaymentProcessor() {
   }

   public FiservMultiBillPaymentProcessor(boolean isTestMode) {
      this.isTestMode = isTestMode;
   }

   @Override
   public ApiClient createApiClient(EndpointAttributes endpointAttributes) {
      HttpClient httpClient = HttpClient.newBuilder()
          .connectTimeout(Duration.ofMillis(endpointAttributes.connTimeoutMs()))
          .build();
      return new FiservApiClient(
          httpClient,
          endpointAttributes.serverUri(),
          Duration.ofMillis(endpointAttributes.readTimeoutMs()),
          isTestMode
      );
   }

   @Override
   protected Request deserializeRequest(String depRequestJson) {
      try {
         return FISERV_OBJECT_MAPPER.readValue(depRequestJson, FiservRequest.class);
      } catch (JsonProcessingException e) {
         throw new IllegalArgumentException("Invalid request JSON", e);
      }
   }

   @Override
   protected Error apiErrorResponseJson(Exception e) {
      if (e instanceof CbsApiException cbsApiException &&
              cbsApiException.responseBodyJson() != null && !cbsApiException.responseBodyJson().isBlank() &&
              cbsApiException.errorCode() != null && !cbsApiException.errorCode().isBlank()) {
         return new Error(cbsApiException.errorCode(), cbsApiException.responseBodyJson());
      }
      return new Error("OF5005", e.getMessage());
   }

   @Override
   protected MultiBillResponseDetail processDetail(
       ApiClient apiClient,
       Request depRequest,
       MultiBillRequestDetail detail
   ) {
      FiservRequest fiservRequest = (FiservRequest) depRequest;
      Envelope envelope = generateEnvelope(fiservRequest, detail);

      try {
         return apiClient.payBill(
             "/bill-payment/payment/immediate",
             depRequest,
             envelope
         );
      } catch (Exception e) {
         return new MultiBillResponseDetail(
             null,
             null,
             apiErrorResponseJson(e)
         );
      }
   }

   private Envelope generateEnvelope(
       FiservRequest fiservRequest,
       MultiBillRequestDetail detail
   ) {
      var extRequest = new BillPayMaintenanceRequest();
      var allotments = new ArrayOfBillPayAllotments();
      extRequest.setBillPaymentAllotments(allotments);
      extRequest.setUserId(fiservRequest.cbsContext().userId());

      var allotment = new BillPayAllotments();
      allotments.getBillPayAllotments().add(allotment);

      allotment.setAccountNumber(Long.parseLong(fiservRequest.multiBillRequest().debitAccount())); 
      allotment.setAmount(detail.paymentAmount()); 
      allotment.setCurrencyCode(detail.currency()); 
      if ( (isScheduledPayment(detail) || isRecurringPayment(detail) ) && detail.paymentDate() != null) {
         allotment.setEffectiveDate(toXmlDateTime(detail.paymentDate())); 
      }
      if ( isRecurringPayment(detail) && detail.paymentEndDate() != null) {
         String newPlusOneEndDate = LocalDate.parse(detail.paymentEndDate()).plusDays(1).toString();
         allotment.setEndDate(toXmlDateTime(newPlusOneEndDate));
      }
      if ( isRecurringPayment(detail) && detail.cbsFrequencyType() != null) {
         allotment.setCallPeriodCode(detail.cbsFrequencyType()); 
      }
      allotment.setTransactionSourceCode("API");
      allotment.setVendorAccountNumber(detail.vendorAccountNumber()); 
      allotment.setVendorID(detail.vendorId()); 

      var submitRequest = new SubmitRequest();
      var transactionInput = new TransactionInput();
      var input = new Input();
      var extensions = new ArrayOfanyType();
      extensions.getAnyType().add(extRequest);
      
      input.setExtensionRequests(extensions);

      input.setUserAuthentication(getUserAuthentication(fiservRequest));
      transactionInput.setInput(input);
      transactionInput.setShouldCommitOrRollback(true);
      submitRequest.setInput(transactionInput);

      Envelope envelope = new Envelope();
      Body body = new Body();
      envelope.setBody(body);
      body.setSubmitRequest(submitRequest);

      return envelope;
   }

   private static XMLGregorianCalendar toXmlDateTime(String date) {
      LocalDate localDate = LocalDate.parse(date);
      return XML_DATATYPE_FACTORY.newXMLGregorianCalendar(
          localDate.getYear(),
          localDate.getMonthValue(),
          localDate.getDayOfMonth(),
          0,
          0,
          0,
          0,
          DatatypeConstants.FIELD_UNDEFINED
      );
   }

   private static DatatypeFactory createDatatypeFactory() {
      try {
         return DatatypeFactory.newInstance();
      } catch (DatatypeConfigurationException e) {
         throw new IllegalStateException("Unable to create XML datatype factory", e);
      }
   }

   private UAInput getUserAuthentication(FiservRequest request) {
      var userAuth = new UAInput();
      userAuth.setApplID(request.cbsContext().applID()); 
      userAuth.setAuthorizationType("SingleSignOn");
      userAuth.setNetworkNodeName(request.cbsContext().networkNodeName()); 
      userAuth.setPassword(unescapeXml(request.cbsContext().password())); 
      return userAuth;
   }

   private String unescapeXml(String value) {
      if (value == null) {
         return null;
      }

      String unescaped = value;
      for (int i = 0; i < 3; i++) {
         String next = unescaped
             .replace("&lt;", "<")
             .replace("&gt;", ">")
             .replace("&quot;", "\"")
             .replace("&apos;", "'")
             .replace("&amp;", "&");
         if (next.equals(unescaped)) {
            return next;
         }
         unescaped = next;
      }
      return unescaped;
   }
}
