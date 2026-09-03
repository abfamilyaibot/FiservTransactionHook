package com.dep.integration.transaction.hook.fiserv;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import com.dep.integration.transaction.hook.fiserv.dto.FiservResponse;
import com.dep.integration.transaction.hook.fiserv.dto.common.CasaTransactionDtl;
import com.dep.integration.transaction.hook.fiserv.dto.common.CriteriaDetails.SortingOrder;
import com.dep.integration.transaction.hook.fiserv.dto.common.EndpointAttributes;

import org.junit.jupiter.api.Test;

class FiservTransactionProcessorTest {
    // chequing account with C, D, BILL under 5818090505689440 RETAILDEPSAAS1
    private static final String DEFAULT_START_DATE = "2026-08-01";
    private static final String DEFAULT_END_DATE = "2026-09-02";
    private static final String DEFAULT_ACCOUNT_NUMBER = "660500580845";

    // loan account under 5818090505689440 RETAILDEPSAAS1
    private static final String TEST2_START_DATE = "2026-01-01";
    private static final String TEST2_END_DATE = "2026-09-02";
    private static final String TEST2_ACCOUNT_NUMBER = "660500581785";

    // wih cheque image on Mar 13, 2026 under 5818090505689457 RETAILDEPSAAS3
    private static final String TEST3_START_DATE = "2026-03-01";
    private static final String TEST3_END_DATE = "2026-03-30";
    private static final String TEST3_ACCOUNT_NUMBER = "100000269944";

    // wih exchange rate 5818090505689457 RETAILDEPSAAS3
    private static final String TEST4_START_DATE = "2026-08-28";
    private static final String TEST4_END_DATE = "2026-08-28";
    private static final String TEST4_ACCOUNT_NUMBER = "660500581009";

    @Test
    void noFilteringASC() throws Exception {
        String responseJson = processAndPrint(requestJson( DEFAULT_START_DATE, DEFAULT_END_DATE, DEFAULT_ACCOUNT_NUMBER, "ASC", null, null, null));
        FiservResponse response = FiservApiClient.JSON_OBJECT_MAPPER.readValue(responseJson, FiservResponse.class);
        assertTransactionDateOrder(response, SortingOrder.ASC);
        assertTenantId(response, "CORE_FISERVDNA");
        assertTransactionType(response, Arrays.asList("Credit", "Debit"));
        assertTransType(response, Arrays.asList("Credit", "Debit"));
        assertDebitCreditFlag(response, Arrays.asList("C", "D"));
        assertTransactionCategoryId(response, Arrays.asList("4", "5", "2", "3"));
        assertAccountHolderName(response, "Joel User");
        assertAccountNumber(response, DEFAULT_ACCOUNT_NUMBER);
        assertTransactionCurrencyExists(response);
        assertBalanceExists(response);
        assertTransactionDateExists(response);
        assertValueDateExists(response);
        assertTransactionAmountExists(response);
        assertTransactionDescriptionExists(response);
        assertTransactionReferenceExists(response);
    }

    @Test
    void noFilteringDESC() throws Exception {
        String responseJson = processAndPrint(requestJson( DEFAULT_START_DATE, DEFAULT_END_DATE, DEFAULT_ACCOUNT_NUMBER, "DESC", null, null, null));
        FiservResponse response = FiservApiClient.JSON_OBJECT_MAPPER.readValue(responseJson, FiservResponse.class);
        assertTransactionDateOrder(response, SortingOrder.DESC);
    }

    @Test
    void filterByCreditFlag() throws Exception {
        String responseJson = processAndPrint(requestJson( DEFAULT_START_DATE, DEFAULT_END_DATE, DEFAULT_ACCOUNT_NUMBER, "DESC", "C", null, null));
        FiservResponse response = FiservApiClient.JSON_OBJECT_MAPPER.readValue(responseJson, FiservResponse.class);
        assertTransactionDateOrder(response, SortingOrder.DESC);
        assertDebitCreditFlag(response, Arrays.asList("C"));
        // 'CWTH' -> 4, 'BPMT' -> 5; else 'Credit' -> 2, 'Debit' -> 3
        assertTransactionType(response, Arrays.asList("Credit"));
        assertTransType(response, Arrays.asList("Credit"));
        assertTransactionCategoryId(response, Arrays.asList("4", "2")); // CHEQUE, C

    }

    @Test
    void filterByDebitFlag() throws Exception {
        String responseJson = processAndPrint(requestJson( DEFAULT_START_DATE, DEFAULT_END_DATE, DEFAULT_ACCOUNT_NUMBER, "DESC", "D", null, null));
        FiservResponse response = FiservApiClient.JSON_OBJECT_MAPPER.readValue(responseJson, FiservResponse.class);
        assertTransactionDateOrder(response, SortingOrder.DESC);
        assertDebitCreditFlag(response, Arrays.asList("D"));
        // 'CWTH' -> 4, 'BPMT' -> 5; else 'Credit' -> 2, 'Debit' -> 3
        assertTransactionType(response, Arrays.asList("Debit"));
        assertTransType(response, Arrays.asList("Debit"));
        assertTransactionCategoryId(response, Arrays.asList("4", "5", "3")); // CHEQUE, BILL, D
    }

    @Test
    void filterByBill() throws Exception {
        String responseJson = processAndPrint(requestJson( DEFAULT_START_DATE, DEFAULT_END_DATE, DEFAULT_ACCOUNT_NUMBER, "DESC", "Bill", null, null));
        FiservResponse response = FiservApiClient.JSON_OBJECT_MAPPER.readValue(responseJson, FiservResponse.class);
        assertTransactionDateOrder(response, SortingOrder.DESC);
        assertTransactionCategory(response, "BILL_PAYMENT");
        assertBillPaymentConfirmationNumberExists(response);
        assertMerchantIdExists(response);
        // 'CWTH' -> 4, 'BPMT' -> 5; else 'Credit' -> 2, 'Debit' -> 3
        assertTransactionCategoryId(response, Arrays.asList("5"));
    }

    @Test
    void filterByCheque() throws Exception {
        String responseJson = processAndPrint(requestJson( TEST3_START_DATE, TEST3_END_DATE, TEST3_ACCOUNT_NUMBER, "DESC", "Cheque", null, null));
        FiservResponse response = FiservApiClient.JSON_OBJECT_MAPPER.readValue(responseJson, FiservResponse.class);
        assertTransactionDateOrder(response, SortingOrder.DESC);
        assertChequeNumberExists(response);
        assertInstrumentIdExists(response);
        assertTransactionCategory(response, "CHEQUE");
        // 'CWTH' -> 4, 'BPMT' -> 5; else 'Credit' -> 2, 'Debit' -> 3
        assertTransactionCategoryId(response, Arrays.asList("4"));
    }

    @Test
    void searchByAmount() throws Exception {
        String responseJson = processAndPrint(requestJson( DEFAULT_START_DATE, DEFAULT_END_DATE, DEFAULT_ACCOUNT_NUMBER, "DESC", null, "AMOUNT", "105"));
        FiservResponse response = FiservApiClient.JSON_OBJECT_MAPPER.readValue(responseJson, FiservResponse.class);
        assertTransactionDateOrder(response, SortingOrder.DESC);
        assertTransactionAmount(response, "1");
    }

    @Test
    void searchByDescription() throws Exception {
        String responseJson = processAndPrint(requestJson( DEFAULT_START_DATE, DEFAULT_END_DATE, DEFAULT_ACCOUNT_NUMBER, "DESC", null, "DESCRIPTION", "memo"));
        FiservResponse response = FiservApiClient.JSON_OBJECT_MAPPER.readValue(responseJson, FiservResponse.class);
        assertTransactionDateOrder(response, SortingOrder.DESC);
        assertTransactionDescriptionContains(response, "Stop Payment");
    }

    @Test
    void searchByConfirmationNumber() throws Exception {
        String responseJson = processAndPrint(requestJson( DEFAULT_START_DATE, DEFAULT_END_DATE, DEFAULT_ACCOUNT_NUMBER, "DESC", null, "CONFIRMATION_NUMBER", "1740662070"));
        FiservResponse response = FiservApiClient.JSON_OBJECT_MAPPER.readValue(responseJson, FiservResponse.class);
        assertTransactionDateOrder(response, SortingOrder.DESC);
        assertConfirmationNumber(response, "8053275");
        assertTransactionCategory(response, "BILL_PAYMENT");
        // 'CWTH' -> 4, 'BPMT' -> 5; else 'Credit' -> 2, 'Debit' -> 3
        assertTransactionCategoryId(response, Arrays.asList("5")); // BILL
    }

  @Test
    void searchByChequeNumber() throws Exception {
        String responseJson = processAndPrint(requestJson( TEST3_START_DATE, TEST3_END_DATE, TEST3_ACCOUNT_NUMBER, "DESC", null, "CHEQUE_NUMBER", "123"));
        FiservResponse response = FiservApiClient.JSON_OBJECT_MAPPER.readValue(responseJson, FiservResponse.class);
        assertTransactionDateOrder(response, SortingOrder.DESC);
        assertChequeNumber(response, "100");
        assertTransactionCategory(response, "CHEQUE");
        // 'CWTH' -> 4, 'BPMT' -> 5; else 'Credit' -> 2, 'Debit' -> 3
        assertTransactionCategoryId(response, Arrays.asList("4"));
    }

    @Test
    void searchByAmountAndFilterByCreditFlag() throws Exception {
        String responseJson = processAndPrint(requestJson( DEFAULT_START_DATE, DEFAULT_END_DATE, DEFAULT_ACCOUNT_NUMBER, "DESC", "C", "AMOUNT", "105"));
        FiservResponse response = FiservApiClient.JSON_OBJECT_MAPPER.readValue(responseJson, FiservResponse.class);
        assertTransactionDateOrder(response, SortingOrder.DESC);
        assertTransactionAmount(response, "1");
        assertDebitCreditFlag(response, Arrays.asList("C"));
    }

    @Test
    void loanAccount() throws Exception {
        String responseJson = processAndPrint(requestJson( TEST2_START_DATE, TEST2_END_DATE, TEST2_ACCOUNT_NUMBER, "ASC", null, null, null));
        FiservResponse response = FiservApiClient.JSON_OBJECT_MAPPER.readValue(responseJson, FiservResponse.class);
        assertContainsPrincipalAmountOrInterestChargeAmount(response);
    }

    @Test
    void exchangeRate() throws Exception {
        String responseJson = processAndPrint(requestJson( TEST4_START_DATE, TEST4_END_DATE, TEST4_ACCOUNT_NUMBER, "ASC", null, null, null));
        FiservResponse response = FiservApiClient.JSON_OBJECT_MAPPER.readValue(responseJson, FiservResponse.class);
        assertExchangeRateExists(response);
        assertExchangeAmountExists(response);
        assertTransactionDescriptionContainsExchangeInfo(response);
    }

    private String processAndPrint(String requestJson) {
        TransactionProcessor processor = new FiservTransactionProcessor(true);

        long startedAtNanos = System.nanoTime();
        String response = processor.process(requestJson, getEndpointAttributes());
        long elapsedNanos = System.nanoTime() - startedAtNanos;

        System.out.println("Process elapsed time: " + elapsedMillis(elapsedNanos) + " ms");
        System.out.println("Request: " + requestJson);
        System.out.println("Response: " + response);
        return response;
    }

    private List<CasaTransactionDtl> assertTransactions(FiservResponse response) {
        assertNotNull(response.casaTransactionDtlsResponse(), "casaTransactionDtlsResponse should be present");

        List<CasaTransactionDtl> transactions = response.casaTransactionDtlsResponse().casatransactiondtls();
        assertNotNull(transactions, "casatransactiondtls should be present");
        assertFalse(transactions.isEmpty(), "casatransactiondtls should not be empty");
        return transactions;
    }

    private <T> void assertEveryEquals(
        FiservResponse response,
        Function<CasaTransactionDtl, T> valueExtractor,
        T expectedValue,
        String fieldName
    ) {
        for (CasaTransactionDtl transaction : assertTransactions(response)) {
            assertEquals(
                expectedValue,
                valueExtractor.apply(transaction),
                "casatransactiondtls should only contain " + fieldName + " " + expectedValue
            );
        }
    }

    private <T> void assertEveryIn(
        FiservResponse response,
        Function<CasaTransactionDtl, T> valueExtractor,
        List<T> expectedValues,
        String fieldName
    ) {
        for (CasaTransactionDtl transaction : assertTransactions(response)) {
            assertTrue(
                expectedValues.contains(valueExtractor.apply(transaction)),
                "casatransactiondtls should only contain " + fieldName + " in " + expectedValues
            );
        }
    }

    private void assertEveryExists(
        FiservResponse response,
        Function<CasaTransactionDtl, ?> valueExtractor,
        String fieldName
    ) {
        for (CasaTransactionDtl transaction : assertTransactions(response)) {
            assertNotNull(
                valueExtractor.apply(transaction),
                "casatransactiondtls should contain " + fieldName
            );
        }
    }

    private void assertTransactionDateOrder(FiservResponse response, SortingOrder sortingOrder) {
        List<CasaTransactionDtl> transactions = assertTransactions(response);

        for (int i = 1; i < transactions.size(); i++) {
            LocalDate previousDate = LocalDate.parse(transactions.get(i - 1).transactionDate());
            LocalDate currentDate = LocalDate.parse(transactions.get(i).transactionDate());
            if (sortingOrder == SortingOrder.ASC) {
                assertFalse(
                    currentDate.isBefore(previousDate),
                    "casatransactiondtls should be sorted by transactionDate ascending"
                );
            } else {
                assertFalse(
                    currentDate.isAfter(previousDate),
                    "casatransactiondtls should be sorted by transactionDate descending"
                );
            }
        }
    }

    private void assertContainsPrincipalAmountOrInterestChargeAmount(FiservResponse response) {
        for (CasaTransactionDtl transaction : assertTransactions(response)) {
            assertTrue(
                transaction.principalAmount() != null || transaction.interestChargeAmount() != null,
                "interestChargeAmount or principalAmount should be present"
            );
        }
    }

    private void assertDebitCreditFlag(FiservResponse response, List<String> expectedDebitCreditFlags) {
        assertEveryIn(response, CasaTransactionDtl::debitCreditFlag, expectedDebitCreditFlags, "debitCreditFlag");
    }

    private void assertTenantId(FiservResponse response, String expectedTenantId) {
        assertEveryEquals(response, CasaTransactionDtl::tenantId, expectedTenantId, "tenantId");
    }

    private void assertAccountHolderName(FiservResponse response, String expectedAccountHolderName) {
        assertEveryEquals(response, CasaTransactionDtl::accountHolderName, expectedAccountHolderName, "accountHolderName");
    }

    private void assertAccountNumber(FiservResponse response, String expectedAccountNumber) {
        assertEveryEquals(response, CasaTransactionDtl::accountNumber, expectedAccountNumber, "accountNumber");
    }

    private void assertTransactionCategory(FiservResponse response, String expectedTransactionCategory) {
        assertEveryEquals(response, CasaTransactionDtl::transactionCategory, expectedTransactionCategory, "transactionCategory");
    }

    private void assertTransactionCategoryId(FiservResponse response, List<String> expectedTranactionCategoryIds) {
        assertEveryIn(response, CasaTransactionDtl::transactionCategoryId, expectedTranactionCategoryIds, "transactionCategoryId");
    }

    private void assertTransactionType(FiservResponse response, List<String> expectedTransactionTypes) {
        assertEveryIn(response, CasaTransactionDtl::transactionType, expectedTransactionTypes, "transactionType");
    }

    private void assertTransType(FiservResponse response, List<String> expectedTransTypes) {
        assertEveryIn(response, CasaTransactionDtl::transType, expectedTransTypes, "transType");
    }

    private void assertBillPaymentConfirmationNumberExists(FiservResponse response) {
        assertEveryExists(response, CasaTransactionDtl::confirmationNumber, "confirmationNumber for bill payment transactions");
    }

    private void assertMerchantIdExists(FiservResponse response) {
        assertEveryExists(response, CasaTransactionDtl::merchantId, "merchantId for bill payment transactions");
    }

    private void assertTransactionAmount(FiservResponse response, String expectedTransactionAmount) {
        BigDecimal expectedAmount = new BigDecimal(expectedTransactionAmount);
        for (CasaTransactionDtl transaction : assertTransactions(response)) {
            assertEquals(
                0,
                expectedAmount.compareTo(transaction.transactionAmount()),
                "casatransactiondtls should only contain transactionAmount " + expectedTransactionAmount
            );
        }
    }

    private void assertTransactionAmountExists(FiservResponse response) {
        assertEveryExists(response, CasaTransactionDtl::transactionAmount, "transactionAmount");
    }

    private void assertTransactionDescriptionContains(FiservResponse response, String expectedDescription) {
        String normalizedExpectedDescription = expectedDescription.toLowerCase();
        for (CasaTransactionDtl transaction : assertTransactions(response)) {
            assertNotNull(transaction.transactionDescription(), "transactionDescription should be present");
            assertFalse(
                !transaction.transactionDescription().toLowerCase().contains(normalizedExpectedDescription),
                "casatransactiondtls should only contain transactionDescription with " + expectedDescription
            );
        }
    }

    private void assertConfirmationNumber(FiservResponse response, String expectedConfirmationNumber) {
        assertEveryEquals(response, CasaTransactionDtl::confirmationNumber, expectedConfirmationNumber, "confirmationNumber");
    }

    private void assertChequeNumber(FiservResponse response, String expectedChequeNumber) {
        assertEveryEquals(response, CasaTransactionDtl::chequeNumber, expectedChequeNumber, "chequeNumber");
    }

    private void assertChequeNumberExists(FiservResponse response) {
        assertEveryExists(response, CasaTransactionDtl::chequeNumber, "chequeNumber for cheque transactions");
    }

    private void assertInstrumentIdExists(FiservResponse response) {
        assertEveryExists(response, CasaTransactionDtl::instrumentId, "instrumentId for cheque transactions");
    }

    private void assertTransactionCurrencyExists(FiservResponse response) {
        assertEveryExists(response, CasaTransactionDtl::transactionCurrency, "transactionCurrency");
    }

    private void assertBalanceExists(FiservResponse response) {
        assertEveryExists(response, CasaTransactionDtl::balance, "balance");
    }

    private void assertTransactionDateExists(FiservResponse response) {
        assertEveryExists(response, CasaTransactionDtl::transactionDate, "transactionDate");
    }

    private void assertValueDateExists(FiservResponse response) {
        assertEveryExists(response, CasaTransactionDtl::valueDate, "valueDate");
    }

    private void assertExchangeRateExists(FiservResponse response) {
        assertEveryExists(response, CasaTransactionDtl::exchangeRate, "exchangeRate");
    }

    private void assertExchangeAmountExists(FiservResponse response) {
        assertEveryExists(response, CasaTransactionDtl::exchangeAmount, "exchangeAmount");
    }

    private void assertTransactionDescriptionContainsExchangeInfo(FiservResponse response) {
        for (CasaTransactionDtl transaction : assertTransactions(response)) {
            assertNotNull(transaction.transactionDescription(), "transactionDescription should be present");
            assertTrue(
                transaction.transactionDescription().contains(" Exchange Amount: $"),
                "transactionDescription should contain exchange amount"
            );
            assertTrue(
                transaction.transactionDescription().contains(" Exchange Rate: "),
                "transactionDescription should contain exchange rate"
            );
        }
    }

    private void assertTransactionDescriptionExists(FiservResponse response) {
        assertEveryExists(response, CasaTransactionDtl::transactionDescription, "transactionDescription");
    }

    private void assertTransactionReferenceExists(FiservResponse response) {
        assertEveryExists(response, CasaTransactionDtl::transactionReference, "transactionReference");
    }

    private EndpointAttributes getEndpointAttributes() {
        return new EndpointAttributes(
            60000,
            60000,
            null,
            null,
            null,
            null, null, null,
            "http://10.211.1.169:8443/Extensions/DNA.CoreApiService/CoreApiService.svc/Soap11"  ); 
    }


    private String requestJson(
        String startDate,
        String endDate,
        String accountNumber,
        String sortingOrder,
        String filterType,
        String searchType,
        String searchValue
    ) {
        String filterTypeLine = filterType == null ? "" : """
                    "filterType": "%s",
        """.formatted(filterType);
        String searchTypeLine = searchType == null ? "" : """
                    "searchType": "%s",
        """.formatted(searchType);
        String searchValueLine = searchValue == null ? "" : """
                    "searchValue": "%s",
        """.formatted(searchValue);
        // TODO: update password
        return """
            {
                "cbsContext": {
                    "applID": "56233560-146F-413C-98EC-CBF6338B52DA", 
                    "networkNodeName": "INTELLECTDEP", 
                    "password": "&lt;WhoIsResponse xmlns:xsd=&quot;http://www.w3.org/2001/XMLSchema&quot; xmlns:xsi=&quot;http://www.w3.org/2001/XMLSchema-instance&quot; MessageDateTime=&quot;2026-08-25T13:48:44.2802527-07:00&quot; TrackingId=&quot;b44edc59-6b6f-421c-a582-434d9d033377&quot; Successful=&quot;true&quot; ExceptionCode=&quot;None&quot; Signature=&quot;Rqp4p+XnD7CRXYGgUPTncqv4UIG9T4gTPjhb8Ab1gL/LLjq+SFCGyYQyEPVOhkJ6co411mSMw0M25//MKDNxGtIjoR8q18XOmzO6kE6pfKX4nRgAets7Yasj1VCLkJzyZwbA4G2UNnCv/AoM8jz0BIINts0ZnQVgzuK0DEUvi+mYOHCnHG0SrAxsaTipJas3LQAWJBQ/rp6emmO0o4FWUgbLUXWmXt990cWbD2WiCFgFlfPY7jjVy31QK/c11AtPP+5lSovTEEEWPKvSxSMkDL1DmxV6l+RwpawrWkGHnKi7Ys6yRS9W400Jcc20n7qEOOw+fTjySX2WO1pLEf5TPa1TdxCBRLqkxL7pnv+SrLQSTP0Flp9PHnukqRGAWhdw0KBiGqHQGUEoDOlxzRHH+roHfZndleSWYkXqth/pi6wYuCY8J/fV/OWxbynn3yiTianZGlTwrvd9ZXg+2r7aOsffecaYZuYA9vZ0v8wacEfZFe3/QJ27/0izT4WqNGH0hE0IoWi4A2AihMbQ4JqB2OJYIXS/L0UNQ3FQXtAWSut6gtZmeoWRMIyGpJ7he04b5QmGAkV2FhKcmBD3Ywemdbum9+ol/tD9EMzH9zBLGSF5fXGAx6ToIRdRegEd7P4k3AIsOAIVputa2b2C4h4r8pYJdINoGPTBvZJKld4QvtA=&quot;&gt;&lt;SafUserNbr&gt;1674&lt;/SafUserNbr&gt;&lt;SafUserName&gt;INTELLECTDEP&lt;/SafUserName&gt;&lt;FirstName&gt;Intellect&lt;/FirstName&gt;&lt;LastName&gt;DEP&lt;/LastName&gt;&lt;ExpirationDate&gt;2026-08-26T13:48:33.2730922-07:00&lt;/ExpirationDate&gt;&lt;AuthenticatedProducts&gt;&lt;ProdNbr&gt;3&lt;/ProdNbr&gt;&lt;ProdPersId&gt;1532997&lt;/ProdPersId&gt;&lt;/AuthenticatedProducts&gt;&lt;AuthenticatedProducts&gt;&lt;ProdNbr&gt;1&lt;/ProdNbr&gt;&lt;ProdPersId&gt;1674&lt;/ProdPersId&gt;&lt;/AuthenticatedProducts&gt;&lt;/WhoIsResponse&gt;" 
                },
                "depTenantId": "CORE_FISERVDNA",
                "isLoanAccount": "false",
                "accountHolderName": "Joel User",
                "criteriaDetails": {
                    "startDate": "%s",
                    "endDate": "%s",
                    "accountNumber": "%s",
        %s%s%s            "sortingOrder": "%s"
                }
            }
        """.formatted(
            startDate,
            endDate,
            accountNumber,
            filterTypeLine,
            searchTypeLine,
            searchValueLine,
            sortingOrder
        );
    }

    private double elapsedMillis(long elapsedNanos) {
        return elapsedNanos / 1_000_000.0;
    }
}
