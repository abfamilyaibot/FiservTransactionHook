package com.dep.integration.transaction.hook.fiserv.dto;

import com.dep.integration.transaction.hook.fiserv.dto.common.CasaTransactionDtlsResponse;
import com.dep.integration.transaction.hook.fiserv.dto.common.Error;
import com.dep.integration.transaction.hook.fiserv.dto.common.Response;

public record FiservResponse( CasaTransactionDtlsResponse casaTransactionDtlsResponse,
    FiservImageCachesResponse imageCachesResponse,
    Error error
) implements Response {

}
