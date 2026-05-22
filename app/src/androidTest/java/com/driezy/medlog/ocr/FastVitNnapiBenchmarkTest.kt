package com.driezy.medlog.ocr

import android.os.Bundle
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.FloatBuffer
import java.util.EnumSet
import kotlin.math.roundToLong
import org.junit.Test

class FastVitNnapiBenchmarkTest {

    @Test
    fun benchmarkFastVitOnnxRuntimeProvider() {
        val args = InstrumentationRegistry.getArguments()
        val provider = args.getString("provider") ?: "nnapi"
        val modelAsset = args.getString("modelAsset") ?: "fastvit_t8_ctc_reparam.onnx"
        val warmup = args.getString("warmup")?.toIntOrNull() ?: 10
        val runs = args.getString("runs")?.toIntOrNull() ?: 100

        val testContext = InstrumentationRegistry.getInstrumentation().context
        val modelBytes = testContext.assets.open(modelAsset).use { it.readBytes() }
        val environment = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(1)
            if (provider == "nnapi") {
                addNnapi(EnumSet.of(NNAPIFlags.USE_NCHW))
            } else if (provider == "nnapi_cpu_disabled") {
                addNnapi(EnumSet.of(NNAPIFlags.USE_NCHW, NNAPIFlags.CPU_DISABLED))
            }
        }
        val input = FloatBuffer.allocate(1 * 3 * 128 * 256).apply {
            repeat(capacity()) { put(0.5f) }
            rewind()
        }
        val shape = longArrayOf(1, 3, 128, 256)

        environment.createSession(modelBytes, options).use { session ->
            repeat(warmup) {
                runOnce(environment, session, input, shape)
            }
            val durationsMs = LongArray(runs)
            repeat(runs) { index ->
                val started = System.nanoTime()
                runOnce(environment, session, input, shape)
                durationsMs[index] = ((System.nanoTime() - started) / 1_000_000.0).roundToLong()
            }
            durationsMs.sort()
            val meanMs = durationsMs.average()
            val p50Ms = percentile(durationsMs, 0.50)
            val p95Ms = percentile(durationsMs, 0.95)
            val throughput = if (meanMs > 0.0) 1000.0 / meanMs else 0.0
            val line = "FASTVIT_BENCHMARK provider=$provider model=$modelAsset " +
                "runs=$runs mean_ms=${"%.2f".format(meanMs)} " +
                "p50_ms=$p50Ms p95_ms=$p95Ms throughput_sps=${"%.2f".format(throughput)}"
            Log.i(TAG, line)
            InstrumentationRegistry.getInstrumentation().sendStatus(
                0,
                Bundle().apply { putString("fastvit_benchmark", line) },
            )
        }
        options.close()
    }

    private fun runOnce(
        environment: OrtEnvironment,
        session: OrtSession,
        input: FloatBuffer,
        shape: LongArray,
    ) {
        input.rewind()
        OnnxTensor.createTensor(environment, input, shape).use { tensor ->
            session.run(mapOf("input" to tensor)).use { result ->
                result[0].value
            }
        }
    }

    private fun percentile(sorted: LongArray, quantile: Double): Long {
        if (sorted.isEmpty()) return 0
        val index = ((sorted.size - 1) * quantile).roundToLong().toInt()
        return sorted[index.coerceIn(sorted.indices)]
    }

    private companion object {
        private const val TAG = "FastVitBenchmark"
    }
}
