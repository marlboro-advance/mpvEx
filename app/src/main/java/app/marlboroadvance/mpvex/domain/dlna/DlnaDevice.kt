package app.marlboroadvance.mpvex.domain.dlna

/**
 * A discovered UPnP/DLNA media server. Ephemeral: lives only in memory
 * while the UPnP service is bound, never persisted.
 */
data class DlnaDevice(
  val udn: String,
  val friendlyName: String,
  val host: String,
  val manufacturer: String,
  val modelName: String,
)
