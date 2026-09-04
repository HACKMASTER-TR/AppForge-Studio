package com.appforge.studio.terminal

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.nativeClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.launch

@Composable
internal fun LinuxMultiSessionTerminalPanel(
    manager: AndroidLinuxRuntimeManager,
    distribution: LinuxDistribution,
    workspace: File
) {
    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    val allStates by
        LinuxPtySessionRegistry.states
            .collectAsState()

    var activeSessionId by
        remember(
            distribution,
            workspace.absolutePath
        ) {
            mutableStateOf<String?>(
                null
            )
        }

    var splitEnabled by
        remember {
            mutableStateOf(false)
        }

    var secondarySessionId by
        remember {
            mutableStateOf<String?>(
                null
            )
        }

    var message by
        remember {
            mutableStateOf<String?>(
                null
            )
        }

    var showSessionManager by
        remember {
            mutableStateOf(false)
        }

    LaunchedEffect(
        context.applicationContext,
        distribution,
        workspace.absolutePath
    ) {
        LinuxBackgroundJobStore.initialize(
            context.applicationContext
        )

        LinuxPtySessionRegistry.initialize(
            context.applicationContext
        )

        val id =
            LinuxPtySessionRegistry.ensureSession(
                context = context.applicationContext,
                distribution = distribution,
                workspace = workspace
            )

        val preferred =
            LinuxPtySessionRegistry
                .matching(
                    distribution = distribution,
                    workspace = workspace
                )
                .maxByOrNull {
                    it.lastActivatedAt
                }
                ?.id
                ?: id

        activeSessionId =
            activeSessionId ?: preferred

        LinuxPtySessionRegistry
            .markActivated(
                activeSessionId ?: preferred
            )
    }

    val workspacePath =
        remember(
            workspace.absolutePath
        ) {
            workspace.canonicalPath
        }

    val sessions =
        allStates
            .filter {
                it.distribution == distribution &&
                    it.workspacePath == workspacePath
            }
            .sortedWith(
                compareByDescending<LinuxManagedPtySessionState> {
                    it.favorite
                }.thenByDescending {
                    it.lastActivatedAt
                }
            )

    LaunchedEffect(
        sessions.map {
            it.id
        }
    ) {
        if (
            activeSessionId == null ||
            sessions.none {
                it.id == activeSessionId
            }
        ) {
            activeSessionId =
                sessions.firstOrNull()
                    ?.id

            activeSessionId?.let { id ->
                LinuxPtySessionRegistry
                    .markActivated(id)
            }
        }

        if (
            secondarySessionId != null &&
            sessions.none {
                it.id == secondarySessionId
            }
        ) {
            secondarySessionId =
                null
        }
    }

    val active =
        sessions.firstOrNull {
            it.id == activeSessionId
        }

    val secondary =
        sessions.firstOrNull {
            it.id == secondarySessionId
        }

    var renameDraft by
        remember(
            active?.id,
            active?.title
        ) {
            mutableStateOf(
                active?.title.orEmpty()
            )
        }

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    TerminalSurfaceRaised
            )
    ) {
        Column(
            modifier =
                Modifier.padding(12.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(7.dp)
            ) {
                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text(
                        "Linux PTY Çalışma Alanı",
                        color = TerminalText,
                        fontWeight =
                            FontWeight.Black,
                        fontSize = 14.sp
                    )

                    Text(
                        "Favoriler • son oturum • kopyalama • bölünmüş görünüm",
                        color =
                            TerminalSecondary,
                        fontSize = 9.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        showSessionManager =
                            !showSessionManager
                    }
                ) {
                    Text(
                        if (showSessionManager) {
                            "Oturumları Gizle"
                        } else {
                            "Oturumlar"
                        }
                    )
                }

                OutlinedButton(
                    onClick = {
                        runCatching {
                            LinuxPtySessionRegistry
                                .createSession(
                                    context =
                                        context.applicationContext,
                                    distribution =
                                        distribution,
                                    workspace =
                                        workspace
                                )
                        }.onSuccess {
                            activeSessionId = it
                            LinuxPtySessionRegistry
                                .markActivated(it)
                            message = null
                        }.onFailure {
                            message =
                                it.message
                                    ?: "Yeni Linux oturumu açılamadı."
                        }
                    }
                ) {
                    Text("+ Oturum")
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        ),
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {
                sessions.forEach { session ->
                    if (
                        session.id ==
                        activeSessionId
                    ) {
                        Button(
                            onClick = {
                                activeSessionId =
                                    session.id
                                LinuxPtySessionRegistry
                                    .markActivated(
                                        session.id
                                    )
                            }
                        ) {
                            Text(
                                sessionTabLabel(
                                    session
                                )
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                activeSessionId =
                                    session.id
                                LinuxPtySessionRegistry
                                    .markActivated(
                                        session.id
                                    )
                            }
                        ) {
                            Text(
                                sessionTabLabel(
                                    session
                                )
                            )
                        }
                    }
                }
            }

            if (showSessionManager) {
                Card(
                    modifier =
                        Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                TerminalSurface
                        )
                ) {
                    Column(
                        modifier =
                            Modifier.padding(9.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            "Oturum Yöneticisi",
                            color = TerminalText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )

                        sessions.forEach { session ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(
                                            rememberScrollState()
                                        ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(5.dp)
                            ) {
                                MiniPtyKey(
                                    if (
                                        session.id ==
                                        activeSessionId
                                    ) {
                                        "Aktif ✓"
                                    } else {
                                        "Aç"
                                    },
                                    true
                                ) {
                                    activeSessionId =
                                        session.id
                                    LinuxPtySessionRegistry
                                        .markActivated(
                                            session.id
                                        )
                                }

                                MiniPtyKey(
                                    if (session.favorite) {
                                        "★ Favori"
                                    } else {
                                        "☆ Favori"
                                    },
                                    true
                                ) {
                                    LinuxPtySessionRegistry
                                        .setFavorite(
                                            session.id,
                                            !session.favorite
                                        )
                                }

                                MiniPtyKey(
                                    "Kopyala",
                                    true
                                ) {
                                    runCatching {
                                        LinuxPtySessionRegistry
                                            .duplicateSession(
                                                context =
                                                    context.applicationContext,
                                                sourceId =
                                                    session.id
                                            )
                                    }.onSuccess { id ->
                                        activeSessionId = id
                                        LinuxPtySessionRegistry
                                            .markActivated(id)
                                        message =
                                            "Oturum profili kopyalandı; terminal çıktısı ve çalışan süreç kopyalanmadı."
                                    }.onFailure { error ->
                                        message =
                                            error.message
                                                ?: "Oturum kopyalanamadı."
                                    }
                                }

                                Text(
                                    session.title,
                                    color = TerminalSecondary,
                                    fontSize = 9.sp,
                                    modifier =
                                        Modifier.padding(
                                            horizontal = 4.dp,
                                            vertical = 11.dp
                                        )
                                )
                            }
                        }

                        active?.let { session ->
                            OutlinedTextField(
                                value = renameDraft,
                                onValueChange = { value ->
                                    renameDraft =
                                        value.take(40)
                                },
                                label = {
                                    Text(
                                        "Aktif oturum adı"
                                    )
                                },
                                singleLine = true,
                                modifier =
                                    Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    enabled =
                                        renameDraft
                                            .trim()
                                            .isNotEmpty(),
                                    onClick = {
                                        LinuxPtySessionRegistry
                                            .rename(
                                                session.id,
                                                renameDraft
                                            )
                                        message =
                                            "Oturum adı güncellendi."
                                    }
                                ) {
                                    Text("Adı Kaydet")
                                }

                                Text(
                                    if (session.favorite) {
                                        "Favori oturumlar önce, ardından son kullanılanlar gösterilir."
                                    } else {
                                        "Son kullanılan oturum uygulamaya döndüğünde otomatik seçilir."
                                    },
                                    color = TerminalMuted,
                                    fontSize = 8.sp,
                                    modifier =
                                        Modifier.padding(
                                            vertical = 11.dp
                                        )
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(7.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (splitEnabled) {
                            splitEnabled = false
                            secondarySessionId =
                                null
                        } else {
                            val other =
                                sessions.firstOrNull {
                                    it.id !=
                                        activeSessionId
                                }

                            if (other != null) {
                                secondarySessionId =
                                    other.id
                                splitEnabled = true
                            } else {
                                runCatching {
                                    LinuxPtySessionRegistry
                                        .createSession(
                                            context =
                                                context.applicationContext,
                                            distribution =
                                                distribution,
                                            workspace =
                                                workspace,
                                            title =
                                                "Linux Split"
                                        )
                                }.onSuccess {
                                    secondarySessionId = it
                                    splitEnabled = true
                                    LinuxPtySessionRegistry
                                        .markActivated(it)
                                    message = null
                                }.onFailure {
                                    message =
                                        it.message
                                            ?: "Bölünmüş oturum açılamadı."
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        if (splitEnabled) {
                            "Tek Görünüm"
                        } else {
                            "Bölünmüş"
                        }
                    )
                }

                active?.let { session ->
                    OutlinedButton(
                        onClick = {
                            LinuxPtySessionRegistry
                                .setNotifyOnCompletion(
                                    session.id,
                                    !session.notifyOnCompletion
                                )
                        }
                    ) {
                        Text(
                            if (
                                session.notifyOnCompletion
                            ) {
                                "Bildirim ✓"
                            } else {
                                "Bildirim Kapalı"
                            }
                        )
                    }

                    if (session.running) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    LinuxBackgroundJobCoordinator
                                        .protect(
                                            context,
                                            session
                                        )
                                }.onSuccess {
                                    message =
                                        "${session.title} için arka plan koruması başlatılıyor."
                                }.onFailure {
                                    message =
                                        it.message
                                            ?: "Arka plan koruması başlatılamadı."
                                }
                            }
                        ) {
                            Text("Arka Plan")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            LinuxPtySessionRegistry
                                .closeSession(
                                    session.id
                                )
                        }
                    ) {
                        Text("Kapat")
                    }
                }
            }

            TerminalNotificationPermissionControl(
                onMessage = {
                    message = it
                }
            )

            message?.let {
                Text(
                    TerminalSecretMasker.redact(
                        it
                    ),
                    color = TerminalWarning,
                    fontSize = 9.sp
                )
            }

            active?.let { session ->
                ManagedLinuxTerminalPane(
                    state = session,
                    manager = manager,
                    height =
                        if (splitEnabled) {
                            210
                        } else {
                            310
                        },
                    onMessage = {
                        message = it
                    }
                )
            }

            if (
                splitEnabled &&
                secondary != null
            ) {
                ManagedLinuxTerminalPane(
                    state = secondary,
                    manager = manager,
                    height = 210,
                    onMessage = {
                        message = it
                    }
                )
            }

            Text(
                "Oturumlar Terminal ekranından ayrılsan da AppForge süreci yaşadığı sürece çalışmaya devam eder. Android uygulama sürecini sonlandırırsa canlı PTY yeniden bağlanmaz; sekme/profil bilgisi geri yüklenir ve oturum yeniden başlatılabilir.",
                color = TerminalMuted,
                fontSize = 9.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun ManagedLinuxTerminalPane(
    state: LinuxManagedPtySessionState,
    manager: AndroidLinuxRuntimeManager,
    height: Int,
    onMessage: (String?) -> Unit
) {
    val scope =
        rememberCoroutineScope()

    val context =
        LocalContext.current

    val density =
        LocalDensity.current

    val clipboard =
        LocalClipboard.current
            .nativeClipboardManager

    val outputScroll =
        rememberScrollState()

    var imeShadow by
        remember(state.id) {
            mutableStateOf(
                TERMINAL_IME_SENTINEL
            )
        }

    var showSearch by
        remember(state.id) {
            mutableStateOf(false)
        }

    var searchQuery by
        remember(state.id) {
            mutableStateOf("")
        }

    var searchMatchIndex by
        remember(state.id) {
            mutableStateOf(0)
        }

    val initialUxPreferences =
        remember(context.applicationContext) {
            TerminalUxPreferences.load(
                context.applicationContext
            )
        }

    var terminalFontSizeSp by
        remember(state.id) {
            mutableStateOf(
                initialUxPreferences.fontSizeSp
            )
        }

    var productivityKeysExpanded by
        remember(state.id) {
            mutableStateOf(
                initialUxPreferences
                    .productivityKeysExpanded
            )
        }

    val terminalFontSize =
        terminalFontSizeSp.sp

    val terminalLineHeight =
        (terminalFontSizeSp *
            TERMINAL_LINE_HEIGHT_MULTIPLIER).sp

    val charWidthPx =
        with(density) {
            terminalFontSize.toPx() *
                TERMINAL_MONOSPACE_WIDTH_MULTIPLIER
        }

    val lineHeightPx =
        with(density) {
            terminalLineHeight.toPx()
        }

    val plainText =
        state.snapshot.plainText()

    val searchMatches =
        remember(
            plainText,
            searchQuery
        ) {
            terminalSearchLineMatches(
                text = plainText,
                query = searchQuery
            )
        }

    LaunchedEffect(
        plainText.length,
        state.running
    ) {
        if (
            searchQuery.isBlank()
        ) {
            outputScroll.scrollTo(
                outputScroll.maxValue
            )
        }
    }

    LaunchedEffect(
        searchQuery,
        searchMatches,
        searchMatchIndex,
        outputScroll.maxValue
    ) {
        if (
            searchQuery.isNotBlank() &&
            searchMatches.isNotEmpty()
        ) {
            val safeIndex =
                searchMatchIndex
                    .coerceIn(
                        0,
                        searchMatches.lastIndex
                    )

            if (
                safeIndex !=
                searchMatchIndex
            ) {
                searchMatchIndex =
                    safeIndex
            }

            val totalLines =
                plainText
                    .lineSequence()
                    .count()
                    .coerceAtLeast(1)

            val target =
                (
                    outputScroll.maxValue.toLong() *
                        searchMatches[safeIndex] /
                        totalLines
                    )
                    .toInt()
                    .coerceIn(
                        0,
                        outputScroll.maxValue
                    )

            outputScroll.scrollTo(target)
        }
    }

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    TerminalSurface
            )
    ) {
        Column(
            modifier =
                Modifier.padding(9.dp),
            verticalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(7.dp)
            ) {
                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text(
                        state.title,
                        color = TerminalText,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize = 11.sp
                    )

                    Text(
                        buildString {
                            when {
                                state.starting ->
                                    append("Başlatılıyor")

                                state.running ->
                                    append("Çalışıyor")

                                state.restored ->
                                    append("Geri yüklendi • yeniden başlatılmalı")

                                state.exitCode != null ->
                                    append("Çıkış: ${state.exitCode}")

                                else ->
                                    append("Hazır")
                            }

                            append(" • ")
                            append(state.columns)
                            append("×")
                            append(state.rows)
                        },
                        color =
                            if (state.running) {
                                TerminalPrimary
                            } else {
                                TerminalSecondary
                            },
                        fontSize = 8.sp
                    )
                }

                if (state.running) {
                    OutlinedButton(
                        onClick = {
                            LinuxPtySessionRegistry
                                .terminate(
                                    state.id
                                )
                        }
                    ) {
                        Text("Durdur")
                    }
                } else {
                    Button(
                        enabled =
                            !state.starting,
                        onClick = {
                            scope.launch {
                                runCatching {
                                    LinuxPtySessionRegistry
                                        .start(
                                            state.id,
                                            manager
                                        )
                                }.onFailure {
                                    onMessage(
                                        it.message
                                            ?: "Linux PTY başlatılamadı."
                                    )
                                }
                            }
                        }
                    ) {
                        Text("Başlat")
                    }
                }
            }

            BasicTextField(
                value = imeShadow,
                onValueChange = { next ->
                    if (!state.running) {
                        imeShadow =
                            TERMINAL_IME_SENTINEL
                        return@BasicTextField
                    }

                    val delta =
                        terminalImeDeltaWithSentinel(
                            previous = imeShadow,
                            next = next
                        )

                    imeShadow =
                        terminalImeShadow(
                            next
                        )

                    if (delta.isNotEmpty()) {
                        scope.launch {
                            LinuxPtySessionRegistry
                                .write(
                                    state.id,
                                    delta
                                )
                        }
                    }
                },
                enabled = state.running,
                textStyle =
                    TextStyle(
                        color = Color.Transparent,
                        fontSize = 1.sp
                    ),
                cursorBrush =
                    SolidColor(
                        Color.Transparent
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(height.dp)
                        .background(
                            TerminalBackground,
                            RoundedCornerShape(7.dp)
                        )
                        .onSizeChanged { size ->
                            val columns =
                                (size.width /
                                    charWidthPx)
                                    .toInt()
                                    .coerceIn(
                                        20,
                                        240
                                    )

                            val rows =
                                (size.height /
                                    lineHeightPx)
                                    .toInt()
                                    .coerceIn(
                                        8,
                                        80
                                    )

                            if (
                                columns !=
                                    state.columns ||
                                rows != state.rows
                            ) {
                                scope.launch {
                                    LinuxPtySessionRegistry
                                        .resize(
                                            state.id,
                                            rows,
                                            columns
                                        )
                                }
                            }
                        },
                decorationBox = { innerTextField ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                    ) {
                        Text(
                            state.snapshot
                                .toAnnotatedTerminalText(),
                            color = TerminalText,
                            fontFamily =
                                FontFamily.Monospace,
                            fontSize = terminalFontSize,
                            lineHeight = terminalLineHeight,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(
                                        outputScroll
                                    )
                        )

                        Box(
                            modifier =
                                Modifier
                                    .size(1.dp)
                                    .alpha(0f)
                        ) {
                            innerTextField()
                        }
                    }
                }
            )

            Text(
                if (state.running) {
                    "Terminale dokun • doğrudan yaz • panodan yapıştır • Enter/Backspace gerçek PTY'ye gider"
                } else {
                    "Doğrudan giriş için önce Linux PTY oturumunu başlat."
                },
                color = TerminalMuted,
                fontSize = 8.sp
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        ),
                horizontalArrangement =
                    Arrangement.spacedBy(5.dp)
            ) {
                MiniPtyKey(
                    "Yapıştır",
                    state.running
                ) {
                    val clipText =
                        clipboard.primaryClip
                            ?.takeIf {
                                it.itemCount > 0
                            }
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                            .orEmpty()

                    if (clipText.isEmpty()) {
                        onMessage(
                            "Panoda yapıştırılabilir metin yok."
                        )
                    } else {
                        val wasTruncated =
                            clipText.length >
                                MAX_TERMINAL_PASTE_CHARS

                        val normalized =
                            normalizeTerminalPaste(
                                clipText
                            )

                        scope.launch {
                            LinuxPtySessionRegistry
                                .write(
                                    state.id,
                                    normalized
                                )
                        }

                        onMessage(
                            if (wasTruncated) {
                                "Pano metni güvenli terminal sınırına kadar yapıştırıldı."
                            } else {
                                "Pano metni doğrudan PTY'ye gönderildi."
                            }
                        )
                    }
                }

                MiniPtyKey(
                    "Çıktıyı Kopyala",
                    plainText.isNotBlank()
                ) {
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            "AppForge Terminal",
                            plainText
                        )
                    )

                    onMessage(
                        "Terminal çıktısı panoya kopyalandı."
                    )
                }

                MiniPtyKey(
                    if (showSearch) {
                        "Aramayı Kapat"
                    } else {
                        "Ara"
                    },
                    true
                ) {
                    showSearch = !showSearch
                    if (!showSearch) {
                        searchQuery = ""
                        searchMatchIndex = 0
                    }
                }

                MiniPtyKey(
                    "A−",
                    terminalFontSizeSp >
                        MIN_TERMINAL_FONT_SP
                ) {
                    terminalFontSizeSp =
                        (terminalFontSizeSp - 1f)
                            .coerceAtLeast(
                                MIN_TERMINAL_FONT_SP
                            )
                    TerminalUxPreferences
                        .saveFontSize(
                            context,
                            terminalFontSizeSp
                        )
                }

                MiniPtyKey(
                    "A+",
                    terminalFontSizeSp <
                        MAX_TERMINAL_FONT_SP
                ) {
                    terminalFontSizeSp =
                        (terminalFontSizeSp + 1f)
                            .coerceAtMost(
                                MAX_TERMINAL_FONT_SP
                            )
                    TerminalUxPreferences
                        .saveFontSize(
                            context,
                            terminalFontSizeSp
                        )
                }

                MiniPtyKey(
                    if (productivityKeysExpanded) {
                        "Kısayollar ✓"
                    } else {
                        "Kısayollar"
                    },
                    true
                ) {
                    productivityKeysExpanded =
                        !productivityKeysExpanded
                    TerminalUxPreferences
                        .saveProductivityKeysExpanded(
                            context,
                            productivityKeysExpanded
                        )
                }

                Text(
                    "${terminalFontSizeSp.toInt()}sp",
                    color = TerminalSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    modifier =
                        Modifier.padding(
                            horizontal = 4.dp,
                            vertical = 11.dp
                        )
                )
            }

            if (showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { value ->
                        searchQuery =
                            value.take(
                                MAX_TERMINAL_SEARCH_CHARS
                            )
                        searchMatchIndex = 0
                    },
                    label = {
                        Text(
                            "Terminal çıktısında ara"
                        )
                    },
                    singleLine = true,
                    modifier =
                        Modifier.fillMaxWidth()
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(
                                rememberScrollState()
                            ),
                    horizontalArrangement =
                        Arrangement.spacedBy(5.dp)
                ) {
                    MiniPtyKey(
                        "Önceki",
                        searchMatches.isNotEmpty()
                    ) {
                        searchMatchIndex =
                            if (
                                searchMatchIndex <= 0
                            ) {
                                searchMatches.lastIndex
                            } else {
                                searchMatchIndex - 1
                            }
                    }

                    MiniPtyKey(
                        "Sonraki",
                        searchMatches.isNotEmpty()
                    ) {
                        searchMatchIndex =
                            if (
                                searchMatchIndex >=
                                searchMatches.lastIndex
                            ) {
                                0
                            } else {
                                searchMatchIndex + 1
                            }
                    }

                    Text(
                        when {
                            searchQuery.isBlank() ->
                                "Arama metni gir"

                            searchMatches.isEmpty() ->
                                "Eşleşme yok"

                            else ->
                                "${searchMatchIndex + 1}/${searchMatches.size} eşleşme"
                        },
                        color = TerminalSecondary,
                        fontSize = 9.sp,
                        modifier =
                            Modifier.padding(
                                horizontal = 4.dp,
                                vertical = 11.dp
                            )
                    )
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        ),
                horizontalArrangement =
                    Arrangement.spacedBy(5.dp)
            ) {
                MiniPtyKey(
                    "Esc",
                    state.running
                ) {
                    scope.launch {
                        LinuxPtySessionRegistry
                            .write(
                                state.id,
                                "\u001b"
                            )
                    }
                }

                MiniPtyKey(
                    "⌫",
                    state.running
                ) {
                    scope.launch {
                        LinuxPtySessionRegistry
                            .write(
                                state.id,
                                "\u007f"
                            )
                    }
                }

                MiniPtyKey(
                    "↵",
                    state.running
                ) {
                    scope.launch {
                        LinuxPtySessionRegistry
                            .write(
                                state.id,
                                "\r"
                            )
                    }
                }

                MiniPtyKey(
                    "Ctrl+C",
                    state.running
                ) {
                    scope.launch {
                        LinuxPtySessionRegistry
                            .sendControlC(
                                state.id
                            )
                    }
                }

                MiniPtyKey(
                    "Ctrl+L",
                    state.running
                ) {
                    scope.launch {
                        LinuxPtySessionRegistry
                            .write(
                                state.id,
                                "\u000c"
                            )
                    }
                }

                MiniPtyKey(
                    "Tab",
                    state.running
                ) {
                    scope.launch {
                        LinuxPtySessionRegistry
                            .write(
                                state.id,
                                "\t"
                            )
                    }
                }

                MiniPtyKey(
                    "↑",
                    state.running
                ) {
                    scope.launch {
                        LinuxPtySessionRegistry
                            .write(
                                state.id,
                                "\u001b[A"
                            )
                    }
                }

                MiniPtyKey(
                    "↓",
                    state.running
                ) {
                    scope.launch {
                        LinuxPtySessionRegistry
                            .write(
                                state.id,
                                "\u001b[B"
                            )
                    }
                }

                MiniPtyKey(
                    "←",
                    state.running
                ) {
                    scope.launch {
                        LinuxPtySessionRegistry
                            .write(
                                state.id,
                                "\u001b[D"
                            )
                    }
                }

                MiniPtyKey(
                    "→",
                    state.running
                ) {
                    scope.launch {
                        LinuxPtySessionRegistry
                            .write(
                                state.id,
                                "\u001b[C"
                            )
                    }
                }

                MiniPtyKey(
                    "Home",
                    state.running
                ) {
                    scope.launch {
                        LinuxPtySessionRegistry
                            .write(
                                state.id,
                                "\u001b[H"
                            )
                    }
                }

                MiniPtyKey(
                    "End",
                    state.running
                ) {
                    scope.launch {
                        LinuxPtySessionRegistry
                            .write(
                                state.id,
                                "\u001b[F"
                            )
                    }
                }

                MiniPtyKey(
                    "Pg↑",
                    state.running
                ) {
                    scope.launch {
                        LinuxPtySessionRegistry
                            .write(
                                state.id,
                                "\u001b[5~"
                            )
                    }
                }

                MiniPtyKey(
                    "Pg↓",
                    state.running
                ) {
                    scope.launch {
                        LinuxPtySessionRegistry
                            .write(
                                state.id,
                                "\u001b[6~"
                            )
                    }
                }
            }

            if (productivityKeysExpanded) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(
                                rememberScrollState()
                            ),
                    horizontalArrangement =
                        Arrangement.spacedBy(5.dp)
                ) {
                    TerminalControlKey(
                        "Ctrl+A",
                        state.running,
                        state.id,
                        "\u0001"
                    )

                    TerminalControlKey(
                        "Ctrl+E",
                        state.running,
                        state.id,
                        "\u0005"
                    )

                    TerminalControlKey(
                        "Ctrl+R",
                        state.running,
                        state.id,
                        "\u0012"
                    )

                    TerminalControlKey(
                        "Ctrl+U",
                        state.running,
                        state.id,
                        "\u0015"
                    )

                    TerminalControlKey(
                        "Ctrl+W",
                        state.running,
                        state.id,
                        "\u0017"
                    )
                }
            }

        }
    }
}

private fun terminalImeDeltaWithSentinel(
    previous: String,
    next: String
): String {
    if (
        previous == TERMINAL_IME_SENTINEL &&
        next.isEmpty()
    ) {
        return "\u007f"
    }

    return terminalImeDelta(
        previous =
            previous.removePrefix(
                TERMINAL_IME_SENTINEL
            ),
        next =
            next.removePrefix(
                TERMINAL_IME_SENTINEL
            )
    )
}

private fun terminalImeShadow(
    next: String
): String {
    val payload =
        next.removePrefix(
            TERMINAL_IME_SENTINEL
        )

    return if (
        payload.contains('\n') ||
        payload.contains('\r') ||
        payload.length >
            MAX_IME_SHADOW_CHARS
    ) {
        TERMINAL_IME_SENTINEL
    } else {
        TERMINAL_IME_SENTINEL +
            payload
    }
}

private fun normalizeTerminalPaste(
    value: String
): String =
    value
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .take(
            MAX_TERMINAL_PASTE_CHARS
        )

private fun terminalSearchLineMatches(
    text: String,
    query: String
): List<Int> {
    val needle =
        query.trim()

    if (needle.isEmpty()) {
        return emptyList()
    }

    return text
        .lineSequence()
        .mapIndexedNotNull { index, line ->
            index.takeIf {
                line.contains(
                    needle,
                    ignoreCase = true
                )
            }
        }
        .toList()
}

private fun terminalImeDelta(
    previous: String,
    next: String
): String {
    var prefix = 0
    val maxPrefix =
        minOf(
            previous.length,
            next.length
        )

    while (
        prefix < maxPrefix &&
        previous[prefix] == next[prefix]
    ) {
        prefix += 1
    }

    val removed =
        previous.length - prefix

    return buildString {
        repeat(removed) {
            append('\u007f')
        }

        append(
            next.substring(prefix)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
        )
    }
}

private const val TERMINAL_IME_SENTINEL =
    "\u2063"

private const val MAX_IME_SHADOW_CHARS =
    2_048

private const val MAX_TERMINAL_PASTE_CHARS =
    65_536

private const val MAX_TERMINAL_SEARCH_CHARS =
    256

private const val DEFAULT_TERMINAL_FONT_SP =
    10f

private const val MIN_TERMINAL_FONT_SP =
    8f

private const val MAX_TERMINAL_FONT_SP =
    18f

private const val TERMINAL_LINE_HEIGHT_MULTIPLIER =
    1.4f

private const val TERMINAL_MONOSPACE_WIDTH_MULTIPLIER =
    0.72f

@Composable
private fun MiniPtyKey(
    title: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        enabled = enabled,
        onClick = onClick
    ) {
        Text(title)
    }
}

@Composable
private fun TerminalControlKey(
    title: String,
    enabled: Boolean,
    sessionId: String,
    sequence: String
) {
    val scope =
        rememberCoroutineScope()

    MiniPtyKey(
        title = title,
        enabled = enabled
    ) {
        scope.launch {
            LinuxPtySessionRegistry
                .write(
                    sessionId,
                    sequence
                )
        }
    }
}

private fun sessionTabLabel(
    state: LinuxManagedPtySessionState
): String =
    buildString {
        if (state.favorite) {
            append("★ ")
        }

        if (state.running) {
            append("● ")
        } else if (state.restored) {
            append("↻ ")
        }

        append(state.title)
    }
