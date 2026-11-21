package vn.unicom.thi360.attempt.domain.grading;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable exact rational number backed by reduced {@link BigInteger} numerator/denominator.
 *
 * <p>Prevents intermediate rounding drift when summing non-terminating decimals such as {@code 1/3}
 * before the single final HALF_UP rounding step at scale 2 (BE §8, LT §9.1).
 */
public record ExactRational(BigInteger numerator, BigInteger denominator)
        implements Comparable<ExactRational> {

    public static final ExactRational ZERO = new ExactRational(BigInteger.ZERO, BigInteger.ONE);
    public static final ExactRational ONE = new ExactRational(BigInteger.ONE, BigInteger.ONE);

    public ExactRational {
        Objects.requireNonNull(numerator, "numerator must not be null");
        Objects.requireNonNull(denominator, "denominator must not be null");
        if (denominator.signum() == 0) {
            throw new ArithmeticException("Denominator cannot be zero");
        }
        if (numerator.signum() == 0) {
            numerator = BigInteger.ZERO;
            denominator = BigInteger.ONE;
        } else {
            if (denominator.signum() < 0) {
                numerator = numerator.negate();
                denominator = denominator.negate();
            }
            BigInteger gcd = numerator.abs().gcd(denominator);
            if (!gcd.equals(BigInteger.ONE)) {
                numerator = numerator.divide(gcd);
                denominator = denominator.divide(gcd);
            }
        }
    }

    public static ExactRational of(long numerator, long denominator) {
        return new ExactRational(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    public static ExactRational of(BigDecimal decimal) {
        if (decimal == null) {
            return ZERO;
        }
        if (decimal.compareTo(BigDecimal.ZERO) == 0) {
            return ZERO;
        }
        int scale = decimal.scale();
        BigInteger unscaled = decimal.unscaledValue();
        if (scale >= 0) {
            return new ExactRational(unscaled, BigInteger.TEN.pow(scale));
        }
        return new ExactRational(unscaled.multiply(BigInteger.TEN.pow(-scale)), BigInteger.ONE);
    }

    public static ExactRational parse(String text) {
        if (text == null || text.isBlank()) {
            return ZERO;
        }
        String trimmed = text.trim();
        int slash = trimmed.indexOf('/');
        if (slash >= 0) {
            BigInteger num = new BigInteger(trimmed.substring(0, slash).trim());
            BigInteger den = new BigInteger(trimmed.substring(slash + 1).trim());
            return new ExactRational(num, den);
        }
        return of(new BigDecimal(trimmed));
    }

    public ExactRational add(ExactRational other) {
        if (other == null || other.isZero()) {
            return this;
        }
        if (this.isZero()) {
            return other;
        }
        return new ExactRational(
                this.numerator.multiply(other.denominator).add(other.numerator.multiply(this.denominator)),
                this.denominator.multiply(other.denominator));
    }

    public ExactRational subtract(ExactRational other) {
        if (other == null || other.isZero()) {
            return this;
        }
        return add(new ExactRational(other.numerator.negate(), other.denominator));
    }

    public ExactRational multiply(ExactRational other) {
        if (other == null || other.isZero() || this.isZero()) {
            return ZERO;
        }
        return new ExactRational(
                this.numerator.multiply(other.numerator),
                this.denominator.multiply(other.denominator));
    }

    public ExactRational divide(ExactRational other) {
        Objects.requireNonNull(other, "divisor must not be null");
        if (other.isZero()) {
            throw new ArithmeticException("Division by zero");
        }
        if (this.isZero()) {
            return ZERO;
        }
        return new ExactRational(
                this.numerator.multiply(other.denominator),
                this.denominator.multiply(other.numerator));
    }

    public boolean isZero() {
        return numerator.signum() == 0;
    }

    public boolean isPositive() {
        return numerator.signum() > 0;
    }

    /** Rounds exact rational once to {@code scale} decimals using HALF_UP with zero intermediate rounding. */
    public BigDecimal toBigDecimal(int scale) {
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), scale, RoundingMode.HALF_UP);
    }

    public String toFractionString() {
        return denominator.equals(BigInteger.ONE) ? numerator.toString() : numerator + "/" + denominator;
    }

    @Override
    public int compareTo(ExactRational other) {
        return this.numerator.multiply(other.denominator).compareTo(other.numerator.multiply(this.denominator));
    }
}
