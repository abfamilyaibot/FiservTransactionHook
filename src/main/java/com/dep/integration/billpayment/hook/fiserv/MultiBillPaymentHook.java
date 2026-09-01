package com.dep.integration.billpayment.hook.fiserv;

import java.util.HashMap;

import com.dep.integration.billpayment.hook.fiserv.dto.common.EndpointAttributes;
import com.intellect.commons.core.HookImpl;
import com.intellect.commons.exception.HooksException;
import com.intellect.commons.holder.DataHolder;
import com.intellect.commons.loggers.ApplicationLogger;
import com.intellect.olive.transport.cache.TransportCacheManager;

public class MultiBillPaymentHook implements HookImpl {

    private static final long serialVersionUID = 1L;
    private ApplicationLogger logger = ApplicationLogger.getInstance("MultiBillPaymentHook");

    @Override
    public DataHolder invoke(DataHolder inData) throws HooksException {
        DataHolder outData = new DataHolder();

        try {
            String requestJson = inData.get("requestJson");
            String endpointId = inData.get("endpointId");
            String cbsSystem = inData.get("cbsSystem");

            logger.logInfo("Initiating MultiBillPaymentHook for endpointId:" + endpointId + " with requestJson:" + requestJson);

            TransportCacheManager cacheManager = TransportCacheManager.getInstance();
            HashMap<String, String> attributes = cacheManager.getEndpointAttributeMap(endpointId);

            if ( attributes == null || attributes.isEmpty()) {
            throw new HooksException("HOOK_ERROR", "No endpoint attributes found for endpointId: " + endpointId);
            }

            String targetUrl = attributes.get("SERVERURI");
            if ( targetUrl == null || targetUrl.isEmpty()) {
                throw new HooksException("HOOK_ERROR", "SERVERURI attribute is missing for endpointId: " + endpointId);
            }
            
             EndpointAttributes endpointAttributes = new EndpointAttributes(
                Long.parseLong(attributes.getOrDefault("CONNTIMEOUT", "60000")),
                Long.parseLong(attributes.getOrDefault("READTIMEOUT", "60000")),
                attributes.get("KEYSTOREPATH"),
                attributes.get("KEYSTOREPASSWORD"),
                attributes.get("KEYSTORETYPE"),
                attributes.get("TRUSTSTOREPATH"),
                attributes.get("TRUSTSTOREPASSWORD"),
                attributes.get("TRUSTSTORETYPE"),
                targetUrl
            );

            logger.logInfo("EndpointAttributes from cache: " + endpointAttributes);

            MultiBillPaymentProcessor processor = null;
            switch (cbsSystem) {
                case "CBS_FISERV_DNA":
                    processor = new FiservMultiBillPaymentProcessor();
                    break;
                case "CBS_CGI_DNA":
                    processor = new CgiMultiBillPaymentProcessor();
                    break;        
                default:
                    throw new HooksException("HOOK_ERROR", "Unsupported CBS system: " + cbsSystem);
            }
            
            
            String responseJson = processor.asyncProcess(requestJson, endpointAttributes);
            outData.setData("responseJson", responseJson);
            return outData;
        }
        catch (Exception e) {
            logger.logError( "Error processing bill payment requests", e);
            throw new HooksException("HOOK_ERROR", "Error processing multi-bill payment:" + e.getMessage());
        }
    }
}
