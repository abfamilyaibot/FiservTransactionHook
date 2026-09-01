package com.dep.integration.billpayment.hook.fiserv;

import com.dep.integration.billpayment.hook.fiserv.dto.common.EndpointAttributes;

import java.net.http.HttpClient;
import java.time.Duration;

public class CgiMultiBillPaymentProcessor extends FiservMultiBillPaymentProcessor{

    public CgiMultiBillPaymentProcessor() {
    }

    public CgiMultiBillPaymentProcessor(boolean isTestMode) {
        super(isTestMode);
    }

    @Override
    public ApiClient createApiClient(EndpointAttributes endpointAttributes) {
        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(createSslContext(endpointAttributes))
                .connectTimeout(Duration.ofMillis(endpointAttributes.connTimeoutMs()))
                .build();
        return new CgiApiClient(
                httpClient,
                endpointAttributes.serverUri(),
                Duration.ofMillis(endpointAttributes.readTimeoutMs()),
                isTestMode
        );
    }
}
