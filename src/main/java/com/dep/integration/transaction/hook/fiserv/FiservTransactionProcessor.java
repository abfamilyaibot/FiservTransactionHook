package com.dep.integration.transaction.hook.fiserv;

import java.math.BigDecimal;
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

import com.dep.integration.transaction.hook.fiserv.dto.common.CriteriaDetails.FilterType;
import com.dep.integration.transaction.hook.fiserv.dto.common.CasaTransactionDtl;
import com.dep.integration.transaction.hook.fiserv.dto.common.CasaTransactionDtlsResponse;
import com.dep.integration.transaction.hook.fiserv.dto.common.CbsApiException;
import com.dep.integration.transaction.hook.fiserv.dto.common.CriteriaDetails;
import com.dep.integration.transaction.hook.fiserv.dto.common.EndpointAttributes;
import com.dep.integration.transaction.hook.fiserv.dto.common.Error;
import com.dep.integration.transaction.hook.fiserv.dto.AccountInfo;
import com.dep.integration.transaction.hook.fiserv.dto.FiservApiResponse;
import com.dep.integration.transaction.hook.fiserv.dto.FiservImageCachesResponse;
import com.dep.integration.transaction.hook.fiserv.dto.FiservRequest;
import com.dep.integration.transaction.hook.fiserv.dto.FiservTransaction;
import com.dep.integration.transaction.hook.fiserv.dto.FiservResponse;
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
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.messages.ExchTxn;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.messages.Rtxn;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.messages.Transaction;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.messages.TransactionBalanceType;
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
      if (isLoanAccount(fiservRequest)) {
         requests.getRequestBase().add(transactionHistoryInquiryRequest);
      }
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
      request.setIsResultingBalance(true);
      request.setSearchDateOption(3); // effectiveDate
      // filter by  RtxnTypeCodes not working, need to do manual filtering on AccountTransactionHistoryResponse
      // request.setRtxnTypeCodes(getRTxnTypeCode(criteriaDetails.filterType()));
      

      // if (criteriaDetails.filterType() == CriteriaDetails.FilterType.D ||
      //     criteriaDetails.filterType() == CriteriaDetails.FilterType.C) {
      //       // filter by DebitCreditOnly not working, need to do manual filtering on AccountTransactionHistoryResponse
      //    request.setDebitCreditOnly(criteriaDetails.filterType().value());
      // }

      applySearchCriteria(request, criteriaDetails);
      return request;
   }

   private String getRTxnTypeCode(CriteriaDetails.FilterType filterType) {
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
      // note:  the date search is for effectiveDate to align with the other requests
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
      // note:  the date search is for effectiveDate to align with the other requests
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

            List<Rtxn> chequeTransactions = getChequeFiservTransactions(transactions, depRequest);
            FiservImageCachesResponse imageCachesResponse = new FiservImageCachesResponse(chequeTransactions);

            List<CasaTransactionDtl> casatransactiondtls = filteredTransactions.stream()
                    .map(t -> mapToCasaTransactionDtl(t, depRequest))
                    .toList();

            // for 'search by Description': search the assembled CasaTransactionDtl transactionDescription field
            casatransactiondtls = sesarchCasaTransactionDtlByDescription( casatransactiondtls, depRequest.criteriaDetails());

            CasaTransactionDtlsResponse casaTransactionDtlsResponse =
                    new CasaTransactionDtlsResponse(casatransactiondtls, casatransactiondtls.size());

            FiservResponse response = new FiservResponse(casaTransactionDtlsResponse, imageCachesResponse, null);

            return serializeResponse(response);

        } catch (Exception e) {
            Error error = apiErrorResponseJson(e);
            return serializeResponse(new FiservResponse(null, null, error));
        }
    }

   private String serializeResponse(FiservResponse response) {
      try {
         return FISERV_OBJECT_MAPPER.writeValueAsString(response);
      } catch (JsonProcessingException e) {
         throw new IllegalStateException("Unable to serialize response", e);
      }
   }

   private List<FiservTransaction> getFiservTransactions(FiservApiClient api, FiservRequest depRequest) throws CbsApiException {
      Envelope envelope = generateEnvelope(depRequest);
      FiservApiResponse fiservApiResponse = api.getTransactions(depRequest, envelope);
      return convertFiservTransactions(fiservApiResponse, isLoanAccount(depRequest));
   }

    private List<FiservTransaction> convertFiservTransactions(FiservApiResponse fiservApiResponse, boolean isLoanAccount) {
      if (fiservApiResponse == null ||
          fiservApiResponse.accountTransactionHistoryResponse() == null ||
          fiservApiResponse.accountTransactionHistoryResponse().getTransactions() == null) {
         return List.of();
      }

      Map<Long, BillPayment> billPaymentsByTransactionNumber = new HashMap<>();
      if (fiservApiResponse.billPayHistoryResponse() != null &&
          fiservApiResponse.billPayHistoryResponse().getBillPaymentList() != null) {
         for (BillPayment billPayment : fiservApiResponse.billPayHistoryResponse().getBillPaymentList().getBillPayment()) {
            Long transactionNumber = getBillPaymentTransactionNumber(billPayment);
            if (transactionNumber != null) {
               billPaymentsByTransactionNumber.put(transactionNumber, billPayment);
            }
         }
      }

      Map<Long, Transaction> transactionsByTransactionNumber = new HashMap<>();
      if (isLoanAccount &&
          fiservApiResponse.transactionHistoryInquiryResponse() != null &&
          fiservApiResponse.transactionHistoryInquiryResponse().getTransactions() != null) {
         for (Transaction transaction : fiservApiResponse.transactionHistoryInquiryResponse().getTransactions().getTransaction()) {
            if (transaction != null && transaction.getTransactionNumber() != null) {
               transactionsByTransactionNumber.put(transaction.getTransactionNumber(), transaction);
            }
         }
      }

      String accountCurrencyCode = fiservApiResponse.accountTransactionHistoryResponse().getAccountCurrencyCode();

      List<FiservTransaction> fiservTransactions = new ArrayList<>();
      for (Rtxn rtxn : fiservApiResponse.accountTransactionHistoryResponse().getTransactions().getRtxn()) {
         if (rtxn == null) {
            continue;
         }
         Long transactionNumber = rtxn.getRtxnNumber();
         fiservTransactions.add(new FiservTransaction(
             accountCurrencyCode,
             rtxn,
             getRTxnTypeCode(FilterType.BILL).equals(rtxn.getRtxnTypeCode()) ? billPaymentsByTransactionNumber.get(transactionNumber) : null,
             isLoanAccount ? transactionsByTransactionNumber.get(transactionNumber) : null
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
      // filter: C -> DebitCredit = C
      // filter: D -> DebitCredit = D
      // filter: BILL -> RtxnTypeCode = BPMT
      // filter: CHEQUE -> RtxnTypeCode = CWTH
      // search by AMOUNT -> absolute value of TransactionAmount matches
      // search by CHEQUE_NUMBER -> CheckNumber matches
      // search by CONFIRMATION_NUMBER -> RtxnTypeCode = BPMT and BillPaymentTransactionNumber matches
      // search by DESCRIPTION -> wait til FiservTransaction converted to CassaTransctionDtl to search for the assembled CassaTransctionDtl.TransactionDescription
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
         // filter: C -> DebitCredit = C; D -> DebitCredit = D
         case C, D -> filterType.value().equals(rtxn.getDebitCredit());
         // filter: BILL -> RtxnTypeCode = BPMT; CHEQUE -> RtxnTypeCode = CWTH
         case BILL, CHEQUE -> getRTxnTypeCode(filterType).equals(rtxn.getRtxnTypeCode());
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

   private List<Rtxn> getChequeFiservTransactions(List<FiservTransaction> transactions, FiservRequest depRequest) {
      if (transactions == null || transactions.isEmpty()) {
         return List.of();
      }
      AccountInfo accountInfo = depRequest == null ? null : depRequest.accountInfo();
      return transactions.stream()
          .map(FiservTransaction::rtxn)
          .filter(rtxn -> rtxn != null && "CWTH".equals(rtxn.getRtxnTypeCode()))
          .peek(rtxn -> populateChequeAccountInfo(rtxn, accountInfo))
          .toList();
   }

   private void populateChequeAccountInfo(Rtxn rtxn, AccountInfo accountInfo) {
      if (rtxn == null || accountInfo == null) {
         return;
      }
      rtxn.setRouteNumber(accountInfo.routeNumber());
      rtxn.setTransitNumber(accountInfo.transitNumber());
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

   private CasaTransactionDtl mapToCasaTransactionDtl(FiservTransaction fiservTransaction, FiservRequest depRequest) {
      // CasaTransactionDtl.tenantId: depRequest.depTenantId

      // CasaTransactionDtl.TransactionDescription: Rtxn.RtxnTypeDescription
      // if Rtxn.InternalRtxnDescription not empty: ' ' + Rtxn.InternalRtxnDescription
      // if BillPayment.VendorName not empty: + ' ' + BillPayment.VendorName
      // if Rtxn.ExchTxnGrp.ExchTxn.OtherAmount not empty: +  ' Exchange Amount: $' + absolute value of Rtxn.ExchTxnGrp.ExchTxn.OtherAmount
      // if Rtxn.ExchTxnGrp.ExchTxn.ExchangeRate not empty: +  ' Exchange Rate: ' + Rtxn.ExchTxnGrp.ExchTxn.ExchangeRate

      // CasaTransactionDtl.transactionReference: BillPayment.BillPayTransactionNumber if not empty, else Rtxn.TransactionReferenceNumber

      // CasaTransactionDtl.confirmationNumber: BillPayment.BillPayTransactionNumber if not empty
      // CasaTransactionDtl.merchantId: BillPayment.VendorID if not empty

      // CasaTransactionDtl.transactionDate: Rtxn.EffectiveDate in yyyy-MM-dd format
      // CasaTransactionDtl.valueDate: Rtxn.EffectiveDate in yyyy-MM-dd format

      // CasaTransactionDtl.balance: Rtxn.RunningBalance

      // CasaTransactionDtl.transactionAmount = absolute value of Rtxn.TransactionAmount

      // CasaTransactionDtl.principalAmount = absolute value of LoanTransaction.BalanceTypes.TransactionBalanceType.Amount with Transaction.BalanceTypes.TransactionBalanceType.BalanceTypeDescription contains 'Note Balance'
      // CasaTransactionDtl.interestChargeAmount = absolute value of LoanTransaction.BalanceTypes.TransactionBalanceType.Amount with Transaction.BalanceTypes.TransactionBalanceType.BalanceTypeDescription contains 'Note Interest'

      // CasaTransactionDtl.transactionCurrency = Rtxn.TransactionCurrency

      // CasaTransactionDtl.debitCreditFlag = Rtxn.DebitCredit
      // CasaTransactionDtl.transactionType = 'Credit' or 'Debit' based on Rtxn.DebitCredit
      // CasaTransactionDtl.transType = 'Credit' or 'Debit' based on Rtxn.DebitCredit

      // CasaTransactionDtl.transactionCategoryId: Rtxn.RtxnTypeCode: 'CWTH' -> 4, 'BPMT' -> 5; else Rtxn.DebitCredit 'Credit' -> 2, 'Debit' -> 3; else -> 1
      // CasaTransactionDtl.transactionCategory = Rtxn.RtxnTypeCode

      // CasaTransactionDtl.instrumentId = Rtxn.CheckNumber
      // CasaTransactionDtl.chequeNumber = Rtxn.CheckNumber

      // CasaTransactionDtl.accountHolderName = DepRequest.AccountHolderName

      // CasaTransactionDtl.exchangeAmount =  absolute value of Rtxn.ExchTxnGrp.ExchTxn.OtherAmount
      // CasaTransactionDtl.exchangeRate = Rtxn.ExchTxnGrp.ExchTxn.ExchangeRate

      // CasaTransactionDtl.accountNumber = Rtxn.AccountNumber

      Rtxn rtxn = fiservTransaction == null ? null : fiservTransaction.rtxn();
      BillPayment billPayment = fiservTransaction == null ? null : fiservTransaction.billPayment();
      Transaction loanTransaction = fiservTransaction == null ? null : fiservTransaction.loanTransaction();
      ExchTxn exchangeTransaction = getExchangeTransaction(rtxn);

      return new CasaTransactionDtl(
          getTenantId(depRequest),
          getTransactionDate(rtxn),
          getValueDate(rtxn),
          getRemarks(),
          getTransactionAmount(rtxn),
          getTransactionReference(rtxn, billPayment),
          getTransactionDescription(rtxn, billPayment, exchangeTransaction),
          getMerchantId(billPayment),
          getTransactionCategory(rtxn),
          getBalance(rtxn),
          getDebitCreditFlag(rtxn),
          getInstrumentId(rtxn),
          getTransactionType(rtxn),
          getChequeNumber(rtxn),
          getExchangeRate(exchangeTransaction),
          getExchangeAmount(exchangeTransaction),
          getAccountNumber(rtxn),
          getTransactionCurrency(fiservTransaction),
          getConfirmationNumber(billPayment),
          getAccountHolderName(depRequest),
          getPrincipalAmount(loanTransaction),
          getInterestChargeAmount(loanTransaction),
          getTransactionCategoryId(rtxn),
          getTransType(rtxn)
      );
   }

   private String getTenantId(FiservRequest depRequest) {
      return depRequest == null ? null : depRequest.depTenantId();
   }

   private String getTransactionCurrency(FiservTransaction fiservTransaction) {
      return fiservTransaction == null ? null : fiservTransaction.accountCurrencyCode();
   }

   private String getTransactionDate(Rtxn rtxn) {
      return rtxn == null ? null : toDateString(rtxn.getEffectiveDate());
   }

   private String getValueDate(Rtxn rtxn) {
      return getTransactionDate(rtxn);
   }

   private String getRemarks() {
      return null; // not mapped as transfer memo is part of transactionDescription (from Rtxn.InternalRtxnDescription) and cannot extract the transfer memo portion
   }

   private BigDecimal getTransactionAmount(Rtxn rtxn) {
      return rtxn == null ? null : toAbsBigDecimal(rtxn.getTransactionAmount());
   }

   private BigDecimal getBalance(Rtxn rtxn) {
      return rtxn == null ? null : toBigDecimal(rtxn.getRunningBalance());
   }

   private String getTransactionReference(Rtxn rtxn, BillPayment billPayment) {
      String billPayTransactionNumber = getConfirmationNumber(billPayment);
      return billPayTransactionNumber != null ? billPayTransactionNumber : (rtxn == null ? null : rtxn.getTransactionReferenceNumber());
   }

   private String getConfirmationNumber(BillPayment billPayment) {
      Long billPayTransactionNumber = getBillPayTransactionNumberValue(billPayment);
      return billPayTransactionNumber == null ? null : String.valueOf(billPayTransactionNumber);
   }

   private Long getBillPayTransactionNumberValue(BillPayment billPayment) {
      return billPayment == null ? null : billPayment.getBillPayTransactionNumber();
   }

   private String getMerchantId(BillPayment billPayment) {
      return billPayment == null || billPayment.getVendorID() == null ? null : String.valueOf(billPayment.getVendorID());
   }

   private String getTransactionCategory(Rtxn rtxn) {
      return rtxn == null ? null : rtxn.getRtxnTypeCode();
   }

   private String getDebitCreditFlag(Rtxn rtxn) {
      return rtxn == null ? null : rtxn.getDebitCredit();
   }

   private String getTransactionType(Rtxn rtxn) {
      return toDebitCreditType( getDebitCreditFlag(rtxn) );
   }

   private String getTransType(Rtxn rtxn) {
      return getTransactionType(rtxn);
   }

   private String toDebitCreditType(String debitCredit) {
      if ("C".equals(debitCredit) ) {
         return "Credit";
      }
      if ("D".equals(debitCredit)) {
         return "Debit";
      }
      return null;
   }

   private String getTransactionCategoryId(Rtxn rtxn) {
      return getTransactionCategoryId(getTransactionCategory(rtxn), getTransactionType(rtxn));
   }

   private String getTransactionCategoryId(String rtxnTypeCode, String debitCreditType) {
      if ("CWTH".equals(rtxnTypeCode)) {
         return "4";
      }
      if ("BPMT".equals(rtxnTypeCode)) {
         return "5";
      }
      if ("Credit".equals(debitCreditType)) {
         return "2";
      }
      if ("Debit".equals(debitCreditType)) {
         return "3";
      }
      return "1";
   }

   private String getChequeNumber(Rtxn rtxn) {
      return rtxn == null || rtxn.getCheckNumber() == null ? null : String.valueOf(rtxn.getCheckNumber());
   }

   private String getInstrumentId(Rtxn rtxn) {
      return getChequeNumber(rtxn);
   }

   private BigDecimal getExchangeAmount(ExchTxn exchangeTransaction) {
      return exchangeTransaction == null ? null : toAbsBigDecimal(exchangeTransaction.getOtherAmount());
   }

   private String getExchangeRate(ExchTxn exchangeTransaction) {
      return exchangeTransaction == null || exchangeTransaction.getExchangeRate() == null
          ? null
          : exchangeTransaction.getExchangeRate().toPlainString();
   }

   private String getAccountNumber(Rtxn rtxn) {
      return rtxn == null || rtxn.getAccountNumber() == null ? null : String.valueOf(rtxn.getAccountNumber());
   }

   private String getAccountHolderName(FiservRequest depRequest) {
      return depRequest == null || depRequest.accountInfo() == null ? null : depRequest.accountInfo().accountHolderName();
   }

   private boolean isLoanAccount(FiservRequest depRequest) {
      return depRequest != null &&
          depRequest.accountInfo() != null &&
          "true".equals(depRequest.accountInfo().isLoanAccount());
   }

   private BigDecimal getPrincipalAmount(Transaction loanTransaction) {
      return getLoanBalanceAmount(loanTransaction, "Note Balance");
   }

   private BigDecimal getInterestChargeAmount(Transaction loanTransaction) {
      return getLoanBalanceAmount(loanTransaction, "Note Interest");
   }

   private BigDecimal getLoanBalanceAmount(Transaction transaction, String balanceTypeDescription) {
      if (transaction == null || transaction.getBalanceTypes() == null) {
         return null;
      }
      for (TransactionBalanceType balanceType : transaction.getBalanceTypes().getTransactionBalanceType()) {
         if (balanceType != null &&
             balanceType.getAmount() != null &&
             balanceType.getBalanceTypeDescription() != null &&
             balanceType.getBalanceTypeDescription().contains(balanceTypeDescription)) {
            return toAbsBigDecimal(balanceType.getAmount());
         }
      }
      return null;
   }

   private String getTransactionDescription(
       Rtxn rtxn,
       BillPayment billPayment,
       ExchTxn exchangeTransaction
   ) {
      BigDecimal exchangeAmount = getExchangeAmount(exchangeTransaction);
      String exchangeRate = getExchangeRate(exchangeTransaction);
      StringBuilder description = new StringBuilder();
      if (rtxn != null && isNotBlank(rtxn.getRtxnTypeDescription())) {
         description.append(rtxn.getRtxnTypeDescription());
      }
      appendDescriptionPart(description, rtxn == null ? null : rtxn.getInternalRtxnDescription());
      appendDescriptionPart(description, billPayment == null ? null : billPayment.getVendorName());
      if (exchangeAmount != null) {
         appendDescriptionPart(description, "Exchange Amount: $" + exchangeAmount.toPlainString());
      }
      if (isNotBlank(exchangeRate)) {
         appendDescriptionPart(description, "Exchange Rate: " + exchangeRate);
      }
      return description.isEmpty() ? null : description.toString();
   }

   private void appendDescriptionPart(StringBuilder description, String value) {
      if (isNotBlank(value)) {
         if (!description.isEmpty()) {
            description.append(' ');
         }
         description.append(value);
      }
   }

   private ExchTxn getExchangeTransaction(Rtxn rtxn) {
      if (rtxn == null || rtxn.getExchTxnGrp() == null || rtxn.getExchTxnGrp().getExchTxn().isEmpty()) {
         return null;
      }
      return rtxn.getExchTxnGrp().getExchTxn().get(0);
   }

   private BigDecimal toAbsBigDecimal(Double value) {
      return value == null ? null : BigDecimal.valueOf(Math.abs(value));
   }

   private BigDecimal toBigDecimal(Double value) {
      return value == null ? null : BigDecimal.valueOf(value);
   }

   private String toDateString(XMLGregorianCalendar value) {
      return value == null ? null : value.toGregorianCalendar().toZonedDateTime().toLocalDate().toString();
   }

   private boolean isNotBlank(String value) {
      return value != null && !value.isBlank();
   }
}
