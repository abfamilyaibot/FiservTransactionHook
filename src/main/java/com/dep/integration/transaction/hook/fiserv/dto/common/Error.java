package com.dep.integration.transaction.hook.fiserv.dto.common;

public record Error(
    String errorCode,
    String errorDescription
) {

}
