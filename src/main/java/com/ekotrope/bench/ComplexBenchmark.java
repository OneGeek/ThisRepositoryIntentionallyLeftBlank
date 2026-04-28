package com.ekotrope.bench;

import com.ekotrope.shared.utils.Complex;
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

    // Inputs chosen to exercise all code paths without hitting NaN/Inf
    private Complex opt;
    private Complex optExp;
    private ComplexOriginal orig;
    private ComplexOriginal origExp;

    @Setup
    public void setup() {
        opt     = new Complex(1.5, 0.7);
        optExp  = new Complex(0.5, 0.3);
        orig    = new ComplexOriginal(1.5, 0.7);
        origExp = new ComplexOriginal(0.5, 0.3);
    }

    // --- atan ---

    @Benchmark
    public void orig_atan(Blackhole bh) {
        bh.consume(orig.atan());
    }

    @Benchmark
    public void opt_atan(Blackhole bh) {
        bh.consume(opt.atan());
    }

    // --- atanh ---

    @Benchmark
    public void orig_atanh(Blackhole bh) {
        bh.consume(orig.atanh());
    }

    @Benchmark
    public void opt_atanh(Blackhole bh) {
        bh.consume(opt.atanh());
    }

    // --- pow(Complex) ---

    @Benchmark
    public void orig_powComplex(Blackhole bh) {
        bh.consume(orig.pow(origExp));
    }

    @Benchmark
    public void opt_powComplex(Blackhole bh) {
        bh.consume(opt.pow(optExp));
    }

    // --- pow(double) ---

    @Benchmark
    public void orig_powDouble(Blackhole bh) {
        bh.consume(orig.pow(2.5));
    }

    @Benchmark
    public void opt_powDouble(Blackhole bh) {
        bh.consume(opt.pow(2.5));
    }

    // --- pow(int) ---

    @Benchmark
    public void orig_powInt(Blackhole bh) {
        bh.consume(orig.pow(3));
    }

    @Benchmark
    public void opt_powInt(Blackhole bh) {
        bh.consume(opt.pow(3));
    }

    // --- log (local-cache benefit) ---

    @Benchmark
    public void orig_log(Blackhole bh) {
        bh.consume(orig.log());
    }

    @Benchmark
    public void opt_log(Blackhole bh) {
        bh.consume(opt.log());
    }

    // --- sqrt (local-cache benefit) ---

    @Benchmark
    public void orig_sqrt(Blackhole bh) {
        bh.consume(orig.sqrt());
    }

    @Benchmark
    public void opt_sqrt(Blackhole bh) {
        bh.consume(opt.sqrt());
    }
}
