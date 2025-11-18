package app.marlboroadvance.mpvex.ui.browser.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.domain.network.NetworkConnection
import app.marlboroadvance.mpvex.domain.network.NetworkProtocol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddConnectionSheet(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  onSave: (NetworkConnection) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!isOpen) return

  var name by remember { mutableStateOf("") }
  var protocol by remember { mutableStateOf(NetworkProtocol.SMB) }
  var host by remember { mutableStateOf("") }
  var port by remember { mutableStateOf(protocol.defaultPort.toString()) }
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var path by remember { mutableStateOf("/") }
  var isAnonymous by remember { mutableStateOf(false) }
  var passwordVisible by remember { mutableStateOf(false) }
  var protocolMenuExpanded by remember { mutableStateOf(false) }

  // Validation states
  var hostError by remember { mutableStateOf<String?>(null) }
  var portError by remember { mutableStateOf<String?>(null) }
  var usernameError by remember { mutableStateOf<String?>(null) }
  var pathError by remember { mutableStateOf<String?>(null) }

  val handleDismiss = {
    onDismiss()
  }

  // Validation functions
  fun validateHost(value: String): String? {
    return when {
      value.isBlank() -> "Host is required"
      value.contains(" ") -> "Host cannot contain spaces"
      value.length > 253 -> "Host name too long"
      else -> null
    }
  }

  fun validatePort(value: String): String? {
    val portNum = value.toIntOrNull()
    return when {
      value.isBlank() -> "Port is required"
      portNum == null -> "Port must be a number"
      portNum < 1 || portNum > 65535 -> "Port must be between 1 and 65535"
      else -> null
    }
  }

  fun validateUsername(value: String): String? {
    return when {
      !isAnonymous && value.isBlank() -> "Username is required"
      value.length > 255 -> "Username too long"
      else -> null
    }
  }

  fun validatePath(value: String): String? {
    return when {
      value.isBlank() -> null // Will default to "/"
      !value.startsWith("/") -> "Path must start with /"
      value.length > 4096 -> "Path too long"
      else -> null
    }
  }

  val handleSave = {
    // Validate all fields
    hostError = validateHost(host)
    portError = validatePort(port)
    usernameError = validateUsername(username)
    pathError = validatePath(path)

    // Only save if all validations pass
    if (hostError == null && portError == null && usernameError == null && pathError == null) {
      val connection =
        NetworkConnection(
          name = name.ifBlank { "${protocol.displayName} - $host" },
          protocol = protocol,
          host = host.trim(),
          port = port.toIntOrNull() ?: protocol.defaultPort,
          username = if (isAnonymous) "" else username.trim(),
          password = if (isAnonymous) "" else password,
          path = path.ifBlank { "/" },
          isAnonymous = isAnonymous,
        )
      onSave(connection)
    }
  }

  AlertDialog(
    onDismissRequest = handleDismiss,
    modifier = Modifier.widthIn(min = 400.dp, max = 600.dp),
    title = {
      Text(
        text = "Add Network Connection",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Medium,
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {

      // Name and Protocol in one row
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              // Connection Name
              OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.weight(0.50f),
                singleLine = true,
              )

              // Protocol Dropdown
              ExposedDropdownMenuBox(
                expanded = protocolMenuExpanded,
                onExpandedChange = { protocolMenuExpanded = it },
                modifier = Modifier.weight(0.50f),
              ) {
                OutlinedTextField(
                  value = protocol.displayName,
                  onValueChange = { },
                  readOnly = true,
                  label = { Text("Protocol", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                  trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = protocolMenuExpanded) },
                  modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                  expanded = protocolMenuExpanded,
                  onDismissRequest = { protocolMenuExpanded = false },
                ) {
                  NetworkProtocol.entries.forEach { proto ->
                    DropdownMenuItem(
                      text = { Text(proto.displayName) },
                      onClick = {
                        protocol = proto
                        port = proto.defaultPort.toString()
                        protocolMenuExpanded = false
                      },
                    )
                  }
          }
        }
      }

      // Host
      OutlinedTextField(
        value = host,
        onValueChange = {
          host = it
          hostError = validateHost(it)
        },
        label = { Text("Host/IP Address", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("192.168.1.100", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        isError = hostError != null,
        supportingText = hostError?.let { { Text(it) } },
      )

      // Port and Path in one row
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              // Port
              OutlinedTextField(
                value = port,
                onValueChange = {
                  port = it
                  portError = validatePort(it)
                },
                label = { Text("Port", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.weight(0.3f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = portError != null,
                supportingText = portError?.let { { Text(it) } },
              )

              // Path
              OutlinedTextField(
                value = path,
                onValueChange = {
                  path = it
                  pathError = validatePath(it)
                },
                label = { Text("Path", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.weight(0.7f),
                singleLine = true,
                placeholder = { Text("/", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                isError = pathError != null,
                supportingText = pathError?.let { { Text(it) } },
              )
            }

      // Anonymous checkbox
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Checkbox(
          checked = isAnonymous,
          onCheckedChange = { isAnonymous = it },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Anonymous/Guest Access")
      }

      // Username
        OutlinedTextField(
          value = username,
          onValueChange = {
            username = it
            usernameError = validateUsername(it)
          },
          label = { Text("Username", maxLines = 1, overflow = TextOverflow.Ellipsis) },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          enabled = !isAnonymous,
          isError = usernameError != null && !isAnonymous,
          supportingText = if (!isAnonymous) usernameError?.let { { Text(it) } } else null,
        )

        // Password
        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          label = { Text("Password", maxLines = 1, overflow = TextOverflow.Ellipsis) },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          enabled = !isAnonymous,
          visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          trailingIcon = {
            if (!isAnonymous) {
              IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                  imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                  contentDescription = if (passwordVisible) "Hide password" else "Show password",
                )
              }
            }
          },
        )

      }
    },
    confirmButton = {
      Button(
        onClick = handleSave,
        enabled = host.isNotBlank() &&
          (isAnonymous || username.isNotBlank()) &&
          hostError == null &&
          portError == null &&
          usernameError == null &&
          pathError == null,
      ) {
        Text(
          text = "Save",
          fontWeight = FontWeight.SemiBold,
        )
      }
    },
    dismissButton = {
      TextButton(onClick = handleDismiss) {
        Text(
          text = "Cancel",
          fontWeight = FontWeight.Medium,
        )
      }
    },
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    tonalElevation = 6.dp,
    shape = MaterialTheme.shapes.extraLarge,
  )
}
