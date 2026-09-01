package com.dep.integration.billpayment.hook.fiserv.dto.common;

import com.dep.integration.billpayment.hook.fiserv.dto.common.Error;

public record MultiBillResponseDetail(
    String paymentId,
    String confirmationNumber,
    Error error
) {

}
