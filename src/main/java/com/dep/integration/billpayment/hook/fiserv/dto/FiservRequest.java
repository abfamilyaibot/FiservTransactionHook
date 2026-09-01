package com.dep.integration.billpayment.hook.fiserv.dto;


import com.dep.integration.billpayment.hook.fiserv.dto.common.MultiBillRequest;
import com.dep.integration.billpayment.hook.fiserv.dto.common.Request;

public record FiservRequest  (
    FiservCbsContext cbsContext,
    String accessToken,
    MultiBillRequest multiBillRequest) implements Request {
}
