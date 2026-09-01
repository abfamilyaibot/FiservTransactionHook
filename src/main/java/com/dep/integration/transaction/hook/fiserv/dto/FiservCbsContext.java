package com.dep.integration.transaction.hook.fiserv.dto;

public record FiservCbsContext(
    String userId,
    String applID,
    String networkNodeName,
    String password
) {
}
