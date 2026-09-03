package com.dep.integration.transaction.hook.fiserv.dto;

import com.dep.integration.transaction.hook.fiserv.dto.common.CriteriaDetails;
import com.dep.integration.transaction.hook.fiserv.dto.common.Request;

public record FiservRequest  (
    FiservCbsContext cbsContext,
    String depTenantId,
    AccountInfo accountInfo,
    CriteriaDetails criteriaDetails,
    String accessToken 
) implements Request {
}
