package com.dep.integration.billpayment.hook.fiserv.dto.common;

public record Error(
    String errorCode,
    String errorDescription
) {

}
