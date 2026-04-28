package com.ekotrope.shared.utils;

/**
 * Further-optimised variant of Complex for benchmarking.
 *
 * Changes vs ComplexOriginal (results are NOT bit-for-bit identical):
 *
 *  1. log(sqrt(r²)) → 0.5*log(r²) everywhere — eliminates one Math.sqrt
 *     per log evaluation (affects log, pow*, atan, atanh).
 *
 *  2. pow(int) fast path — for |n| ≤ 4 uses direct complex multiplication
 *     (repeated squaring) instead of exp(n·log(z)), removing 4 transcendental
 *     calls for the common small-exponent case.
 *
 *  3. atan() direct closed-form — replaces the complex-division + complex-log
 *     chain with two scalar expressions derived directly from the components:
 *       Re = 0.5 · atan2(2x,  1 − x² − y²)
 *       Im = 0.25 · log((x²+(y+1)²) / (x²+(y−1)²))
 *     Cost: 1 atan2 + 1 log (no sqrt, no complex division).
 */
public class ComplexOpt2 {

    double x, y;

    public ComplexOpt2() { x = Double.NaN; y = Double.NaN; }
    public ComplexOpt2(ComplexOpt2 z) { x = z.x; y = z.y; }
    public ComplexOpt2(double x) { this.x = x; y = 0.0; }
    public ComplexOpt2(double x, double y) { this.x = x; this.y = y; }

    public ComplexOpt2 polar() {
        double lx = this.x, ly = this.y;
        return new ComplexOpt2(Math.sqrt(lx*lx + ly*ly), Math.atan2(ly, lx));
    }

    public ComplexOpt2 cartesian() {
        return new ComplexOpt2(this.x * Math.cos(this.y), this.x * Math.sin(this.y));
    }

    public double real()      { return this.x; }
    public double imaginary() { return this.y; }

    public double magnitude() { double lx=this.x, ly=this.y; return Math.sqrt(lx*lx+ly*ly); }
    public double argument()  { return Math.atan2(this.y, this.x); }

    public ComplexOpt2 add(ComplexOpt2 z)      { return new ComplexOpt2(this.x+z.x, this.y+z.y); }
    public ComplexOpt2 add(double d)           { return new ComplexOpt2(this.x+d, this.y); }
    public ComplexOpt2 subtract(ComplexOpt2 z) { return new ComplexOpt2(this.x-z.x, this.y-z.y); }
    public ComplexOpt2 subtract(double d)      { return new ComplexOpt2(this.x-d, this.y); }
    public ComplexOpt2 negate()                { return new ComplexOpt2(-this.x, -this.y); }

    public ComplexOpt2 multiply(ComplexOpt2 z) {
        return new ComplexOpt2(this.x*z.x - this.y*z.y, this.x*z.y + this.y*z.x);
    }
    public ComplexOpt2 multiply(double d) { return new ComplexOpt2(this.x*d, this.y*d); }

    public ComplexOpt2 divide(ComplexOpt2 z) {
        double r = z.x*z.x + z.y*z.y;
        return new ComplexOpt2((this.x*z.x+this.y*z.y)/r, (this.y*z.x-this.x*z.y)/r);
    }
    public ComplexOpt2 divide(double d) { return new ComplexOpt2(this.x/d, this.y/d); }

    public ComplexOpt2 invert() {
        double lx=this.x, ly=this.y, r=lx*lx+ly*ly;
        return new ComplexOpt2(lx/r, -ly/r);
    }

    public ComplexOpt2 conjugate() { return new ComplexOpt2(this.x, -this.y); }

    public double abs() { double lx=this.x, ly=this.y; return Math.sqrt(lx*lx+ly*ly); }

    public static ComplexOpt2 parseComplex(String s) {
        int from = s.indexOf('(');
        if (from == -1) return null;
        int to = s.indexOf(',', from);
        double x = Double.parseDouble(s.substring(from+1, to));
        from = to;
        to = s.indexOf(')', from);
        double y = Double.parseDouble(s.substring(from+1, to));
        return new ComplexOpt2(x, y);
    }

    public ComplexOpt2 exp() {
        double exp_x = Math.exp(this.x);
        return new ComplexOpt2(exp_x * Math.cos(this.y), exp_x * Math.sin(this.y));
    }

    /** Opt 1: log(sqrt(r²)) → 0.5*log(r²), eliminating one sqrt. */
    public ComplexOpt2 log() {
        double lx=this.x, ly=this.y;
        double ipart = Math.atan2(ly, lx);
        if (ipart > Math.PI) ipart = ipart - 2.0*Math.PI;
        return new ComplexOpt2(0.5 * Math.log(lx*lx + ly*ly), ipart);
    }

    public ComplexOpt2 sqrt() {
        double lx=this.x, ly=this.y;
        double r = Math.sqrt(lx*lx + ly*ly);
        double rpart = Math.sqrt(0.5*(r+lx));
        double ipart = Math.sqrt(0.5*(r-lx));
        if (ly < 0.0) ipart = -ipart;
        return new ComplexOpt2(rpart, ipart);
    }

    /** Opt 1 applied: sqrt removed from log. */
    public ComplexOpt2 pow(ComplexOpt2 z) {
        double lx=this.x, ly=this.y;
        double log_ipart = Math.atan2(ly, lx);
        if (log_ipart > Math.PI) log_ipart = log_ipart - 2.0*Math.PI;
        double log_x = 0.5 * Math.log(lx*lx + ly*ly);
        double log_y = log_ipart;
        double a_x = z.x*log_x - z.y*log_y;
        double a_y = z.x*log_y + z.y*log_x;
        double exp_ax = Math.exp(a_x);
        return new ComplexOpt2(exp_ax*Math.cos(a_y), exp_ax*Math.sin(a_y));
    }

    /** Opt 1 applied: sqrt removed from log. */
    public ComplexOpt2 pow(double d) {
        double lx=this.x, ly=this.y;
        double log_ipart = Math.atan2(ly, lx);
        if (log_ipart > Math.PI) log_ipart = log_ipart - 2.0*Math.PI;
        double a_x = 0.5 * Math.log(lx*lx + ly*ly) * d;
        double a_y = log_ipart * d;
        double exp_ax = Math.exp(a_x);
        return new ComplexOpt2(exp_ax*Math.cos(a_y), exp_ax*Math.sin(a_y));
    }

    /**
     * Opt 1 + Opt 2: fast path for small |n| avoids all transcendentals;
     * falls through to exp/log (with sqrt removed) for larger exponents.
     */
    public ComplexOpt2 pow(int d) {
        double lx=this.x, ly=this.y;
        switch (d) {
            case 0: return new ComplexOpt2(1.0, 0.0);
            case 1: return new ComplexOpt2(lx, ly);
            case -1: { double r=lx*lx+ly*ly; return new ComplexOpt2(lx/r, -ly/r); }
            case 2: return new ComplexOpt2(lx*lx - ly*ly, 2.0*lx*ly);
            case -2: { double x2=lx*lx-ly*ly, y2=2.0*lx*ly, r=x2*x2+y2*y2;
                       return new ComplexOpt2(x2/r, -y2/r); }
            case 3: { double x2=lx*lx-ly*ly, y2=2.0*lx*ly;
                      return new ComplexOpt2(x2*lx-y2*ly, x2*ly+y2*lx); }
            case 4: { double x2=lx*lx-ly*ly, y2=2.0*lx*ly;
                      return new ComplexOpt2(x2*x2-y2*y2, 2.0*x2*y2); }
            default: {
                double log_ipart = Math.atan2(ly, lx);
                if (log_ipart > Math.PI) log_ipart = log_ipart - 2.0*Math.PI;
                double a_x = 0.5 * Math.log(lx*lx + ly*ly) * d;
                double a_y = log_ipart * d;
                double exp_ax = Math.exp(a_x);
                return new ComplexOpt2(exp_ax*Math.cos(a_y), exp_ax*Math.sin(a_y));
            }
        }
    }

    /**
     * Opt 3: direct closed-form avoids complex division entirely.
     *   Re = 0.5 * atan2(2x, 1 − x² − y²)
     *   Im = 0.25 * log((x²+(y+1)²) / (x²+(y−1)²))
     * Transcendental cost: 1 atan2 + 1 log (was: 1 sqrt + 1 log + 1 atan2).
     */
    public ComplexOpt2 atan() {
        double lx=this.x, ly=this.y;
        double x2 = lx*lx, y2 = ly*ly;
        double yp1 = ly+1.0, ym1 = ly-1.0;
        double re = 0.5  * Math.atan2(2.0*lx, 1.0 - x2 - y2);
        double im = 0.25 * Math.log((x2 + yp1*yp1) / (x2 + ym1*ym1));
        return new ComplexOpt2(re, im);
    }

    /**
     * Opt 1 applied to both inner log calls: two sqrts removed.
     *   atanh(z) = [log(1+z) − log(1−z)] / 2
     */
    public ComplexOpt2 atanh() {
        double lx=this.x, ly=this.y;
        double add1_x=lx+1.0, add1_y=ly;
        double log1_ipart = Math.atan2(add1_y, add1_x);
        if (log1_ipart > Math.PI) log1_ipart = log1_ipart - 2.0*Math.PI;
        double log1_x = 0.5 * Math.log(add1_x*add1_x + add1_y*add1_y);
        double neg_x=1.0-lx, neg_y=-ly;
        double log2_ipart = Math.atan2(neg_y, neg_x);
        if (log2_ipart > Math.PI) log2_ipart = log2_ipart - 2.0*Math.PI;
        double log2_x = 0.5 * Math.log(neg_x*neg_x + neg_y*neg_y);
        return new ComplexOpt2((log1_x-log2_x)/2.0, (log1_ipart-log2_ipart)/2.0);
    }
}
