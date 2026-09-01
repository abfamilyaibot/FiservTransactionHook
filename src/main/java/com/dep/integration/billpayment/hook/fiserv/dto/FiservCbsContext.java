package com.dep.integration.billpayment.hook.fiserv.dto;

public record FiservCbsContext(
    String userId,
    String applID,
    String networkNodeName,
    String password
) {
}

