package com.dep.integration.transaction.hook.fiserv;

import com.dep.integration.transaction.hook.fiserv.dto.common.EndpointAttributes;

import java.net.http.HttpClient;
import java.time.Duration;

public class CgiTransactionProcessor extends FiservTransactionProcessor{

    public CgiTransactionProcessor() {
    }

    public CgiTransactionProcessor(boolean isTestMode) {
        super(isTestMode);
    }

    @Override
    public FiservApiClient createApiClient(EndpointAttributes endpointAttributes) {
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
