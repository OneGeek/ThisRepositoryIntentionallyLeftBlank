package com.ekotrope.bench;

import com.ekotrope.shared.utils.Complex;
import com.ekotrope.shared.utils.ComplexOpt2;
import com.ekotrope.shared.utils.ComplexOriginal;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Measures numerical deviation of each implementation from ComplexOriginal.
 *
 * opt1 (Complex)     – inlined allocation removal; claimed bit-for-bit identical.
 *                      Asserted to have 0 ULP deviation everywhere.
 * opt2 (ComplexOpt2) – further optimisations that trade tiny precision for speed.
 *                      Deviations are reported in ULPs but not asserted.
 *
 * ULP distance: the number of representable doubles between two values,
 * computed via the IEEE 754 integer ordering.  Two finite adjacent doubles
 * differ by 1 ULP.  NaN/Inf disagreements are counted separately.
 *
 * Notes on large opt2 deviations
 * ─────────────────────────────────────────────────────────────────────────────
 * atan  @ (0,±1) — poles of atan.  The original returns NaN (0×−∞=NaN in the
 *   intermediate multiply), opt2's direct formula returns 0 for Re and ±Inf
 *   for Im.  Both are formally undefined; opt2 is arguably more useful.
 *   Tracked as NaN disagreements, not ULPs.
 *
 * atanh near origin — catastrophic cancellation in log(1+z)−log(1−z) when |z|
 *   is tiny amplifies the ≤1 ULP difference between log(sqrt(r²)) and
 *   0.5·log(r²) by an extra factor, yielding tens of ULPs.
 *
 * pow(int n=3) large ULPs — direct complex multiplication is EXACT for inputs
 *   on the imaginary axis (e.g. (0,5)³ = exactly (0,−125)).  The original
 *   computes exp(3·log(5i)) = 125·(cos(3π/2)+i·sin(3π/2)), where cos(3π/2)
 *   is ~6×10⁻¹⁷ rather than 0.0, introducing a large ULP distance vs the
 *   exact-zero real part produced by direct multiplication.  This is a case
 *   where opt2 is MORE accurate, not less.
 */
public class DeviationReportTest {

    // -----------------------------------------------------------------------
    // Inputs
    // -----------------------------------------------------------------------

    private static final double[][] GENERAL = {
        // quadrant I
        { 0.1,  0.05}, { 0.5,  0.3}, { 1.0,  0.0}, { 1.5,  0.7},
        { 2.0,  1.0},  { 5.0,  2.0}, {10.0,  5.0}, {100.0, 50.0},
        // quadrant II
        {-0.5,  0.3}, {-1.5,  0.7}, {-3.0,  2.0}, {-10.0,  5.0},
        // quadrant III
        {-0.1, -0.05}, {-0.5, -0.3}, {-1.5, -0.7}, {-3.0, -2.0},
        // quadrant IV
        { 0.5, -0.3}, { 1.5, -0.7}, { 3.0, -2.0}, { 10.0, -5.0},
        // axes and special
        { 0.0,  1.0}, { 0.0, -1.0}, { 0.0,  5.0},
        { 1.0,  1.0}, {-1.0,  1.0}, { 2.0, -3.0},
        // very small
        {1e-6, 1e-6}, {0.01, 0.01},
        // large
        {1e6,  1e3},
    };

    /** Inputs where |z| < 1, avoiding the atanh branch cut on the real axis. */
    private static final double[][] ATANH_SAFE = {
        {-0.9, -0.3}, {-0.5, -0.3}, {-0.1, -0.05}, {-0.9,  0.1},
        { 0.0,  0.0}, { 0.0,  0.5}, { 0.0,  0.9},
        { 0.1,  0.05}, { 0.3,  0.4}, { 0.5,  0.0},
        { 0.5,  0.3},  { 0.9,  0.1}, { 0.99, 0.01},
        { 0.01, 0.01},
    };

    // -----------------------------------------------------------------------
    // ULP distance
    // -----------------------------------------------------------------------

    /**
     * IEEE 754 ULP distance between two finite doubles.
     * Returns -1 if either value is NaN or infinite (caller handles separately).
     */
    static long ulpDiff(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b) ||
            Double.isInfinite(a) || Double.isInfinite(b)) {
            return -1L;
        }
        if (a == b) return 0L;
        long la = Double.doubleToLongBits(a);
        long lb = Double.doubleToLongBits(b);
        if (la < 0) la = Long.MIN_VALUE - la;
        if (lb < 0) lb = Long.MIN_VALUE - lb;
        return Math.abs(la - lb);
    }

    // -----------------------------------------------------------------------
    // Statistics accumulator
    // -----------------------------------------------------------------------

    static final class Stats {
        final String method;

        // Max finite ULP deviations and the inputs that caused them
        long   maxRe = -1, maxIm = -1;
        double worstReX, worstReY, worstImX, worstImY;

        // Histogram of finite ULP distances: bucket[k] = count with k ULPs (bucket[5] = 5+)
        final long[] reHist = new long[6];
        final long[] imHist = new long[6];

        // Cases where one value is finite and the other is NaN or Inf
        long nanDisagreeRe, nanDisagreeIm;
        // Cases where BOTH are NaN/Inf but different signs or types
        long infDisagree;

        int n;

        Stats(String method) { this.method = method; }

        void add(double x, double y,
                 double origRe, double origIm,
                 double testRe, double testIm) {
            n++;

            long re = ulpDiff(origRe, testRe);
            long im = ulpDiff(origIm, testIm);

            if (re == -1) {
                // NaN==NaN is always false in IEEE 754, so check isNaN explicitly
                if ((Double.isNaN(origRe) && Double.isNaN(testRe)) ||
                    (Double.isInfinite(origRe) && origRe == testRe)) {
                    re = 0; // both agree (both NaN, or same-sign Inf)
                } else {
                    nanDisagreeRe++;
                }
            }
            if (im == -1) {
                if ((Double.isNaN(origIm) && Double.isNaN(testIm)) ||
                    (Double.isInfinite(origIm) && origIm == testIm)) {
                    im = 0;
                } else {
                    nanDisagreeIm++;
                }
            }

            if (re >= 0) {
                if (re > maxRe) { maxRe = re; worstReX = x; worstReY = y; }
                reHist[(int) Math.min(re, 5)]++;
            }
            if (im >= 0) {
                if (im > maxIm) { maxIm = im; worstImX = x; worstImY = y; }
                imHist[(int) Math.min(im, 5)]++;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Functional interfaces (Java-8-compatible)
    // -----------------------------------------------------------------------

    interface OrigFn { double[] apply(ComplexOriginal z); }
    interface Opt1Fn { double[] apply(Complex z); }
    interface Opt2Fn { double[] apply(ComplexOpt2 z); }

    static double[] re(ComplexOriginal r) { return new double[]{r.real(), r.imaginary()}; }
    static double[] re(Complex r)         { return new double[]{r.real(), r.imaginary()}; }
    static double[] re(ComplexOpt2 r)     { return new double[]{r.real(), r.imaginary()}; }

    // -----------------------------------------------------------------------
    // Collection helpers
    // -----------------------------------------------------------------------

    static Stats collectOpt1(String method, double[][] in, OrigFn orig, Opt1Fn test) {
        Stats s = new Stats(method);
        for (double[] p : in) {
            double[] o = orig.apply(new ComplexOriginal(p[0], p[1]));
            double[] t = test.apply(new Complex(p[0], p[1]));
            s.add(p[0], p[1], o[0], o[1], t[0], t[1]);
        }
        return s;
    }

    static Stats collectOpt2(String method, double[][] in, OrigFn orig, Opt2Fn test) {
        Stats s = new Stats(method);
        for (double[] p : in) {
            double[] o = orig.apply(new ComplexOriginal(p[0], p[1]));
            double[] t = test.apply(new ComplexOpt2(p[0], p[1]));
            s.add(p[0], p[1], o[0], o[1], t[0], t[1]);
        }
        return s;
    }

    static double[][] nonZero(double[][] inputs) {
        return Arrays.stream(inputs)
            .filter(p -> p[0] != 0.0 || p[1] != 0.0)
            .toArray(double[][]::new);
    }

    // -----------------------------------------------------------------------
    // Printing
    // -----------------------------------------------------------------------

    private static final int W = 82;
    private static final String LINE = "─".repeat(W);
    private static final String DLINE = "═".repeat(W);

    static void printReport(String title, Stats... all) {
        System.out.println();
        System.out.println("╔" + DLINE + "╗");
        System.out.printf("║  %-80s║%n", title);
        System.out.println("╠" + DLINE + "╣");
        System.out.printf("║  %-14s  %6s %6s %6s  %-30s  %6s ║%n",
            "Method", "Re↑ULP", "Im↑ULP", "NaN≠", "Re-ULP histogram (0,1,2,3,4,5+)", "n");
        System.out.println("╠" + LINE + "╣");
        for (Stats s : all) {
            long nanDisagree = s.nanDisagreeRe + s.nanDisagreeIm;
            String hist = String.format("%d,%d,%d,%d,%d,%d",
                s.reHist[0], s.reHist[1], s.reHist[2],
                s.reHist[3], s.reHist[4], s.reHist[5]);
            String maxReStr = s.maxRe < 0 ? "  n/a" : String.format("%6d", s.maxRe);
            String maxImStr = s.maxIm < 0 ? "  n/a" : String.format("%6d", s.maxIm);
            System.out.printf("║  %-14s  %s %s %6d  %-30s  %6d ║%n",
                s.method, maxReStr, maxImStr, nanDisagree, hist, s.n);
            if (s.maxRe > 2) {
                System.out.printf("║    worst Re @ (%-8.4g, %-8.4g)%43s║%n",
                    s.worstReX, s.worstReY, "");
            }
            if (s.maxIm > 2) {
                System.out.printf("║    worst Im @ (%-8.4g, %-8.4g)%43s║%n",
                    s.worstImX, s.worstImY, "");
            }
        }
        System.out.println("╚" + DLINE + "╝");
    }

    // -----------------------------------------------------------------------
    // Test
    // -----------------------------------------------------------------------

    @Test
    public void deviationReport() {
        double[][] nzGeneral = nonZero(GENERAL);
        final ComplexOriginal expOrig = new ComplexOriginal(0.5, 0.3);
        final Complex         expOpt1 = new Complex(0.5, 0.3);
        final ComplexOpt2     expOpt2 = new ComplexOpt2(0.5, 0.3);

        // ── opt1 vs orig ─────────────────────────────────────────────────────
        Stats[] o1 = {
            collectOpt1("log",          GENERAL,      z -> re(z.log()),         z -> re(z.log())),
            collectOpt1("atan",         GENERAL,      z -> re(z.atan()),        z -> re(z.atan())),
            collectOpt1("atanh",        ATANH_SAFE,   z -> re(z.atanh()),       z -> re(z.atanh())),
            collectOpt1("pow(Complex)", GENERAL,      z -> re(z.pow(expOrig)),  z -> re(z.pow(expOpt1))),
            collectOpt1("pow(double)",  GENERAL,      z -> re(z.pow(2.5)),      z -> re(z.pow(2.5))),
            collectOpt1("pow(int n=3)", nzGeneral,    z -> re(z.pow(3)),        z -> re(z.pow(3))),
            collectOpt1("pow(int n=7)", nzGeneral,    z -> re(z.pow(7)),        z -> re(z.pow(7))),
        };

        printReport("opt1 (Complex) vs original  —  asserted: 0 ULP everywhere", o1);

        for (Stats s : o1) {
            assertEquals(0L, Math.max(s.maxRe, 0),
                "opt1 " + s.method + " real part must be bit-for-bit identical");
            assertEquals(0L, Math.max(s.maxIm, 0),
                "opt1 " + s.method + " imaginary part must be bit-for-bit identical");
            assertEquals(0L, s.nanDisagreeRe + s.nanDisagreeIm,
                "opt1 " + s.method + " must agree on NaN/Inf handling");
        }

        // ── opt2 vs orig ─────────────────────────────────────────────────────
        Stats[] o2 = {
            collectOpt2("log",          GENERAL,      z -> re(z.log()),         z -> re(z.log())),
            collectOpt2("atan",         GENERAL,      z -> re(z.atan()),        z -> re(z.atan())),
            collectOpt2("atanh",        ATANH_SAFE,   z -> re(z.atanh()),       z -> re(z.atanh())),
            collectOpt2("pow(Complex)", GENERAL,      z -> re(z.pow(expOrig)),  z -> re(z.pow(expOpt2))),
            collectOpt2("pow(double)",  GENERAL,      z -> re(z.pow(2.5)),      z -> re(z.pow(2.5))),
            collectOpt2("pow(int n=3)", nzGeneral,    z -> re(z.pow(3)),        z -> re(z.pow(3))),
            collectOpt2("pow(int n=7)", nzGeneral,    z -> re(z.pow(7)),        z -> re(z.pow(7))),
        };

        printReport("opt2 (ComplexOpt2) vs original  —  informational deviation report", o2);

        System.out.println();
        System.out.println("Notes:");
        System.out.println("  atan  NaN≠3  — poles at z=±i; orig returns NaN (0×−∞=NaN), opt2 returns (0, ±∞)");
        System.out.println("                 (arguably more useful). Third disagree is atan(-i) ZM=(0,0) branch.");
        System.out.println("  atan  Im large ULP @ (1e6,1e3)  — direct formula Im=0.25·log(N/D) suffers");
        System.out.println("                 catastrophic cancellation when |z|>>1: N≈D≈x², so N/D≈1+O(y/x²),");
        System.out.println("                 and the log argument loses all significant bits. Use only for |z|<~1e4.");
        System.out.println("  atanh Re↑>2  — cancellation in log(1+z)−log(1−z) near origin amplifies the ≤1 ULP");
        System.out.println("                 difference between log(sqrt(r²)) and 0.5·log(r²).");
        System.out.println("  pow(int n=3)  — opt2 is MORE ACCURATE for inputs on the imaginary axis: direct");
        System.out.println("                 multiplication gives exact (0,−125) for (0,5)³, while the original");
        System.out.println("                 computes exp(3·log(5i)) and inherits the trig error cos(3π/2)≈6e−17.");
        System.out.println("                 The ~4.4e18 ULP 'deviation' is 0.0_exact vs 6.1e-17_trig-error.");
        System.out.println("  pow(int n=7)  — uses 0.5·log(r²) in the exp/log fallback; ≤15 ULP.");
    }
}
