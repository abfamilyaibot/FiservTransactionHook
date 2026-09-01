package com.dep.integration.billpayment.hook.fiserv.dto;

import java.math.BigDecimal;

public record MultiBillRequestDetail(
    BigDecimal paymentAmount,
    String currency,
    String scheduleType,
    String paymentDate,
    String paymentEndDate,
    String vendorAccountNumber,
    String vendorId,
    String cbsFrequencyType
) {

}
