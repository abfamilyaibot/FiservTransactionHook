package com.dep.integration.transaction.hook.fiserv;

import java.util.HashMap;


import com.dep.integration.transaction.hook.fiserv.dto.common.EndpointAttributes;
import com.intellect.commons.core.HookImpl;
import com.intellect.commons.exception.HooksException;
import com.intellect.commons.holder.DataHolder;
import com.intellect.commons.loggers.ApplicationLogger;
import com.intellect.olive.transport.cache.TransportCacheManager;

public class TransactionHook implements HookImpl {

    private static final long serialVersionUID = 1L;
    private final ApplicationLogger logger = ApplicationLogger.getInstance("TransactionHook");

    @Override
    public DataHolder invoke(DataHolder inData) throws HooksException {
        DataHolder outData = new DataHolder();

        try {
            String requestJson = inData.get("requestJson");
            String endpointId = inData.get("endpointId");
            String serverUri = inData.get("serverUri");
            String cbsSystem = inData.get("cbsSystem");

            logger.logInfo("Initiating TransactionHook for endpointId: " + endpointId + " with serverUri: " + serverUri + " and requestJson: " + requestJson + " and cbsSystem: " + cbsSystem);

            TransportCacheManager cacheManager = TransportCacheManager.getInstance();
            HashMap<String, String> attributes = cacheManager.getEndpointAttributeMap(endpointId);

            if ( attributes == null || attributes.isEmpty()) {
            throw new HooksException("HOOK_ERROR", "No endpoint attributes found for endpointId: " + endpointId);
            }

            String targetUrl = serverUri != null && serverUri.isBlank() ? serverUri : attributes.get("SERVERURI");
            if ( targetUrl == null || targetUrl.isEmpty()) {
                throw new HooksException("HOOK_ERROR", "serverUri is empty and SERVERURI attribute is missing for endpointId: " + endpointId);
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


            TransactionProcessor processor = null;
            switch (cbsSystem) {
                case "CBS_FISERV_DNA":
                    processor = new FiservTransactionProcessor();
                    break;
                case "CBS_CGI_DNA":
                    processor = new CgiTransactionProcessor();
                    break;        
                default:
                    throw new HooksException("HOOK_ERROR", "Unsupported CBS system: " + cbsSystem);
            }

            String responseJson = processor.process(requestJson, endpointAttributes);
            outData.setData("responseJson", responseJson);
            return outData;
        } catch (Exception e) {
            logger.logError( "Error getting transactions", e);
            throw new HooksException("HOOK_ERROR", "Error getting transactions:" + e.getMessage());
        }
    }
}
