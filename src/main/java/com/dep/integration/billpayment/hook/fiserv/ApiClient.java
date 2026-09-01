package com.dep.integration.billpayment.hook.fiserv;

import com.dep.integration.billpayment.hook.fiserv.dto.common.CbsApiException;
import com.dep.integration.billpayment.hook.fiserv.dto.common.MultiBillResponseDetail;
import com.dep.integration.billpayment.hook.fiserv.dto.common.Request;

/**
 * this client populate the correct headers etc and send the cbs request using HttpClient
 */
public interface ApiClient {

   MultiBillResponseDetail payBill(
       String cbsPath,
       Request depRequest,
       Object cbsRequest
   ) throws CbsApiException;
}
