package com.dep.integration.transaction.hook.fiserv.dto.common;

public interface Response {
    public CasaTransactionDtlsResponse casaTransactionDtlsResponse();
    public Error error();

}
