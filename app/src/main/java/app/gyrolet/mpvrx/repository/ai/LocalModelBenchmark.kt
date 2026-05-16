package app.gyrolet.mpvrx.repository.ai

import kotlinx.serialization.Serializable

@Serializable
data class LocalModelBenchmark(
  val modelId: String,
  val loadMs: Long,
  val tokensPerSecond: Float,
  val memoryEstimateMb: Int,
  val benchmarkedAtMs: Long,
) {
  val loadLabel: String
    get() = if (loadMs >= 1000) {
      "%.1fs load".format(loadMs / 1000f)
    } else {
      "${loadMs}ms load"
    }

  val speedLabel: String
    get() = "%.1f tok/s".format(tokensPerSecond)
}
