package com.ekotrope.bench;

import com.ekotrope.shared.utils.Complex;
import com.ekotrope.shared.utils.ComplexOpt2;
import com.ekotrope.shared.utils.ComplexOpt3;
import com.ekotrope.shared.utils.ComplexOriginal;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class ComplexBenchmark {

    private ComplexOriginal orig;
    private ComplexOriginal origExp;
    private Complex         opt1;
    private Complex         opt1Exp;
    private ComplexOpt2     opt2;
    private ComplexOpt2     opt2Exp;
    private ComplexOpt3     opt3;
    private ComplexOpt3     opt3Exp;

    @Setup
    public void setup() {
        orig    = new ComplexOriginal(1.5, 0.7);
        origExp = new ComplexOriginal(0.5, 0.3);
        opt1    = new Complex(1.5, 0.7);
        opt1Exp = new Complex(0.5, 0.3);
        opt2    = new ComplexOpt2(1.5, 0.7);
        opt2Exp = new ComplexOpt2(0.5, 0.3);
        opt3    = new ComplexOpt3(1.5, 0.7);
        opt3Exp = new ComplexOpt3(0.5, 0.3);
    }

    // ---- atan ----

    @Benchmark public void orig_atan(Blackhole bh)  { bh.consume(orig.atan()); }
    @Benchmark public void opt1_atan(Blackhole bh)  { bh.consume(opt1.atan()); }
    @Benchmark public void opt2_atan(Blackhole bh)  { bh.consume(opt2.atan()); }
    @Benchmark public void opt3_atan(Blackhole bh)  { bh.consume(opt3.atan()); }

    // ---- atanh ----

    @Benchmark public void orig_atanh(Blackhole bh) { bh.consume(orig.atanh()); }
    @Benchmark public void opt1_atanh(Blackhole bh) { bh.consume(opt1.atanh()); }
    @Benchmark public void opt2_atanh(Blackhole bh) { bh.consume(opt2.atanh()); }
    @Benchmark public void opt3_atanh(Blackhole bh) { bh.consume(opt3.atanh()); }

    // ---- log ----

    @Benchmark public void orig_log(Blackhole bh)   { bh.consume(orig.log()); }
    @Benchmark public void opt1_log(Blackhole bh)   { bh.consume(opt1.log()); }
    @Benchmark public void opt2_log(Blackhole bh)   { bh.consume(opt2.log()); }
    @Benchmark public void opt3_log(Blackhole bh)   { bh.consume(opt3.log()); }

    // ---- pow(Complex) ----

    @Benchmark public void orig_powComplex(Blackhole bh) { bh.consume(orig.pow(origExp)); }
    @Benchmark public void opt1_powComplex(Blackhole bh) { bh.consume(opt1.pow(opt1Exp)); }
    @Benchmark public void opt2_powComplex(Blackhole bh) { bh.consume(opt2.pow(opt2Exp)); }
    @Benchmark public void opt3_powComplex(Blackhole bh) { bh.consume(opt3.pow(opt3Exp)); }

    // ---- pow(double) ----

    @Benchmark public void orig_powDouble(Blackhole bh) { bh.consume(orig.pow(2.5)); }
    @Benchmark public void opt1_powDouble(Blackhole bh) { bh.consume(opt1.pow(2.5)); }
    @Benchmark public void opt2_powDouble(Blackhole bh) { bh.consume(opt2.pow(2.5)); }
    @Benchmark public void opt3_powDouble(Blackhole bh) { bh.consume(opt3.pow(2.5)); }

    // ---- pow(int n=3): fast-path in opt2/opt3, exp/log in orig/opt1 ----

    @Benchmark public void orig_powInt3(Blackhole bh) { bh.consume(orig.pow(3)); }
    @Benchmark public void opt1_powInt3(Blackhole bh) { bh.consume(opt1.pow(3)); }
    @Benchmark public void opt2_powInt3(Blackhole bh) { bh.consume(opt2.pow(3)); }
    @Benchmark public void opt3_powInt3(Blackhole bh) { bh.consume(opt3.pow(3)); }

    // ---- pow(int n=7): exp/log fallback in opt1/opt2, binary-exp in opt3 ----

    @Benchmark public void orig_powInt7(Blackhole bh) { bh.consume(orig.pow(7)); }
    @Benchmark public void opt1_powInt7(Blackhole bh) { bh.consume(opt1.pow(7)); }
    @Benchmark public void opt2_powInt7(Blackhole bh) { bh.consume(opt2.pow(7)); }
    @Benchmark public void opt3_powInt7(Blackhole bh) { bh.consume(opt3.pow(7)); }
}
