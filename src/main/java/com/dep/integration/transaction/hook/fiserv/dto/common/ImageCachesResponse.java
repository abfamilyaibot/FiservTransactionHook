package com.dep.integration.transaction.hook.fiserv.dto.common;

import java.util.List;

public record ImageCachesResponse(
   List<ChequeImageTransaction> chequeImageTransactions
) {

}
