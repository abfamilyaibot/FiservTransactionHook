package com.dep.integration.billpayment.hook.fiserv;

import com.dep.integration.billpayment.hook.fiserv.dto.common.EndpointAttributes;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class CgiMultiBillPaymentProcessorTest {
    private static final String[] VENDOR_ACCOUNT_NUMBERS = {"111111", "222222", "26680", "7788", "99999"};

    @Test
    void processReturnsMultiBillResponseJson() throws Exception {
        MultiBillPaymentProcessor processor = new CgiMultiBillPaymentProcessor(true);
        String requestJson = requestJson();

        long startedAtNanos = System.nanoTime();
        String response = processor.process(requestJson, getEndpointAttributes());
        long elapsedNanos = System.nanoTime() - startedAtNanos;

        System.out.println("Process elapsed time: " + elapsedMillis(elapsedNanos) + " ms");
        System.out.println("Request: " + requestJson);
        System.out.println("Response: " + response);

    }

    @Test
    void asyncProcessReturnsMultiBillResponseJson() throws Exception {
        MultiBillPaymentProcessor processor = new CgiMultiBillPaymentProcessor(true);
        String requestJson = requestJson();

        long startedAtNanos = System.nanoTime();
        String response = processor.asyncProcess(requestJson, getEndpointAttributes());
        long elapsedNanos = System.nanoTime() - startedAtNanos;

        System.out.println("Async process elapsed time: " + elapsedMillis(elapsedNanos) + " ms");
        System.out.println("Async request: " + requestJson);
        System.out.println("Async response: " + response);

    }

    private EndpointAttributes getEndpointAttributes() {
        return new EndpointAttributes(
            60000,
            60000,
                "/home/betty-leung/Development/K3_AMERICAS_CANADA_fiserv/hook/src/main/resources/dep-cgidna-nonprod-client.jks",
            "admin123",
            "JKS",
            null, null, null,
        "https://v2.fte.integrationservices.celero.ca/core-proxy/v2.0/fiserv-dna//Extensions/DNA.CoreApiService/CoreApiService.svc/Soap11"
        );
    }

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
                    "userId": "213933", 
                    "applID": "4897EFB4-0CEA-4F36-83C3-CB8C365583DF", 
                    "networkNodeName": "INTELINT2019", 
                    "password": "&lt;WhoIsResponse xmlns:xsd=&quot;http://www.w3.org/2001/XMLSchema&quot; xmlns:xsi=&quot;http://www.w3.org/2001/XMLSchema-instance&quot; MessageDateTime=&quot;2026-07-20T15:56:01.4284714-05:00&quot; TrackingId=&quot;b44edc59-6b6f-421c-a582-434d9d033377&quot; Successful=&quot;true&quot; ExceptionCode=&quot;None&quot; Signature=&quot;dTYd6nfSFL7M2r8jgSHA5uds5+EF+YmJZ4sP/ILPiUpW8HxEg4HB/mizOn/vSaECvx0sziH2Zq8BIk4WobBoMYwo1Eyj1VWqfP93q90VhE6IbGK/T7v+7xNaJ35AmnpJvi4MwjeiD1hNdpn2HgUhz8M7eqoi3n0Bv1fcLj6p2Y32G9ooJN4IUs1T8QoMWxIQOMSJL2fnJ1jNg6AWcV8vsIubUk2BCiADDqHPKIh08KOtWaG11ked/gAMNZiFANTsT/X8Q9ZR0Qa/fErnv/boTai0+yha5P8zpiB+2xp9unf1nXyCWvN61qxbbOPVPr78El6IyCgii1AboRLanJ6X+OUHxGcjdrqDMQeiwq/FOaBxvWOaWo045d7WO3ooA1qYoDSGYTE3KX6AHtl8gLM0yyKDzgW2WiJWslF86D9fVHdfPkDHx+y3e4RBgWD+lvzajom2DcWe3huTs15rtldBUMDEFMH3FYhU6BnfAwzPUjkucLvhMe3gtgqNpMOiAckVxg1Joyhfcs4RVNxNkji8daqf9eufgFpmGWKgh6sg3fUfZKT+BQAiH4qRvcz84Vvy2nKbM3lXDeQkFnxI5x6tT/PiaXR6alsX/j4U/mVLzKpNmnqWcE/Zxv6zh2ncaGDx5eBqS89mh0di3KOrJS83x+JTU4NYJ07Me2NEGmyQ2Ow=&quot;&gt;&lt;SafUserNbr&gt;2894&lt;/SafUserNbr&gt;&lt;SafUserName&gt;INTELUSER&lt;/SafUserName&gt;&lt;FirstName&gt;INTELLECT&lt;/FirstName&gt;&lt;LastName&gt;DESIGN&lt;/LastName&gt;&lt;ExpirationDate&gt;2026-07-21T15:55:57.10866-05:00&lt;/ExpirationDate&gt;&lt;AuthenticatedProducts&gt;&lt;ProdNbr&gt;3&lt;/ProdNbr&gt;&lt;ProdPersId&gt;153012&lt;/ProdPersId&gt;&lt;/AuthenticatedProducts&gt;&lt;AuthenticatedProducts&gt;&lt;ProdNbr&gt;1&lt;/ProdNbr&gt;&lt;ProdPersId&gt;2894&lt;/ProdPersId&gt;&lt;/AuthenticatedProducts&gt;&lt;/WhoIsResponse&gt;" 
                },
                "accessToken":"eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6ImFGa21LVkZjLTRXVjZzWENCdk5aa1hJNTA1WSIsImtpZCI6ImFGa21LVkZjLTRXVjZzWENCdk5aa1hJNTA1WSJ9.eyJhdWQiOiJhcGk6Ly84OGZhNGE2ZS1mMmMxLTQxYzEtOTRjMC1iOTFiMTY4ZTA4MGEvT04xOTYtSW50ZWxsZWN0IiwiaXNzIjoiaHR0cHM6Ly9zdHMud2luZG93cy5uZXQvODhmYTRhNmUtZjJjMS00MWMxLTk0YzAtYjkxYjE2OGUwODBhLyIsImlhdCI6MTc4NDYxMjE1NiwibmJmIjoxNzg0NjEyMTU2LCJleHAiOjE3ODQ2MTYwNTYsImFpbyI6ImsyRmdZT2lRQ0dUNXhmWXJzMHBPK3RmNU1MNGRHcGYvTFR2RUtNZHNMUk9ySEtibUxnZ0EiLCJhcHBpZCI6Ijc1ZTIwZDkzLTk2ZTItNDdhNC1hNzYxLTJjMTZhNGJmMDU4MiIsImFwcGlkYWNyIjoiMSIsImlkcCI6Imh0dHBzOi8vc3RzLndpbmRvd3MubmV0Lzg4ZmE0YTZlLWYyYzEtNDFjMS05NGMwLWI5MWIxNjhlMDgwYS8iLCJvaWQiOiJiZjc0YTg2Ni1jNzgwLTRhYjYtODUxYS0yYWM1OTQyYjNlZWEiLCJyaCI6IjEuQVJVQWJrcjZpTUh5d1VHVXdMa2JGbzRJQ3JJUk9MbHY2SHRFa2ROME5WQ1FWWW9BQUFBVkFBLiIsInJvbGVzIjpbIkFQSS5Db3JlUHJveHkuQWxsIl0sInN1YiI6ImJmNzRhODY2LWM3ODAtNGFiNi04NTFhLTJhYzU5NDJiM2VlYSIsInRpZCI6Ijg4ZmE0YTZlLWYyYzEtNDFjMS05NGMwLWI5MWIxNjhlMDgwYSIsInV0aSI6IlRxemZNY1g3bjBXbFdIMzA1X2NPQUEiLCJ2ZXIiOiIxLjAiLCJ4bXNfZnRkIjoiTHRrbkIxTFVfa2ZCSURobU9yYTJDd29ReUx0TmpjZnpHY1lzZF9yekktUUJkWE5sWVhOMExXUnpiWE0ifQ.j1VmkLLDtTLd7Zmnw9Yrj6yZe580ElGvEVq-z_VGh3AXPnYukfg2EFkyJ15Ltjqovf9--I1YleyvIlxmT__jRYquf9obb66dqLmqAcpAsN-DZ4JWjLabsHTQUHjEKh6anIZTgSwdhLcAA-HLiBunmnYXetUoQaAV6E-fGd_H2uWZIV_1PjKie-wxlb-_JWzA49FVlwNckMrkv6usjxDEP6unt_7S7Wo9SlI2h3FC0NNag4c0awidmxl8_JJonwzit_dgC0FIVzB03gzpbjcLHB47wIHd5MfqP2zr1cFfJ3Ch5znwXb-Pa_sKsKtQLDga2YqQf-vEzIdurpdkNEzYcw",
                "multiBillRequest": {
                    "debitAccount": "551963980018", 
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
