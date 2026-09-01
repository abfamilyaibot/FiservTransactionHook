package com.dep.integration.transaction.hook.fiserv;

import java.util.Map;

import org.glassfish.jaxb.runtime.marshaller.NamespacePrefixMapper;

public class NSPrefixMapper extends NamespacePrefixMapper {
    private Map<String, String> namespaceMap = Map.of(
        "http://www.w3.org/2001/XMLSchema-instance", "i",
        "http://schemas.xmlsoap.org/soap/envelope/", "s",
        "http://schemas.microsoft.com/2003/10/Serialization/Arrays", "d6p1",
        "http://schemas.datacontract.org/2004/07/CoreApiExtension.Messages.BillPayHistory", "d8p1",
        "http://schemas.datacontract.org/2004/07/OpenSolutions.CoreApiService.Services.Messages", "m",
        "http://www.opensolutions.com/CoreApi", "core"
    );

    @Override
    public String getPreferredPrefix(String namespaceUri, String suggestion,
        boolean requirePrefix) {
      return namespaceMap.getOrDefault(namespaceUri, suggestion);
    }
  }
