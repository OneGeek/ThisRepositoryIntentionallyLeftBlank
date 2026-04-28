package com.ekotrope.shared.utils;

/**
 * Third-generation optimised Complex, building on ComplexOpt2.
 *
 * Changes vs ComplexOpt2
 * ──────────────────────
 * 1. atan() Im — replaced log(N/D) with log1p(4y/denom):
 *       Im = 0.25 · log((x²+(y+1)²)/(x²+(y−1)²))
 *          = 0.25 · log1p(4y / (x²+(y−1)²))
 *    Eliminates cancellation when |z|>>1 (where N≈D≈x² → N/D≈1 loses bits).
 *
 * 2. atanh() rewritten with log1p:
 *       Re = 0.25 · log1p(4a / ((1−a)²+b²))     [was log(sqrt(…)) − log(sqrt(…))]
 *       Im = 0.5  · (atan2(b,1+a) + atan2(b,1−a))
 *    Benefits: (a) log1p is accurate near the origin, eliminating the ~35 ULP
 *    cancellation seen in opt2; (b) saves one transcendental call (1 log1p + 2
 *    atan2 vs 2 log + 2 atan2 in opt2).
 *
 * 3. pow(int) — full binary exponentiation for all n:
 *    Uses repeated squaring, zero transcendental calls for any integer exponent.
 *    Falls back to exp/log only for |n| > 100 to bound multiplication count.
 */
public class ComplexOpt3 {

    double x, y;

    public ComplexOpt3() { x = Double.NaN; y = Double.NaN; }
    public ComplexOpt3(ComplexOpt3 z) { x = z.x; y = z.y; }
    public ComplexOpt3(double x) { this.x = x; y = 0.0; }
    public ComplexOpt3(double x, double y) { this.x = x; this.y = y; }

    public ComplexOpt3 polar() {
        double lx=this.x, ly=this.y;
        return new ComplexOpt3(Math.sqrt(lx*lx+ly*ly), Math.atan2(ly,lx));
    }
    public ComplexOpt3 cartesian() {
        return new ComplexOpt3(this.x*Math.cos(this.y), this.x*Math.sin(this.y));
    }

    public double real()      { return this.x; }
    public double imaginary() { return this.y; }
    public double magnitude() { double lx=this.x,ly=this.y; return Math.sqrt(lx*lx+ly*ly); }
    public double argument()  { return Math.atan2(this.y, this.x); }

    public ComplexOpt3 add(ComplexOpt3 z)      { return new ComplexOpt3(this.x+z.x, this.y+z.y); }
    public ComplexOpt3 add(double d)           { return new ComplexOpt3(this.x+d, this.y); }
    public ComplexOpt3 subtract(ComplexOpt3 z) { return new ComplexOpt3(this.x-z.x, this.y-z.y); }
    public ComplexOpt3 subtract(double d)      { return new ComplexOpt3(this.x-d, this.y); }
    public ComplexOpt3 negate()                { return new ComplexOpt3(-this.x, -this.y); }

    public ComplexOpt3 multiply(ComplexOpt3 z) {
        return new ComplexOpt3(this.x*z.x-this.y*z.y, this.x*z.y+this.y*z.x);
    }
    public ComplexOpt3 multiply(double d) { return new ComplexOpt3(this.x*d, this.y*d); }

    public ComplexOpt3 divide(ComplexOpt3 z) {
        double r=z.x*z.x+z.y*z.y;
        return new ComplexOpt3((this.x*z.x+this.y*z.y)/r, (this.y*z.x-this.x*z.y)/r);
    }
    public ComplexOpt3 divide(double d) { return new ComplexOpt3(this.x/d, this.y/d); }

    public ComplexOpt3 invert() {
        double lx=this.x,ly=this.y,r=lx*lx+ly*ly;
        return new ComplexOpt3(lx/r, -ly/r);
    }
    public ComplexOpt3 conjugate() { return new ComplexOpt3(this.x, -this.y); }
    public double abs() { double lx=this.x,ly=this.y; return Math.sqrt(lx*lx+ly*ly); }

    public static ComplexOpt3 parseComplex(String s) {
        int from=s.indexOf('(');
        if(from==-1) return null;
        int to=s.indexOf(',',from);
        double x=Double.parseDouble(s.substring(from+1,to));
        from=to; to=s.indexOf(')',from);
        double y=Double.parseDouble(s.substring(from+1,to));
        return new ComplexOpt3(x,y);
    }

    public ComplexOpt3 exp() {
        double ex=Math.exp(this.x);
        return new ComplexOpt3(ex*Math.cos(this.y), ex*Math.sin(this.y));
    }

    /** log(sqrt(r²)) → 0.5*log(r²) from opt2. */
    public ComplexOpt3 log() {
        double lx=this.x,ly=this.y;
        double ip=Math.atan2(ly,lx);
        if(ip>Math.PI) ip=ip-2.0*Math.PI;
        return new ComplexOpt3(0.5*Math.log(lx*lx+ly*ly), ip);
    }

    public ComplexOpt3 sqrt() {
        double lx=this.x,ly=this.y;
        double r=Math.sqrt(lx*lx+ly*ly);
        double rp=Math.sqrt(0.5*(r+lx));
        double ip=Math.sqrt(0.5*(r-lx));
        if(ly<0.0) ip=-ip;
        return new ComplexOpt3(rp,ip);
    }

    /** log(sqrt(r²)) → 0.5*log(r²) from opt2. */
    public ComplexOpt3 pow(ComplexOpt3 z) {
        double lx=this.x,ly=this.y;
        double lip=Math.atan2(ly,lx);
        if(lip>Math.PI) lip=lip-2.0*Math.PI;
        double lx2=0.5*Math.log(lx*lx+ly*ly), ly2=lip;
        double ax=z.x*lx2-z.y*ly2, ay=z.x*ly2+z.y*lx2;
        double eax=Math.exp(ax);
        return new ComplexOpt3(eax*Math.cos(ay), eax*Math.sin(ay));
    }

    /** log(sqrt(r²)) → 0.5*log(r²) from opt2. */
    public ComplexOpt3 pow(double d) {
        double lx=this.x,ly=this.y;
        double lip=Math.atan2(ly,lx);
        if(lip>Math.PI) lip=lip-2.0*Math.PI;
        double ax=0.5*Math.log(lx*lx+ly*ly)*d, ay=lip*d;
        double eax=Math.exp(ax);
        return new ComplexOpt3(eax*Math.cos(ay), eax*Math.sin(ay));
    }

    /**
     * Opt 3: full binary exponentiation — zero transcendental calls for any
     * |d| ≤ 100.  Falls back to exp/log for larger exponents.
     */
    public ComplexOpt3 pow(int d) {
        if (d == 0) return new ComplexOpt3(1.0, 0.0);
        int n = d < 0 ? -d : d;
        double lx=this.x, ly=this.y;
        if (n > 100) {
            // exp/log fallback: 0.5*log(r²) from opt2
            double lip=Math.atan2(ly,lx);
            if(lip>Math.PI) lip=lip-2.0*Math.PI;
            double ax=0.5*Math.log(lx*lx+ly*ly)*d, ay=lip*d;
            double eax=Math.exp(ax);
            return new ComplexOpt3(eax*Math.cos(ay), eax*Math.sin(ay));
        }
        // Binary exponentiation: no transcendentals
        double rx=1.0, ry=0.0;
        double bx=lx, by=ly;
        while (n > 0) {
            if ((n & 1) != 0) {
                double t=rx*bx-ry*by; ry=rx*by+ry*bx; rx=t;
            }
            n >>= 1;
            if (n > 0) {
                double t=bx*bx-by*by; by=2.0*bx*by; bx=t;
            }
        }
        if (d < 0) {
            double r=rx*rx+ry*ry;
            return new ComplexOpt3(rx/r, -ry/r);
        }
        return new ComplexOpt3(rx, ry);
    }

    /**
     * Opt 3: atan direct closed-form with log1p for Im.
     *   Re = 0.5  · atan2(2x, 1−x²−y²)
     *   Im = 0.25 · log1p(4y / (x²+(y−1)²))
     * The log1p form avoids the cancellation that occurs in opt2's
     * log(N/D) when |z|>>1 (where N≈D≈x² and N/D≈1 loses all precision).
     */
    public ComplexOpt3 atan() {
        double lx=this.x, ly=this.y;
        double x2=lx*lx, ym1=ly-1.0;
        double re=0.5*Math.atan2(2.0*lx, 1.0-x2-ly*ly);
        double im=0.25*Math.log1p(4.0*ly/(x2+ym1*ym1));
        return new ComplexOpt3(re, im);
    }

    /**
     * Opt 3: atanh rewritten with log1p — fixes cancellation near origin
     * and saves one transcendental call vs opt2.
     *   Re = 0.25 · log1p(4a / ((1−a)²+b²))
     *   Im = 0.5  · (atan2(b,1+a) + atan2(b,1−a))
     *
     * Derivation:
     *   atanh(z) = [log(1+z) − log(1−z)] / 2
     *   Re = 0.25·[log((1+a)²+b²) − log((1−a)²+b²)]
     *      = 0.25·log(1 + 4a/((1−a)²+b²))
     *   Im = 0.5·[arg(1+z) − arg(1−z)] = 0.5·[atan2(b,1+a) + atan2(b,1−a)]
     *        (since arg(1−z) = atan2(−b,1−a) = −atan2(b,1−a) when 1−a>0)
     */
    public ComplexOpt3 atanh() {
        double lx=this.x, ly=this.y;
        double d=(1.0-lx)*(1.0-lx)+ly*ly;
        double re=0.25*Math.log1p(4.0*lx/d);
        double im=0.5*(Math.atan2(ly, 1.0+lx)+Math.atan2(ly, 1.0-lx));
        return new ComplexOpt3(re, im);
    }
}
