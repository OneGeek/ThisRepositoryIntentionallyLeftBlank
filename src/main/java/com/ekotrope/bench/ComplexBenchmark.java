package com.ekotrope.bench;

import com.ekotrope.shared.utils.Complex;
import com.ekotrope.shared.utils.ComplexOpt2;
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

    private Complex      opt1;
    private Complex      opt1Exp;
    private ComplexOpt2  opt2;
    private ComplexOpt2  opt2Exp;
    private ComplexOriginal orig;
    private ComplexOriginal origExp;

    @Setup
    public void setup() {
        opt1    = new Complex(1.5, 0.7);
        opt1Exp = new Complex(0.5, 0.3);
        opt2    = new ComplexOpt2(1.5, 0.7);
        opt2Exp = new ComplexOpt2(0.5, 0.3);
        orig    = new ComplexOriginal(1.5, 0.7);
        origExp = new ComplexOriginal(0.5, 0.3);
    }

    // ---- atan ----

    @Benchmark public void orig_atan(Blackhole bh)  { bh.consume(orig.atan()); }
    @Benchmark public void opt1_atan(Blackhole bh)  { bh.consume(opt1.atan()); }
    @Benchmark public void opt2_atan(Blackhole bh)  { bh.consume(opt2.atan()); }

    // ---- atanh ----

    @Benchmark public void orig_atanh(Blackhole bh) { bh.consume(orig.atanh()); }
    @Benchmark public void opt1_atanh(Blackhole bh) { bh.consume(opt1.atanh()); }
    @Benchmark public void opt2_atanh(Blackhole bh) { bh.consume(opt2.atanh()); }

    // ---- log ----

    @Benchmark public void orig_log(Blackhole bh)   { bh.consume(orig.log()); }
    @Benchmark public void opt1_log(Blackhole bh)   { bh.consume(opt1.log()); }
    @Benchmark public void opt2_log(Blackhole bh)   { bh.consume(opt2.log()); }

    // ---- pow(Complex) ----

    @Benchmark public void orig_powComplex(Blackhole bh) { bh.consume(orig.pow(origExp)); }
    @Benchmark public void opt1_powComplex(Blackhole bh) { bh.consume(opt1.pow(opt1Exp)); }
    @Benchmark public void opt2_powComplex(Blackhole bh) { bh.consume(opt2.pow(opt2Exp)); }

    // ---- pow(double) ----

    @Benchmark public void orig_powDouble(Blackhole bh) { bh.consume(orig.pow(2.5)); }
    @Benchmark public void opt1_powDouble(Blackhole bh) { bh.consume(opt1.pow(2.5)); }
    @Benchmark public void opt2_powDouble(Blackhole bh) { bh.consume(opt2.pow(2.5)); }

    // ---- pow(int) — benchmarked at n=3 (fast-path) and n=7 (fallback) ----

    @Benchmark public void orig_powInt3(Blackhole bh) { bh.consume(orig.pow(3)); }
    @Benchmark public void opt1_powInt3(Blackhole bh) { bh.consume(opt1.pow(3)); }
    @Benchmark public void opt2_powInt3(Blackhole bh) { bh.consume(opt2.pow(3)); }

    @Benchmark public void orig_powInt7(Blackhole bh) { bh.consume(orig.pow(7)); }
    @Benchmark public void opt1_powInt7(Blackhole bh) { bh.consume(opt1.pow(7)); }
    @Benchmark public void opt2_powInt7(Blackhole bh) { bh.consume(opt2.pow(7)); }
}
