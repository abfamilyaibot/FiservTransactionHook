package com.dep.integration.transaction.hook.fiserv;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.billpayhistory.BillPayment;
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
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.messages.Transaction;
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
      transactionInput.setShouldCommitOrRollback(true); 
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
      // filter by  RtxnTypeCodes not working, need to do manual filtering on AccountTransactionHistoryResponse
      request.setRtxnTypeCodes(getRTxnTypeCodes(criteriaDetails.filterType()));
      

      if (criteriaDetails.filterType() == CriteriaDetails.FilterType.D ||
          criteriaDetails.filterType() == CriteriaDetails.FilterType.C) {
            // filter by DebitCreditOnly not working, need to do manual filtering on AccountTransactionHistoryResponse
         request.setDebitCreditOnly(criteriaDetails.filterType().value());
      }

      applySearchCriteria(request, criteriaDetails);
      return request;
   }

   private String getRTxnTypeCodes(CriteriaDetails.FilterType filterType) {
      if (filterType == null) {
         return null;
      }
      switch (filterType) {
         case BILL:
            return "BPMT";
         case CHEQUE:
            return "CWTH";
         default:
            return null;
      }
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
      // TODO: confirm date option is effectiveDate to align
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
      // TODO: confirm the date search is for effectiveDate to align with the other requests
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
         case CHEQUE_NUMBER -> {
            // FromCheckNumber, ThroughtCheckNumber Not working, need to do manual filtering on AccountTransactionHistoryResponse
            Long chequeNumber = toLong(searchValue);
            request.setFromCheckNumber(chequeNumber);
            request.setThroughCheckNumber(chequeNumber);
         }
         case AMOUNT -> {
            // FromAmount, ThroughAmount Not working, need to do manual filtering on AccountTransactionHistoryResponse
            Double amount = toDouble(searchValue);
            request.setFromAmount(amount);
            request.setThroughAmount(amount);
         }
         default -> {
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

            // for 'search by Description': search the assembled CasaTransactionDtl transactionDescription field
            casatransactiondtls = sesarchCasaTransactionDtlByDescription( casatransactiondtls, depRequest.criteriaDetails());

            CasaTransactionDtlsResponse casaTransactionDtlsResponse =
                    new CasaTransactionDtlsResponse(casatransactiondtls, casatransactiondtls.size());

            Response response = new Response(casaTransactionDtlsResponse, imageCachesResponse, null);

            return serializeResponse(response);

        } catch (Exception e) {
            Error error = apiErrorResponseJson(e);
            return serializeResponse(new Response(null, null, error));
        }
    }

    private List<FiservTransaction> getFiservTransactions(FiservApiClient api, FiservRequest depRequest) throws CbsApiException {
      Envelope envelope = generateEnvelope(depRequest);
      FiservResponse fiservResponse = api.getTransactions(depRequest, envelope);
      return convertFiservTransactions(fiservResponse);
    }

    private List<FiservTransaction> convertFiservTransactions(FiservResponse fiservResponse) {
      if (fiservResponse == null ||
          fiservResponse.accountTransactionHistoryResponse() == null ||
          fiservResponse.accountTransactionHistoryResponse().getTransactions() == null) {
         return List.of();
      }

      Map<Long, BillPayment> billPaymentsByTransactionNumber = new HashMap<>();
      if (fiservResponse.billPayHistoryResponse() != null &&
          fiservResponse.billPayHistoryResponse().getBillPaymentList() != null) {
         for (BillPayment billPayment : fiservResponse.billPayHistoryResponse().getBillPaymentList().getBillPayment()) {
            Long transactionNumber = getBillPaymentTransactionNumber(billPayment);
            if (transactionNumber != null) {
               billPaymentsByTransactionNumber.put(transactionNumber, billPayment);
            }
         }
      }

      Map<Long, Transaction> transactionsByTransactionNumber = new HashMap<>();
      if (fiservResponse.transactionHistoryInquiryResponse() != null &&
          fiservResponse.transactionHistoryInquiryResponse().getTransactions() != null) {
         for (Transaction transaction : fiservResponse.transactionHistoryInquiryResponse().getTransactions().getTransaction()) {
            if (transaction != null && transaction.getTransactionNumber() != null) {
               transactionsByTransactionNumber.put(transaction.getTransactionNumber(), transaction);
            }
         }
      }

      List<FiservTransaction> fiservTransactions = new ArrayList<>();
      for (Rtxn rtxn : fiservResponse.accountTransactionHistoryResponse().getTransactions().getRtxn()) {
         if (rtxn == null) {
            continue;
         }
         Long transactionNumber = rtxn.getRtxnNumber();
         fiservTransactions.add(new FiservTransaction(
             rtxn,
             billPaymentsByTransactionNumber.get(transactionNumber),
             transactionsByTransactionNumber.get(transactionNumber)
         ));
      }
      return fiservTransactions;
    }

    private Long getBillPaymentTransactionNumber(BillPayment billPayment) {
      if (billPayment == null ||
          billPayment.getTransactionList() == null ||
          billPayment.getTransactionList().getTransaction().isEmpty() ||
          billPayment.getTransactionList().getTransaction().get(0) == null) {
         return null;
      }
      // note: billPayment.getBillPayTransactionNumber() is different and should not be used for RtxnNumber lookup
      return billPayment.getTransactionList().getTransaction().get(0).getTransactionNumber();
    }
    

    private List<FiservTransaction> filterTransactions(List<FiservTransaction> transactions, CriteriaDetails criteriaDetails) {
      if (transactions == null || transactions.isEmpty()) {
         return List.of();
      }
      if (criteriaDetails == null) {
         return transactions;
      }

      return transactions.stream()
          .filter(transaction -> matchesFilterType(transaction, criteriaDetails.filterType()))
          .filter(transaction -> matchesSearchCriteria(transaction, criteriaDetails))
          .toList();
    }

    private boolean matchesFilterType(FiservTransaction transaction, CriteriaDetails.FilterType filterType) {
      if (filterType == null) {
         return true;
      }

      Rtxn rtxn = transaction == null ? null : transaction.rtxn();
      if (rtxn == null) {
         return false;
      }

      return switch (filterType) {
         // filter: C -> DebitCredit = C
         case C -> "C".equals(rtxn.getDebitCredit());
         // filter: D -> DebitCredit = D
         case D -> "D".equals(rtxn.getDebitCredit());
         // filter: BILL -> RtxnTypeCode = BPMT
         case BILL -> "BPMT".equals(rtxn.getRtxnTypeCode());
         // filter: CHEQUE -> RtxnTypeCode = CWTH
         case CHEQUE -> "CWTH".equals(rtxn.getRtxnTypeCode());
      };
    }

    private boolean matchesSearchCriteria(FiservTransaction transaction, CriteriaDetails criteriaDetails) {
      if (criteriaDetails.searchType() == null ||
          criteriaDetails.searchValue() == null ||
          criteriaDetails.searchValue().isBlank()) {
         return true;
      }

      return switch (criteriaDetails.searchType()) {
         case DESCRIPTION -> true;
         // search CONFIRMATION_NUMBER -> RtxnTypeCode = BPMT and BillPaymentTransactionNumber matches
         case CONFIRMATION_NUMBER -> matchesConfirmationNumber(transaction, criteriaDetails.searchValue());
         // search AMOUNT -> absolute value of TransactionAmount matches
         case AMOUNT -> matchesAmount(transaction, toDouble(criteriaDetails.searchValue()));
         // search CHEQUE -> CheckNumber matches
         case CHEQUE_NUMBER -> matchesChequeNumber(transaction, toLong(criteriaDetails.searchValue()));
      };
    }

    private boolean matchesConfirmationNumber(FiservTransaction transaction, String confirmationNumber) {
      return transaction != null &&
          transaction.rtxn() != null &&
          transaction.billPayment() != null &&
          "BPMT".equals(transaction.rtxn().getRtxnTypeCode()) &&
          confirmationNumber.equals(String.valueOf(transaction.billPayment().getBillPayTransactionNumber()));
    }

    private boolean matchesAmount(FiservTransaction transaction, Double amount) {
      return transaction != null &&
          transaction.rtxn() != null &&
          transaction.rtxn().getTransactionAmount() != null &&
          amount != null &&
          Double.compare(Math.abs(transaction.rtxn().getTransactionAmount()), Math.abs(amount)) == 0;
    }

    private boolean matchesChequeNumber(FiservTransaction transaction, Long chequeNumber) {
      return transaction != null &&
          transaction.rtxn() != null &&
          transaction.rtxn().getCheckNumber() != null &&
          transaction.rtxn().getCheckNumber().equals(chequeNumber);
    }

   private List<Rtxn> getChequeFiservTransactions(List<FiservTransaction> transactions) {
      if (transactions == null || transactions.isEmpty()) {
         return List.of();
      }
      return transactions.stream()
          .map(FiservTransaction::rtxn)
          .filter(rtxn -> rtxn != null && "CWTH".equals(rtxn.getRtxnTypeCode()))
          .toList();
   }


   private List<CasaTransactionDtl> sesarchCasaTransactionDtlByDescription(List<CasaTransactionDtl> casaTransactionDtls, CriteriaDetails criteriaDetails) {
      // matches assembled CasaTransactionDtl.transactionDescription for search by DESCRIPTION, case insensitive
      if (casaTransactionDtls == null || casaTransactionDtls.isEmpty() ||
          criteriaDetails == null ||
          criteriaDetails.searchType() != CriteriaDetails.SearchType.DESCRIPTION ||
          criteriaDetails.searchValue() == null ||
          criteriaDetails.searchValue().isBlank()) {
         return casaTransactionDtls;
      }

      String searchValue = criteriaDetails.searchValue().toLowerCase();
      return casaTransactionDtls.stream()
          .filter(transaction -> descriptionContains(transaction, searchValue))
          .toList();
    }

   private boolean descriptionContains(CasaTransactionDtl transaction, String searchValue) {
      return transaction != null &&
          transaction.transactionDescription() != null &&
          transaction.transactionDescription().toLowerCase().contains(searchValue);
   }

   private CasaTransactionDtl mapToCasaTransactionDtl(FiservTransaction transaction, FiservRequest depRequest) {
      return null; // TODO
   }
}
