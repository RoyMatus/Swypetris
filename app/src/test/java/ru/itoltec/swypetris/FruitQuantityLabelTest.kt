package ru.itoltec.swypetris

import org.junit.Assert.assertEquals
import org.junit.Test

class FruitQuantityLabelTest {
    @Test fun smallCountsStayExactAndLargeCountsUseCompactUnits() {
        val examples = mapOf(0L to "0", 1L to "1", 999L to "999", 1000L to "1K",
            1200L to "1.2K", 1999L to "1.9K", 10000L to "10K", 12300L to "12.3K",
            999999L to "999K", 1000000L to "1M", 3400000L to "3.4M",
            1000000000L to "1B", 1200000000L to "1.2B", 1000000000000L to "1T",
            115292150460685L to "115T")
        examples.forEach { (count, label) -> assertEquals("Quantity $count", label, fruitQuantityLabel(count)) }
    }
}
