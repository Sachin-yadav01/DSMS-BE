package com.dsms.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LargestRemainderTest {
    @Test
    void distributesExactly() {
        Map<Long, Integer> result = AllocationService.largestRemainder(Map.of(1L, 70, 2L, 30), 10, 100);
        assertThat(result.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(10);
        assertThat(result.get(1L)).isEqualTo(7);
        assertThat(result.get(2L)).isEqualTo(3);
    }

    @Test
    void hourlyMoneyExample() {
        assertThat(Money.ofHours(7200, new java.math.BigDecimal("1000.00"))).isEqualByComparingTo("2000.00");
        assertThat(Money.ofHours(7200, new java.math.BigDecimal("600.00"))).isEqualByComparingTo("1200.00");
    }
}
