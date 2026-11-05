---
name: benchmarking
description: Write and run JMH (Java Microbenchmark Harness) micro-benchmarks to get real before/after numbers for CPU- or allocation-bound code in WM-Admin-Portal-BE. Use when a change is claimed to be faster without evidence, or when comparing two implementations of a parser, mapper, or serializer. Do not use for endpoint latency dominated by database round trips — profile that with SQL statement logging first.
user-invocable: true
license: MIT
metadata:
  author: WM-Admin-Portal-BE
  version: "1.0.0"
  domain: performance
  triggers: benchmark, JMH, measure performance, faster, allocations, GC pressure, micro-benchmark, before and after, throughput
  role: performance-engineer
  scope: measurement
  output-format: java-examples
  related-skills: coding-standards, integration-testing
---

# Benchmarking — WM-Admin-Portal-BE

A dormant reference skill. The current work is feature and revamp work, not
performance tuning, so there's nothing to benchmark yet. Load this when a specific change needs
measured evidence, not as routine practice.

## First: Is JMH the Right Tool?

**JMH measures in-process CPU/allocation work.** It's the wrong tool for a slow endpoint that's
actually waiting on the database or an external call.

| Symptom | Tool |
|---|---|
| Endpoint slow, many DB queries per request | Enable `spring.jpa.show-sql`/statement logging, count round trips per request |
| Endpoint slow, one query, big result | `EXPLAIN (ANALYZE, BUFFERS)`, check indexes |
| Cold-start spike, warm requests fine | Caching |
| Same input/output, measurably CPU-hot | **JMH** |
| High GC pressure, large allocations | **JMH** with `-prof gc` |

Rule out round trips and payload size before reaching for a micro-benchmark. A benchmark of
in-process code cannot detect a chatty N+1 query pattern — that's a job for
`integration-testing`/statement-count assertions instead.

## Where It Genuinely Applies Here

Code in this repo that's CPU/allocation bound and worth benchmarking if a change claims to speed it
up:

- **Document generation** — Apache POI (Excel) report generation
- **Mapper chains** — the hand-written `CareersDetailsMapper` and `ApplicantDetailsMapper` on big nested DTOs
- **JWT token parsing** — `jjwt` claims parsing under very high request volume
- **Email template rendering** — placeholder replacement and HTML escaping in `CreateEmailTemplateImpl`

## Project Setup

**This repo is currently a single Maven module** (`groupId com.wealthmax`, `artifactId
AdminPortal`, `packaging jar` — not a multi-module parent). JMH needs its own module so it
can fork a clean JVM with no Spring context attached; before the first benchmark, add a sibling
`benchmarks/pom.xml`:

```xml
<project>
    <parent>
        <groupId>com.wealthmax</groupId>
        <artifactId>AdminPortal</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>benchmarks</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>org.openjdk.jmh</groupId>
            <artifactId>jmh-core</artifactId>
            <version>1.37</version>
        </dependency>
        <dependency>
            <groupId>org.openjdk.jmh</groupId>
            <artifactId>jmh-generator-annprocess</artifactId>
            <version>1.37</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>com.wealthmax</groupId>
            <artifactId>AdminPortal</artifactId>
            <version>0.0.1-SNAPSHOT</version>
        </dependency>
    </dependencies>
</project>
```

Since the root `pom.xml` doesn't currently declare a `<modules>` list (it's a standalone `jar`
project, not a multi-module parent), the first benchmark also needs one of: (a) add
`<modules><module>benchmarks</module></modules>` to the root POM, or (b) build the benchmarks
module standalone with `mvn -f benchmarks/pom.xml package`, referencing the parent via
`<relativePath>` without registering it as a module of the root build. Option (b) is less invasive
if benchmarking stays occasional — it doesn't change how `mvn compile`/`mvn test` behave for the
main module.

## Writing a Benchmark

```java
package com.wealthmax.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@State(Scope.Thread)
public class CareersMapperBenchmark {

    private List<CareersINEntity> jobs;
    private CareersDetailsMapper mapperCurrent;

    @Setup(Level.Trial)
    public void setup() {
        // Build input once — setup cost must not land inside the measurement.
        jobs = TestFixtures.jobs(500);   // a helper you write: 500 fully populated entities
        mapperCurrent = new CareersDetailsMapper();
    }

    @Benchmark
    @OperationsPerInvocation(500)
    public void current(Blackhole bh) {
        for (CareersINEntity job : jobs) {
            bh.consume(mapperCurrent.toResponse(job)); // prevents dead-code elimination
        }
    }
}
```

Rules that make the numbers real:
1. Always pass results to `Blackhole.consume()`.
2. Build input in `@Setup(Level.Trial)`, never inside the benchmark method.
3. Use `@Param({...})` when behaviour changes with input size.
4. `@Fork(1)` minimum — sharing a JVM with a prior test contaminates the JIT profile.
5. `@Warmup(iterations = 3)` — let JIT compilation stabilise before measuring.

## Running

```bash
mvn -f benchmarks/pom.xml package -DskipTests
java -jar benchmarks/target/benchmarks.jar
java -jar benchmarks/target/benchmarks.jar CareersMapperBenchmark   # a specific class
java -jar benchmarks/target/benchmarks.jar -l                    # list available benchmarks
```

Close other applications first — a run on a machine that's also compiling produces noise, not data.

## Recording the Result

Write the result to `docs/performance/<area>-<symptom>.md` with a row added to
`docs/performance/INDEX.md` in the same commit (a new convention here: create the folder and the
`docs/performance/INDEX.md`). Required sections: Symptom, Investigation method, What was ruled out,
Fix, Results (real before/after numbers), Detection. If the change also warrants a
patch summary (see `executing-plans`), that's a separate
file — the performance write-up is the measurement record, the patch summary is the "what shipped."

## Gotchas

1. **Never benchmark inside a test run.** JMH must run standalone with `java -jar` — Spring test
   context startup (and the local PostgreSQL test setup) would contaminate JIT state.
2. **Don't benchmark anything touching the database, network, or filesystem.** Those are round
   trips — profile them with statement logging instead.
3. **Don't compare runs across machines.** Only ratios within a single run are trustworthy.
4. **Don't trust a run with high standard deviation** — JMH warning about a multimodal distribution
   means a noisy environment; rerun on a quiet machine.
5. **Don't add benchmarks to the CI pipeline.** A hosted CI agent is too noisy for reliable
   timing. Run locally.
6. **Don't benchmark something you haven't profiled.** Confirm the code is actually hot first, or
   you'll optimise something that costs a fraction of a percent of the request.
