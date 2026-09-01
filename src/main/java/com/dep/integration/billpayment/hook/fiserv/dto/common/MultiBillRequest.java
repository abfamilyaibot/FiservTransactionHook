package com.dep.integration.billpayment.hook.fiserv.dto.common;

import com.dep.integration.billpayment.hook.fiserv.dto.MultiBillRequestDetail;

import java.util.List;

public record MultiBillRequest(
        String debitAccount,
        List<MultiBillRequestDetail> multiBillRequestDetails
) {
}
