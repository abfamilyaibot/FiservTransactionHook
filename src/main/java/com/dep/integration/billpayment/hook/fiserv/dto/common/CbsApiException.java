package com.dep.integration.billpayment.hook.fiserv.dto.common;

public class CbsApiException extends Exception {
      private String responseBodyJson;
      private String errorCode;

      public CbsApiException(String message, String responseBodyJson, String errorCode) {
         super(message);
         this.responseBodyJson = responseBodyJson;
         this.errorCode = errorCode;
      }

      public String responseBodyJson() {
         return responseBodyJson;
      }
      public String errorCode() { return errorCode; }
   }
