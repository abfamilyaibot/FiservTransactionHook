package com.dep.integration.billpayment.hook.fiserv.dto.common;

public record EndpointAttributes(
    long connTimeoutMs,
    long readTimeoutMs,
    String keystorePath,
    String keystorePassword,
    String keystoreType,
    String truststorePath,
    String truststorePassword,
    String truststoreType,
    String serverUri) {

}
