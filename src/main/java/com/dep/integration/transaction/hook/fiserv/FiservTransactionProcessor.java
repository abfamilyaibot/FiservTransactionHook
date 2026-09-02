package com.dep.integration.transaction.hook.fiserv;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import com.dep.integration.transaction.hook.fiserv.dto.common.Response;
import com.dep.integration.transaction.hook.fiserv.dto.common.CasaTransactionDtl;
import com.dep.integration.transaction.hook.fiserv.dto.common.CasaTransactionDtlsResponse;
import com.dep.integration.transaction.hook.fiserv.dto.common.CbsApiException;
import com.dep.integration.transaction.hook.fiserv.dto.common.CriteriaDetails;
import com.dep.integration.transaction.hook.fiserv.dto.common.EndpointAttributes;
import com.dep.integration.transaction.hook.fiserv.dto.common.Error;
import com.dep.integration.transaction.hook.fiserv.dto.FiservImageCachesResponse;
import com.dep.integration.transaction.hook.fiserv.dto.FiservRequest;
import com.dep.integration.transaction.hook.fiserv.dto.FiservTransaction;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.arrays.ArrayOfanyType;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi.AccountTransactionHistoryRequest;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi.Input;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi.ArrayOfRequestBase;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi.BillPayHistoryRequest;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi.SubmitRequest;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi.TransactionInput;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi.TransactionHistoryInquiryRequest;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi.UAInput;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.envelope.Body;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.envelope.Envelope;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.messages.Rtxn;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class FiservTransactionProcessor extends TransactionProcessor {

   protected static final ObjectMapper FISERV_OBJECT_MAPPER = new ObjectMapper()
       .setSerializationInclusion(JsonInclude.Include.NON_NULL)
       .registerModule(new JavaTimeModule())
       .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
       .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
   private static final DatatypeFactory XML_DATATYPE_FACTORY = createDatatypeFactory();
   private static final String DEFAULT_SORT_BY = "TIMEUNIQUEEXTN";

   public FiservTransactionProcessor() {
   }

   public FiservTransactionProcessor(boolean isTestMode) {
      this.isTestMode = isTestMode;
   }

   protected FiservApiClient createApiClient(EndpointAttributes endpointAttributes) {
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

   private FiservRequest deserializeRequest(String depRequestJson) {
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

   // @Override
   // protected MultiBillResponseDetail processDetail(
   //     ApiClient apiClient,
   //     Request depRequest,
   //     MultiBillRequestDetail detail
   // ) {
   //    FiservRequest fiservRequest = (FiservRequest) depRequest;
   //    Envelope envelope = generateEnvelope(fiservRequest, detail);

   //    try {
   //       return apiClient.payBill(
   //           "/bill-payment/payment/immediate",
   //           depRequest,
   //           envelope
   //       );
   //    } catch (Exception e) {
   //       return new MultiBillResponseDetail(
   //           null,
   //           null,
   //           apiErrorResponseJson(e)
   //       );
   //    }
   // }

   private Envelope generateEnvelope(
       FiservRequest fiservRequest
   ) {
      AccountTransactionHistoryRequest accountTransactionHistoryRequest =
          createAccountTransactionHistoryRequest(fiservRequest);
      TransactionHistoryInquiryRequest transactionHistoryInquiryRequest =
          createTransactionHistoryInquiryRequest(fiservRequest);
      BillPayHistoryRequest billPayHistoryRequest =
          createBillPayHistoryRequest(fiservRequest);

      var submitRequest = new SubmitRequest();
      var transactionInput = new TransactionInput();
      var input = new Input();

      var requests = new ArrayOfRequestBase();
      requests.getRequestBase().add(accountTransactionHistoryRequest);
      requests.getRequestBase().add(transactionHistoryInquiryRequest);
      input.setRequests(requests);

      var extensionRequests = new ArrayOfanyType();
      extensionRequests.getAnyType().add(billPayHistoryRequest);
      input.setExtensionRequests(extensionRequests);

      input.setUserAuthentication(getUserAuthentication(fiservRequest));
      transactionInput.setInput(input);
      transactionInput.setShouldCommitOrRollback(true); // TODO: review 
      submitRequest.setInput(transactionInput);

      Envelope envelope = new Envelope();
      Body body = new Body();
      envelope.setBody(body);
      body.setSubmitRequest(submitRequest);

      return envelope;
   }

   private AccountTransactionHistoryRequest createAccountTransactionHistoryRequest(FiservRequest fiservRequest) {
      CriteriaDetails criteriaDetails = fiservRequest.criteriaDetails();
      var request = new AccountTransactionHistoryRequest();
      if (criteriaDetails == null) {
         return request;
      }

      request.setAccountNumber(toLong(criteriaDetails.accountNumber()));
      request.setFromDate(toXmlDateTimeOrNull(criteriaDetails.startDate()));
      request.setThroughDate(toXmlDateTimeOrNull(criteriaDetails.endDate()));
      request.setSortOrder(toFiservSortOrder(criteriaDetails.sortingOrder()));
      request.setSortBy("EFFDATE");
      request.setSearchDateOption(3); // effectiveDate
      

      if (criteriaDetails.filterType() == CriteriaDetails.FilterType.D ||
          criteriaDetails.filterType() == CriteriaDetails.FilterType.C) {
         request.setDebitCreditOnly(criteriaDetails.filterType().value());
      }

      applySearchCriteria(request, criteriaDetails);
      return request;
   }

   private TransactionHistoryInquiryRequest createTransactionHistoryInquiryRequest(FiservRequest fiservRequest) {
      CriteriaDetails criteriaDetails = fiservRequest.criteriaDetails();
      var request = new TransactionHistoryInquiryRequest();
      if (criteriaDetails == null) {
         return request;
      }

      request.setAccountNumber(toLong(criteriaDetails.accountNumber()));
      request.setFromDate(toXmlDateTimeOrNull(criteriaDetails.startDate()));
      request.setThruDate(toXmlDateTimeOrNull(criteriaDetails.endDate()));
      request.setSearchDateOption(3); // effectiveDate
      return request;
   }

   private BillPayHistoryRequest createBillPayHistoryRequest(FiservRequest fiservRequest) {
      CriteriaDetails criteriaDetails = fiservRequest.criteriaDetails();
      var request = new BillPayHistoryRequest();
      if (criteriaDetails == null) {
         return request;
      }

      request.setAccountNumber(toLong(criteriaDetails.accountNumber()));
      request.setFromDate(toXmlDateTimeOrNull(criteriaDetails.startDate()));
      request.setThroughDate(toXmlDateTimeOrNull(criteriaDetails.endDate()));
      return request;
   }

   private void applySearchCriteria(
       AccountTransactionHistoryRequest request,
       CriteriaDetails criteriaDetails
   ) {
      if (criteriaDetails.searchType() == null || criteriaDetails.searchValue() == null ||
          criteriaDetails.searchValue().isBlank()) {
         return;
      }

      String searchValue = criteriaDetails.searchValue();
      switch (criteriaDetails.searchType()) {
         case DESCRIPTION -> {
            request.setExternalRtxnDescription(searchValue);
            request.setInternalRtxnDescription(searchValue);
         }
         case CONFIRMATION_NUMBER -> request.setTransactionReferenceNumber(searchValue); // TODO review
         case CHEQUE_NUMBER -> {
            Long chequeNumber = toLong(searchValue);
            request.setFromCheckNumber(chequeNumber);
            request.setThroughCheckNumber(chequeNumber);
         }
         case AMOUNT -> {
            Double amount = toDouble(searchValue);
            request.setFromAmount(amount);
            request.setThroughAmount(amount);
         }
      }
   }

   private static XMLGregorianCalendar toXmlDateTimeOrNull(String date) {
      if (date == null || date.isBlank()) {
         return null;
      }
      return toXmlDateTime(date);
   }

   private static Long toLong(String value) {
      if (value == null || value.isBlank()) {
         return null;
      }
      return Long.parseLong(value);
   }

   private static Double toDouble(String value) {
      if (value == null || value.isBlank()) {
         return null;
      }
      return Double.parseDouble(value);
   }

   private static String toFiservSortOrder(CriteriaDetails.SortingOrder sortingOrder) {
      if (sortingOrder == null) {
         return null;
      }
      return sortingOrder == CriteriaDetails.SortingOrder.DESC ? "D" : "A";
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

   private UAInput getUserAuthentication( FiservRequest request) {
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

   @Override
   public String process(String depRequestJson, EndpointAttributes endpointAttributes) {
        FiservRequest depRequest =  deserializeRequest(depRequestJson);

        FiservApiClient api = createApiClient(endpointAttributes);

        try {
            List<FiservTransaction> transactions = getFiservTransactions(api, depRequest);
            
            List<FiservTransaction> filteredTransactions = filterTransactions(transactions, depRequest.criteriaDetails());

            List<Rtxn> chequeTransactions = getChequeFiservTransactions(transactions);
            FiservImageCachesResponse imageCachesResponse = new FiservImageCachesResponse(chequeTransactions);

            List<CasaTransactionDtl> casatransactiondtls = filteredTransactions.stream()
                    .map(t -> mapToCasaTransactionDtl(t, depRequest))
                    .toList();
            CasaTransactionDtlsResponse casaTransactionDtlsResponse =
                    new CasaTransactionDtlsResponse(casatransactiondtls, casatransactiondtls.size());

            Response response = new Response(casaTransactionDtlsResponse, imageCachesResponse, null);

            return serializeResponse(response);

        } catch (Exception e) {
            Error error = apiErrorResponseJson(e);
            return serializeResponse(new Response(null, null, error));
        }
    }

    private List<FiservTransaction> getFiservTransactions(FiservApiClient api, FiservRequest depRequest) {
      return null; // TODO
      // FiservResponse getTransactions
    }

    private List<FiservTransaction> filterTransactions(List<FiservTransaction> transactions, CriteriaDetails criteriaDetails) {
      return null; // TODO
    }

   private List<Rtxn> getChequeFiservTransactions(List<FiservTransaction> transactions) {
         return null; // TODO
   }

   private CasaTransactionDtl mapToCasaTransactionDtl(FiservTransaction transaction, FiservRequest depRequest) {
      return null; // TODO
   }
}
