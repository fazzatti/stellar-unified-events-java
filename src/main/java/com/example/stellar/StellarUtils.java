package com.example.stellar;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Simple utility for formatting Stellar supply amounts
 */
public class StellarUtils {
    // Stellar uses 7 decimal places (1 unit = 10,000,000 stroops)
    private static final BigInteger STROOPS_PER_UNIT = BigInteger.valueOf(10_000_000L);
    
    /**
     * Format supply for display (convert stroops to decimal)
     */
    public static String formatSupply(BigInteger stroops) {
        BigDecimal amount = new BigDecimal(stroops);
        BigDecimal divisor = new BigDecimal(STROOPS_PER_UNIT);
        BigDecimal result = amount.divide(divisor, 7, RoundingMode.DOWN);
        return result.stripTrailingZeros().toPlainString();
    }
}
