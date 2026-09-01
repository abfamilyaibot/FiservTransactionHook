package com.dep.integration.transaction.hook.fiserv.dto;

import java.util.List;
import com.dep.integration.transaction.hook.fiserv.dto.jaxb.messages.Rtxn;

public record FiservImageCachesResponse(
   List<Rtxn> rtxns
) {

}
