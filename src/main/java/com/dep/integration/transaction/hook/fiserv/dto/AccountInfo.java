package com.dep.integration.transaction.hook.fiserv.dto;

public record AccountInfo(
    String accountNumber,
    String isLoanAccount,
    String accountHolderName,
    String transitNumber,
    String routeNumber,
    String accountCurrencyCode
) {

}
