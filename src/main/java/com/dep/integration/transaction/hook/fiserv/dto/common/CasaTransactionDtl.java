package com.dep.integration.transaction.hook.fiserv.dto.common;

import java.math.BigDecimal;

public record CasaTransactionDtl(
    String tenantId, 
    String transactionDate,
    String valueDate,
    String remarks, // not mapped as transfer memo is part of transactionDescription (from Rtxn.InternalRtxnDescription) and cannot extract the transfer memo portion
    BigDecimal transactionAmount,
    String transactionReference,
    String transactionDescription,
    String merchantId, 
    String transactionCategory,
    BigDecimal balance,
    String debitCreditFlag,
    String instrumentId,
    String transactionType, 
    String chequeNumber,
    String exchangeRate,
    BigDecimal exchangeAmount,
    String accountNumber,
    String transactionCurrency,
    String confirmationNumber,
    String accountHolderName,
    BigDecimal principalAmount,
    BigDecimal interestChargeAmount,
    String transactionCategoryId, 
    String transType 
 ) {

}
