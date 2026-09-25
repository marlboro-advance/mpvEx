package app.marlboroadvance.mpvex.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import app.marlboroadvance.mpvex.dlna.DlnaUpnpService
import app.marlboroadvance.mpvex.domain.dlna.DlnaDevice
import app.marlboroadvance.mpvex.domain.network.NetworkFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jupnp.android.AndroidUpnpService
import org.jupnp.model.action.ActionInvocation
import org.jupnp.model.message.UpnpResponse
import org.jupnp.model.meta.RemoteDevice
import org.jupnp.model.meta.Service
import org.jupnp.model.types.UDAServiceType
import org.jupnp.model.types.UDN
import org.jupnp.registry.DefaultRegistryListener
import org.jupnp.registry.Registry
import org.jupnp.support.contentdirectory.callback.Browse
import org.jupnp.support.model.BrowseFlag
import org.jupnp.support.model.BrowseResult
import org.jupnp.support.model.DIDLContent
import org.jupnp.support.model.item.Item
import org.jupnp.support.model.item.VideoItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Manages the UPnP service lifecycle and the ephemeral list of discovered DLNA
 * media servers. Devices live only in memory while the service is bound; leaving
 * all DLNA-related screens unbinds the service and clears the list.
 */
class DlnaRepository(private val context: Context) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  companion object {
    private const val TAG = "DlnaRepository"
    private val contentDirectoryType = UDAServiceType("ContentDirectory")
  }

  private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
  val devices: StateFlow<List<DlnaDevice>> = _devices.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  // Mutated from jUPnP registry threads
  private val deviceMap = ConcurrentHashMap<String, DlnaDevice>()

  @Volatile private var binder: AndroidUpnpService? = null
  @Volatile private var bound = false
  private var pendingBinder = CompletableDeferred<AndroidUpnpService>()
  private var refCount = 0
  private val bindLock = Any()
  private var scanJob: Job? = null

  private val registryListener = object : DefaultRegistryListener() {
    override fun remoteDeviceAdded(registry: Registry, device: RemoteDevice) {
      val dlna = toDlnaDevice(device)
      if (dlna == null) {
        Log.d(TAG, "Discovered UPnP device '${deviceDetailsName(device)}' — no ContentDirectory service, hidden")
      } else {
        Log.i(TAG, "DLNA media server discovered: '${dlna.friendlyName}' (${dlna.host})")
      }
      dlna?.let { deviceMap[it.udn] = it }
      publishDevices()
    }

    override fun remoteDeviceUpdated(registry: Registry, device: RemoteDevice) {
      toDlnaDevice(device)?.let { deviceMap[it.udn] = it }
      publishDevices()
    }

    override fun remoteDeviceRemoved(registry: Registry, device: RemoteDevice) {
      Log.d(TAG, "UPnP device left: '${deviceDetailsName(device)}'")
      deviceMap.remove(device.identity.udn.toString())
      publishDevices()
    }

    override fun remoteDeviceDiscoveryFailed(registry: Registry, device: RemoteDevice, ex: Exception) {
      Log.w(TAG, "Discovery failed for '${deviceDetailsName(device)}': ${ex.message}")
      deviceMap.remove(device.identity.udn.toString())
      publishDevices()
    }
  }

  private fun publishDevices() {
    _devices.value = deviceMap.values.sortedBy { it.friendlyName.lowercase() }
  }

  private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, service: IBinder) {
      if (!bound) return
      val upnp = service as AndroidUpnpService
      binder = upnp
      upnp.registry.addListener(registryListener)
      upnp.registry.remoteDevices.forEach { device ->
        toDlnaDevice(device)?.let { deviceMap[it.udn] = it }
      }
      publishDevices()
      pendingBinder.complete(upnp)
    }

    override fun onServiceDisconnected(name: ComponentName) {
      binder = null
    }
  }

  /** Call from a DisposableEffect; first retain binds the UPnP service. */
  fun retain() {
    synchronized(bindLock) {
      refCount++
      if (refCount == 1) bind()
    }
  }

  /** Last release unbinds the service and clears the ephemeral device list. */
  fun release() {
    synchronized(bindLock) {
      refCount = (refCount - 1).coerceAtLeast(0)
      if (refCount == 0) unbind()
    }
  }

  private fun bind() {
    bound = true
    pendingBinder = CompletableDeferred()
    context.bindService(
      Intent(context, DlnaUpnpService::class.java),
      serviceConnection,
      Context.BIND_AUTO_CREATE,
    )
  }

  private fun unbind() {
    bound = false
    scanJob?.cancel()
    scanJob = null
    binder?.let { upnp ->
      try {
        upnp.registry.removeListener(registryListener)
      } catch (_: Exception) {
      }
    }
    binder = null
    try {
      context.unbindService(serviceConnection)
    } catch (_: Exception) {
    }
    deviceMap.clear()
    publishDevices()
    _isScanning.value = false
  }

  private suspend fun awaitService(): AndroidUpnpService {
    binder?.let { return it }
    val pending = synchronized(bindLock) {
      if (!bound) throw IllegalStateException("DLNA service is not bound")
      pendingBinder
    }
    return try {
      withTimeout(5_000) { pending.await() }
    } catch (e: TimeoutCancellationException) {
      throw IllegalStateException("Timed out waiting for the DLNA service to bind", e)
    }
  }

  /** Trigger SSDP discovery. Devices appear in [devices] as they hydrate. */
  fun scan() {
    // A re-scan owns the indicator; cancelling the previous job keeps its
    // trailing reset from clearing the new scan's state early
    scanJob?.cancel()
    scanJob = scope.launch {
      val upnp =
        try {
          awaitService()
        } catch (e: Exception) {
          Log.e(TAG, "Failed to bind UPnP service for scan", e)
          _isScanning.value = false
          return@launch
        }
      Log.i(TAG, "Starting SSDP scan")
      _isScanning.value = true
      upnp.registry.removeAllRemoteDevices()
      upnp.controlPoint.search()
      // Allow SSDP responses and device hydration to land before clearing the indicator
      delay(12_000)
      Log.i(TAG, "Scan finished, ${deviceMap.size} media server(s) visible")
      _isScanning.value = false
    }
  }

  /**
   * Browse a ContentDirectory. [objectId] is the UPnP ObjectID ("0" is the root);
   * the returned [NetworkFile.path] carries ObjectIDs so folders can be re-browsed.
   */
  suspend fun browse(deviceUdn: String, objectId: String): Result<List<NetworkFile>> =
    withContext(Dispatchers.IO) {
      try {
        val upnp = awaitService()
        val device =
          upnp.registry.getRemoteDevice(UDN.valueOf(deviceUdn), false)
            ?: throw IllegalStateException("Device not found — scan again")
        val service =
          findContentDirectory(device)
            ?: throw IllegalStateException("Device has no ContentDirectory service")
        Result.success(browseAll(upnp, service, objectId))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  /**
   * Page through a container until the server signals the end. Servers may cap
   * NumberReturned below the requested page size, report TotalMatches as 0
   * (unknown), or ignore StartingIndex and resend the same entries every page —
   * so progress is deduplicated by ObjectID and the loop stops when a page
   * contributes nothing new.
   */
  private suspend fun browseAll(upnp: AndroidUpnpService, service: Service<*, *>, objectId: String): List<NetworkFile> {
    val pageSize = 500L
    val maxPages = 100
    val maxFiles = 10_000
    var first = 0L
    val seenIds = HashSet<String>()
    val files = mutableListOf<NetworkFile>()
    var pages = 0
    while (true) {
      currentCoroutineContext().ensureActive()
      val page = browsePage(upnp, service, objectId, first, pageSize)
      pages++
      val newIds = page.ids.filterTo(HashSet()) { seenIds.add(it) }
      files += page.files.filter { it.path in newIds }
      if (page.ids.isEmpty() || newIds.isEmpty()) break
      // Advance by what the server actually returned — some servers cap
      // NumberReturned below the requested count
      first += if (page.numberReturned > 0) page.numberReturned else page.ids.size.toLong()
      if (page.totalMatches > 0 && first >= page.totalMatches) break
      if (files.size >= maxFiles || pages >= maxPages) {
        Log.w(TAG, "Browse of '$objectId' stopped early at ${files.size} items after $pages pages")
        break
      }
    }
    return files
  }

  /** One browse action's outcome: mapped files, raw ObjectIDs, and paging counters. */
  private class DlnaPage(
    val files: List<NetworkFile>,
    val ids: List<String>,
    val numberReturned: Long,
    val totalMatches: Long,
  )

  private class DlnaBrowse(
    service: Service<*, *>,
    objectId: String,
    first: Long,
    count: Long,
    val onReceived: (DIDLContent) -> Unit,
    val onRaw: (numberReturned: Long, totalMatches: Long) -> Unit,
    val onError: (String) -> Unit,
  ) : Browse(service, objectId, BrowseFlag.DIRECT_CHILDREN, "*", first, count) {
    override fun receivedRaw(invocation: ActionInvocation<*>, result: BrowseResult): Boolean {
      onRaw(result.countLong, result.totalMatchesLong)
      return true // default: parse DIDL and call received()
    }

    override fun received(invocation: ActionInvocation<*>, didl: DIDLContent) = onReceived(didl)

    override fun updateStatus(status: Browse.Status) {}

    override fun failure(invocation: ActionInvocation<*>, operation: UpnpResponse?, defaultMsg: String?) {
      invocation.failure?.let { Log.e(TAG, "Browse action failed", it) }
      onError(defaultMsg ?: "Browse failed")
    }
  }

  private fun browsePage(
    upnp: AndroidUpnpService,
    service: Service<*, *>,
    objectId: String,
    first: Long,
    count: Long,
  ): DlnaPage {
    var didl: DIDLContent? = null
    var failure: Exception? = null
    var numberReturned = 0L
    var totalMatches = 0L
    val browse =
      DlnaBrowse(
        service = service,
        objectId = objectId,
        first = first,
        count = count,
        onReceived = { didl = it },
        onRaw = { returned, matches ->
          numberReturned = returned
          totalMatches = matches
        },
        onError = { failure = IllegalStateException(it) },
      )
    // ActionCallbacks must run through the ControlPoint; block until the page completes
    try {
      upnp.controlPoint.execute(browse).get(20, TimeUnit.SECONDS)
    } catch (e: Exception) {
      Log.e(TAG, "Browse execution failed", e)
      throw IllegalStateException("Browse failed: ${e.cause?.message ?: e.message}")
    }
    failure?.let { throw it }
    val content = didl
    return DlnaPage(
      files = content?.let { mapDidl(it) } ?: emptyList(),
      ids = content?.let { it.containers.map { c -> c.id } + it.items.map { i -> i.id } } ?: emptyList(),
      numberReturned = numberReturned,
      totalMatches = totalMatches,
    )
  }

  private fun mapDidl(didl: DIDLContent): List<NetworkFile> {
    val folders =
      didl.containers.map { container ->
        NetworkFile(
          name = container.title ?: container.id,
          path = container.id,
          size = 0,
          isDirectory = true,
        )
      }
    val videos = didl.items.mapNotNull { item -> toItem(item) }
    return folders + videos
  }

  private fun toItem(item: Item): NetworkFile? {
    val res =
      item.resources.firstOrNull { it.value?.startsWith("http") == true }
        ?: item.firstResource
        ?: return null
    val uri = res.value ?: return null
    val mime = res.protocolInfo?.contentFormat?.trim().orEmpty()
    val isVideo = mime.startsWith("video/") || item is VideoItem
    if (!isVideo) return null
    return NetworkFile(
      name = item.title ?: uri.substringAfterLast('/'),
      path = item.id,
      size = res.size ?: 0L,
      isDirectory = false,
      mimeType = mime.ifEmpty { "video/*" },
      remoteUri = uri,
    )
  }

  /**
   * Locate the ContentDirectory service on a device. Matches by service *type*
   * (version-tolerant, standard in practice) with a serviceId fallback: some
   * servers (e.g. CyberGarage) declare non-standard serviceId strings.
   */
  private fun findContentDirectory(device: RemoteDevice): Service<*, *>? =
    device.findServices().firstOrNull { it.serviceType.implementsVersion(contentDirectoryType) }
      ?: device.findServices().firstOrNull {
        it.serviceId.toString().contains("ContentDirectory", ignoreCase = true)
      }

  private fun toDlnaDevice(device: RemoteDevice): DlnaDevice? {
    if (findContentDirectory(device) == null) return null
    return DlnaDevice(
      udn = device.identity.udn.toString(),
      friendlyName = device.details?.friendlyName ?: device.displayString,
      host = device.identity.descriptorURL?.host ?: "",
      manufacturer = device.details?.manufacturerDetails?.manufacturer ?: "",
      modelName = device.details?.modelDetails?.modelName ?: "",
    )
  }

  private fun deviceDetailsName(device: RemoteDevice): String =
    device.details?.friendlyName ?: device.displayString
}
