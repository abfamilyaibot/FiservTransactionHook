package com.dep.integration.billpayment.hook.fiserv;

//import com.dep.integration.billpayment.hook.fiserv.dto.common.EndpointAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class FiservMultiBillPaymentProcessorTest {
    private static final String[] VENDOR_ACCOUNT_NUMBERS = {"11111", "22222", "33333", "44444", "55555"};

    @Disabled
    @Test
    void processReturnsMultiBillResponseJson() throws Exception {
        // MultiBillPaymentProcessor processor = new FiservMultiBillPaymentProcessor(true);
        // String requestJson = requestJson();

        // long startedAtNanos = System.nanoTime();
        // String response = processor.process(requestJson, getEndpointAttributes());
        // long elapsedNanos = System.nanoTime() - startedAtNanos;

        // System.out.println("Process elapsed time: " + elapsedMillis(elapsedNanos) + " ms");
        // System.out.println("Request: " + requestJson);
        // System.out.println("Response: " + response);

    }

    @Test
    void asyncProcessReturnsMultiBillResponseJson() throws Exception {
        // MultiBillPaymentProcessor processor = new FiservMultiBillPaymentProcessor(true);
        // String requestJson = requestJson();

        // long startedAtNanos = System.nanoTime();
        // String response = processor.asyncProcess(requestJson, getEndpointAttributes());
        // long elapsedNanos = System.nanoTime() - startedAtNanos;

        // System.out.println("Async process elapsed time: " + elapsedMillis(elapsedNanos) + " ms");
        // System.out.println("Async request: " + requestJson);
        // System.out.println("Async response: " + response);

    }

    // private EndpointAttributes getEndpointAttributes() {
    //     return new EndpointAttributes(
    //         60000,
    //         60000,
    //         null,
    //         null,
    //         null,
    //         null, null, null,
    //         "http://10.211.1.169:8443/Extensions/DNA.CoreApiService/CoreApiService.svc/Soap11"  ); 
    // }

    private String requestJson() {
        List<String> scheduleTypes = randomScheduleTypes(5);
        String multiBillRequestDetails = IntStream.range(0, 5)
            .mapToObj(index -> multiBillRequestDetailJson(
                randomPaymentAmount(),
                randomVendorAccountNumber(),
                scheduleTypes.get(index)
            ))
            .collect(Collectors.joining(",\n"));

        return """
            {
                "cbsContext": {
                    "userId": "17184067", 
                    "applID": "56233560-146F-413C-98EC-CBF6338B52DA", 
                    "networkNodeName": "INTELLECTDEP", 
                    "password": "&lt;WhoIsResponse xmlns:xsd=&quot;http://www.w3.org/2001/XMLSchema&quot; xmlns:xsi=&quot;http://www.w3.org/2001/XMLSchema-instance&quot; MessageDateTime=&quot;2026-08-25T13:48:44.2802527-07:00&quot; TrackingId=&quot;b44edc59-6b6f-421c-a582-434d9d033377&quot; Successful=&quot;true&quot; ExceptionCode=&quot;None&quot; Signature=&quot;Rqp4p+XnD7CRXYGgUPTncqv4UIG9T4gTPjhb8Ab1gL/LLjq+SFCGyYQyEPVOhkJ6co411mSMw0M25//MKDNxGtIjoR8q18XOmzO6kE6pfKX4nRgAets7Yasj1VCLkJzyZwbA4G2UNnCv/AoM8jz0BIINts0ZnQVgzuK0DEUvi+mYOHCnHG0SrAxsaTipJas3LQAWJBQ/rp6emmO0o4FWUgbLUXWmXt990cWbD2WiCFgFlfPY7jjVy31QK/c11AtPP+5lSovTEEEWPKvSxSMkDL1DmxV6l+RwpawrWkGHnKi7Ys6yRS9W400Jcc20n7qEOOw+fTjySX2WO1pLEf5TPa1TdxCBRLqkxL7pnv+SrLQSTP0Flp9PHnukqRGAWhdw0KBiGqHQGUEoDOlxzRHH+roHfZndleSWYkXqth/pi6wYuCY8J/fV/OWxbynn3yiTianZGlTwrvd9ZXg+2r7aOsffecaYZuYA9vZ0v8wacEfZFe3/QJ27/0izT4WqNGH0hE0IoWi4A2AihMbQ4JqB2OJYIXS/L0UNQ3FQXtAWSut6gtZmeoWRMIyGpJ7he04b5QmGAkV2FhKcmBD3Ywemdbum9+ol/tD9EMzH9zBLGSF5fXGAx6ToIRdRegEd7P4k3AIsOAIVputa2b2C4h4r8pYJdINoGPTBvZJKld4QvtA=&quot;&gt;&lt;SafUserNbr&gt;1674&lt;/SafUserNbr&gt;&lt;SafUserName&gt;INTELLECTDEP&lt;/SafUserName&gt;&lt;FirstName&gt;Intellect&lt;/FirstName&gt;&lt;LastName&gt;DEP&lt;/LastName&gt;&lt;ExpirationDate&gt;2026-08-26T13:48:33.2730922-07:00&lt;/ExpirationDate&gt;&lt;AuthenticatedProducts&gt;&lt;ProdNbr&gt;3&lt;/ProdNbr&gt;&lt;ProdPersId&gt;1532997&lt;/ProdPersId&gt;&lt;/AuthenticatedProducts&gt;&lt;AuthenticatedProducts&gt;&lt;ProdNbr&gt;1&lt;/ProdNbr&gt;&lt;ProdPersId&gt;1674&lt;/ProdPersId&gt;&lt;/AuthenticatedProducts&gt;&lt;/WhoIsResponse&gt;" 
                },
                "multiBillRequest": {
                    "debitAccount": "660500580985", 
                    "multiBillRequestDetails": [
                        %s
                    ]
                }
            }
        """.formatted(multiBillRequestDetails);
    }

    private String multiBillRequestDetailJson(BigDecimal paymentAmount, String vendorAccountNumber, String scheduleType) {
        LocalDate paymentDate = LocalDate.now().plusDays(1);
        LocalDate paymentEndDate = paymentDate.plusYears(1);
        boolean isScheduled = "2".equals(scheduleType);
        boolean isRecurring = "3".equals(scheduleType);

        return """
            {
                "paymentAmount": %s,
                "currency": "CAD",
                "scheduleType": "%s",
                "paymentDate": %s,
                "paymentEndDate": %s,
                "vendorAccountNumber": "%s",
                "vendorId": "1",
                "cbsFrequencyType": %s
            }
            """.formatted(
                paymentAmount,
                scheduleType,
                (isScheduled || isRecurring) ? jsonString(paymentDate) : "null",
                isRecurring ? jsonString(paymentEndDate) : "null",
                vendorAccountNumber,
                isRecurring ? jsonString("MNTH") : "null"
            );
    }

    private BigDecimal randomPaymentAmount() {
        return BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(1, 100), 2)
            .setScale(2, RoundingMode.UNNECESSARY);
    }

    private String randomVendorAccountNumber() {
        return VENDOR_ACCOUNT_NUMBERS[ThreadLocalRandom.current().nextInt(VENDOR_ACCOUNT_NUMBERS.length)];
    }

    private List<String> randomScheduleTypes(int count) {
        List<String> scheduleTypes = new ArrayList<>(List.of("1", "2", "3"));
        while (scheduleTypes.size() < count) {
            scheduleTypes.add(String.valueOf(ThreadLocalRandom.current().nextInt(1, 4)));
        }
        Collections.shuffle(scheduleTypes);
        return scheduleTypes;
    }

    private String jsonString(Object value) {
        return "\"" + value + "\"";
    }

    private double elapsedMillis(long elapsedNanos) {
        return elapsedNanos / 1_000_000.0;
    }
}
