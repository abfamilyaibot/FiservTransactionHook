package com.dep.integration.billpayment.hook.fiserv.dto.common;

import com.dep.integration.billpayment.hook.fiserv.dto.common.MultiBillResponseDetail;

import java.util.List;

public record Response(
    List<MultiBillResponseDetail> multiBillResponseDetails
) {

}
