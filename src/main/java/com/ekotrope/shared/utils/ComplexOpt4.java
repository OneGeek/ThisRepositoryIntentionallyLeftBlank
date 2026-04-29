package com.ekotrope.shared.utils;

/**
 * Fourth-generation optimised Complex, building on ComplexOpt3.
 *
 * Changes vs ComplexOpt3
 * ──────────────────────
 * 1. pow(double) integer fast path — if d is an integer-valued double in
 *    [-100, 100] delegates to binary exponentiation (zero transcendentals).
 *    pow(7.0) now costs the same as pow(7): 8 complex multiplies.
 *
 * 2. log() near the unit circle — when r² is close to 1 (|r²-1| < 0.5)
 *    uses 0.5·log1p((x-1)·(x+1)+y²) instead of 0.5·log(x²+y²).
 *    Reason: the expression (x-1)·(x+1)+y² computes r²-1 without cancellation
 *    (no x² subtraction near 1), and log1p is accurate to 0.5 ULP near 0 while
 *    log(1+ε) can lose 1–2 ULPs when ε is tiny.  Falls back to log(r²) for
 *    larger magnitudes where the benefit is negligible.
 *
 * 3. pow(Complex) real-exponent shortcut — if z.y == 0.0 delegates to
 *    pow(z.x), saving the full log + complex multiply + exp + 2 trig calls.
 *
 * 4. Dead branch removal in log() — Math.atan2 always returns in (-π, π],
 *    so the inherited `if (ip > Math.PI)` guard was unreachable.  Removed.
 */
public class ComplexOpt4 {

    double x, y;

    public ComplexOpt4() { x = Double.NaN; y = Double.NaN; }
    public ComplexOpt4(ComplexOpt4 z) { x = z.x; y = z.y; }
    public ComplexOpt4(double x) { this.x = x; y = 0.0; }
    public ComplexOpt4(double x, double y) { this.x = x; this.y = y; }

    public ComplexOpt4 polar() {
        double lx=this.x, ly=this.y;
        return new ComplexOpt4(Math.sqrt(lx*lx+ly*ly), Math.atan2(ly,lx));
    }
    public ComplexOpt4 cartesian() {
        return new ComplexOpt4(this.x*Math.cos(this.y), this.x*Math.sin(this.y));
    }

    public double real()      { return this.x; }
    public double imaginary() { return this.y; }
    public double magnitude() { double lx=this.x,ly=this.y; return Math.sqrt(lx*lx+ly*ly); }
    public double argument()  { return Math.atan2(this.y, this.x); }

    public ComplexOpt4 add(ComplexOpt4 z)      { return new ComplexOpt4(this.x+z.x, this.y+z.y); }
    public ComplexOpt4 add(double d)           { return new ComplexOpt4(this.x+d, this.y); }
    public ComplexOpt4 subtract(ComplexOpt4 z) { return new ComplexOpt4(this.x-z.x, this.y-z.y); }
    public ComplexOpt4 subtract(double d)      { return new ComplexOpt4(this.x-d, this.y); }
    public ComplexOpt4 negate()                { return new ComplexOpt4(-this.x, -this.y); }

    public ComplexOpt4 multiply(ComplexOpt4 z) {
        return new ComplexOpt4(this.x*z.x-this.y*z.y, this.x*z.y+this.y*z.x);
    }
    public ComplexOpt4 multiply(double d) { return new ComplexOpt4(this.x*d, this.y*d); }

    public ComplexOpt4 divide(ComplexOpt4 z) {
        double r=z.x*z.x+z.y*z.y;
        return new ComplexOpt4((this.x*z.x+this.y*z.y)/r, (this.y*z.x-this.x*z.y)/r);
    }
    public ComplexOpt4 divide(double d) { return new ComplexOpt4(this.x/d, this.y/d); }

    public ComplexOpt4 invert() {
        double lx=this.x,ly=this.y,r=lx*lx+ly*ly;
        return new ComplexOpt4(lx/r, -ly/r);
    }
    public ComplexOpt4 conjugate() { return new ComplexOpt4(this.x, -this.y); }
    public double abs() { double lx=this.x,ly=this.y; return Math.sqrt(lx*lx+ly*ly); }

    public static ComplexOpt4 parseComplex(String s) {
        int from=s.indexOf('(');
        if(from==-1) return null;
        int to=s.indexOf(',',from);
        double x=Double.parseDouble(s.substring(from+1,to));
        from=to; to=s.indexOf(')',from);
        double y=Double.parseDouble(s.substring(from+1,to));
        return new ComplexOpt4(x,y);
    }

    public ComplexOpt4 exp() {
        double ex=Math.exp(this.x);
        return new ComplexOpt4(ex*Math.cos(this.y), ex*Math.sin(this.y));
    }

    /**
     * Opt 4: near unit circle uses log1p((x-1)*(x+1)+y²) to avoid cancellation.
     * Math.atan2 returns in (-π,π] so the ip>π guard is dropped.
     */
    public ComplexOpt4 log() {
        double lx=this.x, ly=this.y;
        double r2m1=(lx-1.0)*(lx+1.0)+ly*ly;   // x²+y²-1, stable near unit circle
        double re = Math.abs(r2m1) < 0.5
            ? 0.5 * Math.log1p(r2m1)
            : 0.5 * Math.log(lx*lx+ly*ly);
        return new ComplexOpt4(re, Math.atan2(ly, lx));
    }

    public ComplexOpt4 sqrt() {
        double lx=this.x, ly=this.y;
        double r=Math.sqrt(lx*lx+ly*ly);
        double rp=Math.sqrt(0.5*(r+lx));
        double ip=Math.sqrt(0.5*(r-lx));
        if(ly<0.0) ip=-ip;
        return new ComplexOpt4(rp,ip);
    }

    /**
     * Opt 4: if exponent is real (z.y==0) delegates to pow(double), saving
     * the complex-multiply of the logarithm and the final exp+2·trig.
     */
    public ComplexOpt4 pow(ComplexOpt4 z) {
        if (z.y == 0.0) return pow(z.x);
        double lx=this.x, ly=this.y;
        double lip=Math.atan2(ly,lx);
        double lx2, ly2;
        double r2m1=(lx-1.0)*(lx+1.0)+ly*ly;
        lx2 = Math.abs(r2m1) < 0.5 ? 0.5*Math.log1p(r2m1) : 0.5*Math.log(lx*lx+ly*ly);
        ly2 = lip;
        double ax=z.x*lx2-z.y*ly2, ay=z.x*ly2+z.y*lx2;
        double eax=Math.exp(ax);
        return new ComplexOpt4(eax*Math.cos(ay), eax*Math.sin(ay));
    }

    /**
     * Opt 4: integer fast path extended to double — if d is an integer in
     * [-100,100] delegates to binary exponentiation (zero transcendentals).
     */
    public ComplexOpt4 pow(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) {
            long n = (long) d;
            if (n >= -100 && n <= 100) return pow((int) n);
        }
        double lx=this.x, ly=this.y;
        double lip=Math.atan2(ly,lx);
        double r2m1=(lx-1.0)*(lx+1.0)+ly*ly;
        double ax = (Math.abs(r2m1)<0.5 ? 0.5*Math.log1p(r2m1) : 0.5*Math.log(lx*lx+ly*ly)) * d;
        double ay  = lip*d;
        double eax = Math.exp(ax);
        return new ComplexOpt4(eax*Math.cos(ay), eax*Math.sin(ay));
    }

    /** Binary exponentiation — zero transcendental calls for |d| ≤ 100. */
    public ComplexOpt4 pow(int d) {
        if (d == 0) return new ComplexOpt4(1.0, 0.0);
        int n = d < 0 ? -d : d;
        double lx=this.x, ly=this.y;
        if (n > 100) {
            double lip=Math.atan2(ly,lx);
            double r2m1=(lx-1.0)*(lx+1.0)+ly*ly;
            double ax=(Math.abs(r2m1)<0.5?0.5*Math.log1p(r2m1):0.5*Math.log(lx*lx+ly*ly))*d;
            double ay=lip*d;
            double eax=Math.exp(ax);
            return new ComplexOpt4(eax*Math.cos(ay), eax*Math.sin(ay));
        }
        double rx=1.0, ry=0.0, bx=lx, by=ly;
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
            return new ComplexOpt4(rx/r, -ry/r);
        }
        return new ComplexOpt4(rx, ry);
    }

    /** atan: Re = 0.5·atan2(2x,1−x²−y²), Im = 0.25·log1p(4y/(x²+(y−1)²)). */
    public ComplexOpt4 atan() {
        double lx=this.x, ly=this.y;
        double x2=lx*lx, ym1=ly-1.0;
        double re=0.5*Math.atan2(2.0*lx, 1.0-x2-ly*ly);
        double im=0.25*Math.log1p(4.0*ly/(x2+ym1*ym1));
        return new ComplexOpt4(re, im);
    }

    /** atanh: Re = 0.25·log1p(4a/((1−a)²+b²)), Im = 0.5·(atan2(b,1+a)+atan2(b,1−a)). */
    public ComplexOpt4 atanh() {
        double lx=this.x, ly=this.y;
        double d=(1.0-lx)*(1.0-lx)+ly*ly;
        double re=0.25*Math.log1p(4.0*lx/d);
        double im=0.5*(Math.atan2(ly, 1.0+lx)+Math.atan2(ly, 1.0-lx));
        return new ComplexOpt4(re, im);
    }
}
