package com.dep.integration.transaction.hook.fiserv.dto.common;

public record Response( CasaTransactionDtlsResponse casaTransactionDtlsResponse,
    ImageCachesResponse imageCachesResponse,
    Error error
) {

}
