package io.moka.bench

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

/** On Scala 2 every one of these folds to a single `ldc`, so all four should
  * measure the same as `baseline`. Whatever Scala 3 measures above baseline is
  * the real cost of the structural `selectDynamic` chain — which `javap` cannot
  * tell us, because it does not know what the JIT will do with it.
  */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
class PathBenchmark {

  @Benchmark
  def baseline: String = "mid.deep.d"

  @Benchmark
  def oneHop: String = Root.Fields.leaf

  @Benchmark
  def twoHops: String = Root.Fields.mid.m

  @Benchmark
  def threeHops: String = Root.Fields.mid.deep.d
}
