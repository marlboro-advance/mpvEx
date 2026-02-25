package app.marlboroadvance.mpvex.ui.components

import android.content.res.AssetManager
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SymbolInputView
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource

// ─── One-time TextMate initialisation ────────────────────────────────────────
//
// Sora Editor docs  : https://project-sora.github.io/sora-editor-docs/
// tm4e grammar repo : https://github.com/eclipse-tm4e/tm4e/tree/main/org.eclipse.tm4e.language_pack/syntaxes
//
// To add support for another language later, drop grammar & theme into
// assets/textmate/<Lang>/, register it in assets/languages.json, and load
// the theme below.

@Volatile private var soraReady = false
private val soraLock = Any()

private suspend fun ensureSoraReady(assets: AssetManager) {
    if (soraReady) return
    withContext(Dispatchers.IO) {
        synchronized(soraLock) {
            if (soraReady) return@synchronized
            runCatching {
                FileProviderRegistry.getInstance()
                    .addFileProvider(AssetsFileResolver(assets))

                val themeRegistry = ThemeRegistry.getInstance()

                // ── Load Lua theme ──
                val luaSource = IThemeSource.fromInputStream(
                    assets.open("textmate/Lua/lua_theme.json"),
                    "lua_theme.json", null,
                )
                themeRegistry.loadTheme(ThemeModel(luaSource, "lua_theme"))

                // ── Load Conf theme ──
                val confSource = IThemeSource.fromInputStream(
                    assets.open("textmate/Conf/conf_theme.json"),
                    "conf_theme.json", null,
                )
                themeRegistry.loadTheme(ThemeModel(confSource, "conf_theme"))

                // Default to Lua theme; the editor factory switches per file extension
                themeRegistry.setTheme("lua_theme")

                GrammarRegistry.getInstance().loadGrammars("languages.json")
            }.onFailure { it.printStackTrace() }
            soraReady = true
        }
    }
}

// ─── Material Dynamic colours ────────────────────────────────────────────────

private data class SoraColors(
    val background: Int, val gutterBg: Int,
    val lineNumber: Int, val lineNumberActive: Int,
    val text: Int, val selection: Int, val cursor: Int, val divider: Int,
    val scrollThumb: Int, val scrollThumbDown: Int, val scrollTrack: Int,
    val completionBg: Int, val completionCorner: Int,
    val symbolBarBg: Int, val symbolBarText: Int,
    val currentLineBg: Int,
    val bracketHighlight: Int, val bracketUnderline: Int,
    val blockLine: Int, val blockLineCurrent: Int,
)

private fun buildColors(c: androidx.compose.material3.ColorScheme) = SoraColors(
    background       = c.surface.toArgb(),
    gutterBg         = c.surfaceContainerHighest.toArgb(),
    lineNumber       = c.onSurfaceVariant.copy(alpha = 0.55f).toArgb(),
    lineNumberActive = c.primary.toArgb(),
    text             = c.onSurface.toArgb(),
    selection        = c.primaryContainer.copy(alpha = 0.45f).toArgb(),
    cursor           = c.primary.toArgb(),
    divider          = c.outlineVariant.copy(alpha = 0.35f).toArgb(),
    scrollThumb      = c.primary.copy(alpha = 0.38f).toArgb(),
    scrollThumbDown  = c.primary.copy(alpha = 0.85f).toArgb(),
    scrollTrack      = c.surfaceVariant.copy(alpha = 0.20f).toArgb(),
    completionBg     = c.surfaceContainerHighest.toArgb(),
    completionCorner = c.primary.copy(alpha = 0.5f).toArgb(),
    symbolBarBg      = c.surfaceContainerHighest.toArgb(),
    symbolBarText    = c.onSurface.toArgb(),
    currentLineBg    = c.onSurface.copy(alpha = 0.08f).toArgb(),
    bracketHighlight = c.primaryContainer.copy(alpha = 0.35f).toArgb(),
    bracketUnderline = c.primary.toArgb(),
    blockLine        = c.outlineVariant.copy(alpha = 0.20f).toArgb(),
    blockLineCurrent = c.primary.copy(alpha = 0.30f).toArgb(),
)

/** Apply every Material-derived colour to a Sora [EditorColorScheme]. */
private fun EditorColorScheme.applyMaterialColors(t: SoraColors) {
    // Editor chrome
    setColor(EditorColorScheme.WHOLE_BACKGROUND,          t.background)
    setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND,    t.gutterBg)
    setColor(EditorColorScheme.LINE_NUMBER,               t.lineNumber)
    setColor(EditorColorScheme.LINE_NUMBER_CURRENT,       t.lineNumberActive)
    setColor(EditorColorScheme.TEXT_NORMAL,                t.text)
    setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND,   t.selection)
    setColor(EditorColorScheme.SELECTION_INSERT,           t.cursor)
    setColor(EditorColorScheme.LINE_DIVIDER,               t.divider)
    setColor(EditorColorScheme.CURRENT_LINE,               t.currentLineBg)
    // Scrollbars
    setColor(EditorColorScheme.SCROLL_BAR_TRACK,           t.scrollTrack)
    setColor(EditorColorScheme.SCROLL_BAR_THUMB,           t.scrollThumb)
    setColor(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED,   t.scrollThumbDown)
    // Autocomplete popup
    setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND,  t.completionBg)
    setColor(EditorColorScheme.COMPLETION_WND_CORNER,      t.completionCorner)
    setColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, t.text)
    setColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, t.lineNumber)
    setColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT, t.currentLineBg)
    // Bracket matching
    setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND, t.bracketHighlight)
    setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE,  t.bracketUnderline)
    setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, t.text)
    // Block / indent guide lines
    setColor(EditorColorScheme.BLOCK_LINE,                 t.blockLine)
    setColor(EditorColorScheme.BLOCK_LINE_CURRENT,         t.blockLineCurrent)
}

// ─── Symbol bar ──────────────────────────────────────────────────────────────

private val SYMBOL_DISPLAY = arrayOf(
    "⇥", "{", "}", "(", ")", "[", "]",
    ";", ".", "=", "+", "-", "*", "/",
    "#", "\"", "'", ",", "<", ">", "~",
)
private val SYMBOL_INSERT = arrayOf(
    "\t", "{", "}", "(", ")", "[", "]",
    ";", ".", "=", "+", "-", "*", "/",
    "#", "\"", "'", ",", "<", ">", "~",
)

// ─── Composable ──────────────────────────────────────────────────────────────

/**
 * Full-featured Sora code editor with TextMate syntax highlighting.
 *
 * Supports **Lua** (`source.lua`) and **MPV Conf** (`source.conf`) grammars,
 * selected by [fileExtension]. MPV-specific autocompletion is injected on
 * top of TextMate's default identifier completions via [MpvLanguageWrapper].
 *
 * Material Dynamic colours are applied live and update on light ↔ dark /
 * dynamic colour changes.
 */
@Composable
fun SoraCodeEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fileExtension: String = "lua",
    editorHeight: Dp = Dp.Unspecified,
) {
    val context = LocalContext.current
    val materialScheme = MaterialTheme.colorScheme
    val sColors = remember(materialScheme) { buildColors(materialScheme) }

    // ── Async TextMate init ──
    var ready by remember { mutableStateOf(soraReady) }
    LaunchedEffect(Unit) {
        if (!ready) {
            ensureSoraReady(context.assets)
            ready = true
        }
    }
    if (!ready) return

    var editor by remember { mutableStateOf<CodeEditor?>(null) }
    var symbolBar by remember { mutableStateOf<SymbolInputView?>(null) }
    var lastText by remember { mutableStateOf(value) }

    Column(modifier = modifier.background(materialScheme.surface)) {

        // ── Code editor ──────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                CodeEditor(ctx).also { ed ->
                    editor = ed

                    // Basic editor properties
                    ed.typefaceText = Typeface.MONOSPACE
                    ed.setTextSize(14f)
                    ed.isLineNumberEnabled = true
                    ed.isWordwrap = false
                    ed.props.autoIndent = true
                    ed.props.symbolPairAutoCompletion = true
                    ed.getComponent(EditorAutoCompletion::class.java).isEnabled = true

                    // TextMate colour scheme + language
                    runCatching {
                        val scheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                        scheme.applyMaterialColors(sColors)
                        ed.colorScheme = scheme

                        // Grammar scope + MPV autocomplete wrapper
                        val scope = if (fileExtension == "conf") "source.conf" else "source.lua"
                        val baseLang = TextMateLanguage.create(scope, true)
                        ed.setEditorLanguage(MpvLanguageWrapper(baseLang, fileExtension))
                    }.onFailure { it.printStackTrace() }

                    ed.setText(value)
                    lastText = value

                    ed.subscribeAlways(ContentChangeEvent::class.java) {
                        val newText = ed.text.toString()
                        if (newText != lastText) {
                            lastText = newText
                            onValueChange(newText)
                        }
                    }

                    symbolBar?.bindEditor(ed)
                }
            },
            update = { ed ->
                // Re-apply Material colours on theme change
                runCatching {
                    (ed.colorScheme as? TextMateColorScheme
                        ?: ed.colorScheme)
                        .applyMaterialColors(sColors)
                }

                // Sync symbol bar colours
                symbolBar?.let {
                    it.setTextColor(sColors.symbolBarText)
                    it.setBackgroundColor(sColors.symbolBarBg)
                }

                // External text update (e.g. load from file)
                if (value != lastText) {
                    lastText = value
                    ed.setText(value)
                    runCatching {
                        val last = ed.lineCount - 1
                        ed.setSelection(last, ed.text.getLine(last).length)
                    }
                }
            },
            modifier = if (editorHeight != Dp.Unspecified)
                Modifier.fillMaxWidth().height(editorHeight)
            else
                Modifier.fillMaxWidth().weight(1f),
        )

        // ── Symbol keypad bar ────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                SymbolInputView(ctx).also { bar ->
                    symbolBar = bar
                    bar.setTextColor(sColors.symbolBarText)
                    bar.setBackgroundColor(sColors.symbolBarBg)
                    bar.addSymbols(SYMBOL_DISPLAY, SYMBOL_INSERT)
                    editor?.let { bar.bindEditor(it) }
                }
            },
            update = { bar ->
                bar.setTextColor(sColors.symbolBarText)
                bar.setBackgroundColor(sColors.symbolBarBg)
                editor?.let { bar.bindEditor(it) }
            },
            modifier = Modifier.fillMaxWidth().height(40.dp),
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            editor?.release()
            editor = null
        }
    }
}
