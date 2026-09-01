package com.dep.integration.transaction.hook.fiserv.dto;

import com.dep.integration.transaction.hook.fiserv.dto.jaxb.billpayhistory.BillPayment;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.messages.Rtxn;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.messages.Transaction;

public record FiservTransaction(
    Rtxn rtxn,
    BillPayment billPayment,
    Transaction transaction
) {

}
