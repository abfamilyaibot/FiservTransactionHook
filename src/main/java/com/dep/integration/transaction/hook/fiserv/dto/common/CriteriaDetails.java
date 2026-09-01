package com.dep.integration.transaction.hook.fiserv.dto.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record CriteriaDetails( 
    String startDate, // filtering in BH
    String endDate,  // filtering in BH
    FilterType filterType,
    String accountNumber, // filtering in BH
    SearchType searchType,
    String searchValue,
    SortingOrder sortingOrder // sorting in BH
) {

    public enum FilterType {
        BILL("Bill"),
        CHEQUE("Cheque"),
        D("D"),
        C("C");

        private final String value;

        FilterType(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }

        @JsonCreator
        public static FilterType fromValue(String value) {
            for (FilterType filterType : values()) {
                if (filterType.value.equals(value)) {
                    return filterType;
                }
            }
            throw new IllegalArgumentException("Unsupported filterType: " + value);
        }
    }

    public enum SearchType {
        DESCRIPTION,
        CONFIRMATION_NUMBER,
        CHEQUE_NUMBER,
        AMOUNT
    }

    public enum SortingOrder {
        ASC,
        DESC
    }
}
