package com.dep.integration.transaction.hook.fiserv;

import com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi.AccountTransactionHistoryResponse;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi.BillPayHistoryResponse;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.coreapi.TransactionHistoryInquiryResponse;

public record FiservResponse(
    AccountTransactionHistoryResponse accountTransactionHistoryResponse,
    BillPayHistoryResponse billPayHistoryResponse,
    TransactionHistoryInquiryResponse transactionHistoryInquiryResponse
) {

}
