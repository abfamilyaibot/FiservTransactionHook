package com.dep.integration.transaction.hook.fiserv.dto.common;

import java.util.List;

public record CasaTransactionDtlsResponse(
    List<CasaTransactionDtl> casatransactiondtls,
    Integer totalRowCount
) {

}
