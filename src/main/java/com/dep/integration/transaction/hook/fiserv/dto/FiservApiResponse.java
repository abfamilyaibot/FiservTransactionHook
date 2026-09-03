package com.dep.integration.transaction.hook.fiserv.dto;

import com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi.AccountTransactionHistoryResponse;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi.BillPayHistoryResponse;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi.TransactionHistoryInquiryResponse;

public record FiservApiResponse(
    AccountTransactionHistoryResponse accountTransactionHistoryResponse,
    BillPayHistoryResponse billPayHistoryResponse,
    TransactionHistoryInquiryResponse transactionHistoryInquiryResponse
) {

}
