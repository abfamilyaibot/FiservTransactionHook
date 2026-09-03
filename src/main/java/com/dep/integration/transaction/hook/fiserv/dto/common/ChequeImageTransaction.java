package com.dep.integration.transaction.hook.fiserv.dto.common;

import java.math.BigDecimal;

public record ChequeImageTransaction( 
    String checkNumber,
    String transactionDate, // in yyyy-MM-dd format
    BigDecimal transactionAmount,
    String traceNumber,
    String routeNumber,
    String transitNumber
) {

}
