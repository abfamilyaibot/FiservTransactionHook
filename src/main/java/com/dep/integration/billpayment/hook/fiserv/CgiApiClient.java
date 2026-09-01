package com.dep.integration.billpayment.hook.fiserv;

import com.dep.integration.billpayment.hook.fiserv.dto.common.Request;
import com.dep.integration.billpayment.hook.fiserv.dto.FiservRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;

public class CgiApiClient extends FiservApiClient {
    public CgiApiClient(HttpClient httpClient, String baseUri, Duration readTimeout) {
        super(httpClient, baseUri, readTimeout);
    }

    public CgiApiClient(HttpClient httpClient, String baseUri, Duration readTimeout, boolean isTestMode) {
        super( httpClient, baseUri, readTimeout, isTestMode);
    }

    @Override
    protected HttpRequest generateHttpRequest(String requestSoapXml, Request depRequest) {
        FiservRequest fiservRequest = (FiservRequest) depRequest;
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(baseUri))
                .timeout(readTimeout)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header( "Authorization", "Bearer " + fiservRequest.accessToken())
                .header("SOAPAction", "http://www.opensolutions.com/CoreApi/ICoreApiService/SubmitRequest")
                .POST(HttpRequest.BodyPublishers.ofString(requestSoapXml));
        return requestBuilder.build();
    }
}
