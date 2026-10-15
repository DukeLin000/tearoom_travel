package com.tripmate.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private Money() {}
    public static BigDecimal parse(String value) {
        if (value == null || !value.matches("(?:0|[1-9][0-9]{0,11})(?:\\.[0-9]{1,2})?")) {
            throw ApiException.invalid("Amount must be a nonnegative decimal string with at most 12 integer and 2 fraction digits");
        }
        return new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);
    }
    public static String format(BigDecimal value) { return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString(); }
}
