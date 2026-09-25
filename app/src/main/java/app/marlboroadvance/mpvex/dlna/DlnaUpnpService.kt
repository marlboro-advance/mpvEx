package app.marlboroadvance.mpvex.dlna

import org.jupnp.UpnpServiceConfiguration
import org.jupnp.android.AndroidUpnpServiceConfiguration
import org.jupnp.android.AndroidUpnpServiceImpl

class DlnaUpnpService : AndroidUpnpServiceImpl() {
  override fun createConfiguration(): UpnpServiceConfiguration =
    object : AndroidUpnpServiceConfiguration() {
      // Registry maintenance every 7s instead of the 3s Android default; devices
      // without a ContentDirectory service are filtered (with logging) in
      // DlnaRepository's registry listener
      override fun getRegistryMaintenanceIntervalMillis(): Int = 7000
    }
}
