package com.dsms.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private Money() {}

    public static BigDecimal ofHours(int seconds, BigDecimal hourlyRate) {
        if (hourlyRate == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return hourlyRate.multiply(BigDecimal.valueOf(seconds)).divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
