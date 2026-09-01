package com.dep.integration.transaction.hook.fiserv.dto.common;

import com.dep.integration.transaction.hook.fiserv.dto.FiservImageCachesResponse;

public record Response( CasaTransactionDtlsResponse casaTransactionDtlsResponse,
    FiservImageCachesResponse imageCachesResponse,
    Error error
) {

}
