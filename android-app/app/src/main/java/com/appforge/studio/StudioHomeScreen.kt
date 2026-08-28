@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.appforge.studio

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.appforge.studio.io.ProjectLibrary
import com.appforge.studio.io.DeletedProject
import com.appforge.studio.io.SavedProject
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import kotlin.math.cos
import kotlin.math.sin

private val HomeBackground =
    Color(0xFF070B0D)

private val HomeCard =
    Color(0xFF10181A)

private val HomeCardStrong =
    Color(0xFF162126)

private val HomeBlue =
    Color(0xFF6EE7B7)

private val HomeBlueStrong =
    Color(0xFF10B981)

private val HomePurple =
    Color(0xFFF59E0B)

private val HomeTextSecondary =
    Color(0xFFA5ADB7)

@Composable
internal fun StudioHomeScreen(
    proUnlocked: Boolean,
    onCreateQuick: () -> Unit,
    onCreateAdvanced: () -> Unit,
    onCreateConversion: () -> Unit,
    onOpenProject: (SavedProject) -> Unit,
    onOpenAi: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenPro: () -> Unit
) {
    val context =
        androidx.compose.ui.platform.LocalContext.current

    val configuration =
        LocalConfiguration.current

    val screenWidthDp =
        configuration.screenWidthDp

    val screenHeightDp =
        configuration.screenHeightDp

    val compact =
        screenWidthDp < 380

    val tablet =
        minOf(
            screenWidthDp,
            screenHeightDp
        ) >= 600

    val wide =
        screenWidthDp >= 600

    val contentMaxWidth =
        if (wide) 840.dp else 10000.dp

    val contentHorizontalPadding =
        when {
            compact -> 14.dp
            tablet -> 32.dp
            else -> 24.dp
        }

    var projects by remember {
        mutableStateOf(
            ProjectLibrary.load(context)
        )
    }

    val usedSlots =
        ProjectLibrary.freeProjectSlotsUsed(
            context
        )

    val trashCount =
        ProjectLibrary
            .loadTrash(
                context
            )
            .size

    var showCreateDialog by remember {
        mutableStateOf(false)
    }

    var showTopMenu by remember {
        mutableStateOf(false)
    }

    var openProjectMenu by remember {
        mutableStateOf<String?>(null)
    }

    var deleteCandidate by remember {
        mutableStateOf<SavedProject?>(null)
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
    ) {
        StudioAnimatedBackground()

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
        ) {
            Row(
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth()
                        .padding(
                            start = contentHorizontalPadding,
                            end = if (compact) 8.dp else contentHorizontalPadding,
                            top = 10.dp,
                            bottom = 10.dp
                        ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text(
                        text = "AppForge Studio",
                        fontSize =
                            when {
                                compact -> 21.sp
                                tablet -> 28.sp
                                else -> 24.sp
                            },
                        fontWeight =
                            FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Text(
                        text =
                            if (proUnlocked) {
                                "Projeler • ${projects.size} kayıt • PRO"
                            } else {
                                "Projeler • ${projects.size} kayıt • Ücretsiz $usedSlots/5"
                            },
                        fontSize = 12.sp,
                        color = HomeTextSecondary
                    )
                }

                Box {
                    LabeledActionButton(
                        icon = "⋮",
                        label = "Diğer",
                        onClick = {
                            showTopMenu =
                                !showTopMenu
                        }
                    )

                    DropdownMenu(
                        expanded = showTopMenu,
                        onDismissRequest = {
                            showTopMenu = false
                        }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text("Hesabım")
                            },
                            onClick = {
                                showTopMenu = false
                                onOpenAccount()
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text("Derleme Geçmişi")
                            },
                            onClick = {
                                showTopMenu = false
                                onOpenHistory()
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Geri Dönüşüm Kutusu ($trashCount)"
                                )
                            },
                            onClick = {
                                showTopMenu = false
                                onOpenTrash()
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text("Pro")
                            },
                            onClick = {
                                showTopMenu = false
                                onOpenPro()
                            }
                        )
                    }
                }
            }

            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .widthIn(max = contentMaxWidth)
                        .padding(
                            horizontal = contentHorizontalPadding,
                            vertical = 8.dp
                        )
                        .fillMaxWidth()
                        .height(
                            when {
                                compact -> 60.dp
                                tablet -> 72.dp
                                else -> 68.dp
                            }
                        )
                        .clip(
                            RoundedCornerShape(
                                if (compact) 18.dp else 20.dp
                            )
                        )
                        .background(
                            brush =
                                Brush.horizontalGradient(
                                    listOf(
                                        HomeBlueStrong,
                                        HomePurple
                                    )
                                )
                        )
                        .clickable {
                            showCreateDialog =
                                true
                        },
                contentAlignment =
                    Alignment.Center
            ) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            12.dp
                        )
                ) {
                    Text(
                        text = "+",
                        fontSize = if (compact) 28.sp else 32.sp,
                        fontWeight =
                            FontWeight.Light,
                        color =
                            Color(0xFF07140F)
                    )

                    Text(
                        text = "Yeni proje",
                        fontSize =
                            when {
                                compact -> 15.sp
                                tablet -> 18.sp
                                else -> 17.sp
                            },
                        fontWeight =
                            FontWeight.Bold,
                        color =
                            Color(0xFF07140F)
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
            ) {
                LazyColumn(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .widthIn(max = contentMaxWidth)
                            .fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start = contentHorizontalPadding,
                            end = contentHorizontalPadding,
                            top = 12.dp,
                            bottom = 24.dp
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            14.dp
                        )
                ) {
                    if (projects.isEmpty()) {
                        item {
                            EmptyProjectsCard()
                        }
                    }

                    items(
                        items = projects,
                        key = {
                            it.id
                        }
                    ) { project ->
                        ProjectHomeCard(
                            project = project,
                            menuExpanded =
                                openProjectMenu ==
                                    project.id,
                            onClick = {
                                onOpenProject(
                                    project
                                )
                            },
                            onMenu = {
                                openProjectMenu =
                                    if (
                                        openProjectMenu ==
                                        project.id
                                    ) {
                                        null
                                    } else {
                                        project.id
                                    }
                            },
                            onDismissMenu = {
                                openProjectMenu =
                                    null
                            },
                            onOpen = {
                                openProjectMenu =
                                    null

                                onOpenProject(
                                    project
                                )
                            },
                            onDelete = {
                                openProjectMenu =
                                    null

                                deleteCandidate =
                                    project
                            }
                        )
                    }
                }
            }

            StudioBottomNavigation(
                onProjects = {},
                onAi =
                    onOpenAi,
                onTemplates =
                    onOpenTemplates,
                onSettings =
                    onOpenSettings
            )
        }
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = {
                showCreateDialog =
                    false
            },
            onQuick = {
                showCreateDialog =
                    false

                onCreateQuick()
            },
            onAdvanced = {
                showCreateDialog =
                    false

                onCreateAdvanced()
            },
            onConversion = {
                showCreateDialog =
                    false

                onCreateConversion()
            }
        )
    }

    deleteCandidate?.let {
        project ->

        AlertDialog(
            onDismissRequest = {
                deleteCandidate =
                    null
            },
            title = {
                Text(
                    "Projeyi sil?"
                )
            },
            text = {
                Text(
                    "${project.name} geri dönüşüm kutusuna taşınacak ve 30 gün sonra otomatik silinecek."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ProjectLibrary.delete(
                            context,
                            project.id
                        )

                        projects =
                            ProjectLibrary.load(
                                context
                            )

                        deleteCandidate =
                            null
                    }
                ) {
                    Text(
                        "Çöpe taşı",
                        color =
                            Color(0xFFFF7171)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deleteCandidate =
                            null
                    }
                ) {
                    Text("Vazgeç")
                }
            }
        )
    }
}

@Composable
internal fun StudioTrashScreen(
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context =
        androidx.compose.ui.platform.LocalContext.current

    var deletedProjects by
        remember {
            mutableStateOf(
                ProjectLibrary.loadTrash(
                    context
                )
            )
        }

    var permanentDeleteCandidate by
        remember {
            mutableStateOf<DeletedProject?>(
                null
            )
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(HomeBackground)
    ) {
        androidx.compose.material3.TopAppBar(
            title = {
                Column {
                    Text(
                        "Geri Dönüşüm Kutusu",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Silinen projeler 30 gün saklanır",
                        color = HomeTextSecondary,
                        fontSize = 11.sp
                    )
                }
            },
            navigationIcon = {
                TextButton(
                    onClick = onBack
                ) {
                    Text("← Geri")
                }
            },
            colors =
                androidx.compose.material3.TopAppBarDefaults
                    .topAppBarColors(
                        containerColor = HomeBackground
                    )
        )

        LazyColumn(
            modifier =
                Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal = 16.dp,
                    vertical = 12.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {
            if (
                deletedProjects.isEmpty()
            ) {
                item {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = HomeCard
                            ),
                        shape =
                            RoundedCornerShape(22.dp)
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {
                            Text(
                                "♻",
                                fontSize = 40.sp,
                                color = HomeBlue
                            )
                            Text(
                                "Geri dönüşüm kutusu boş",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            items(
                items = deletedProjects,
                key = {
                    it.id
                }
            ) {
                project ->

                val dayMs =
                    24L * 60L * 60L * 1000L

                val remainingDays =
                    maxOf(
                        1L,
                        (
                            project.purgeAt -
                                System.currentTimeMillis() +
                                dayMs -
                                1L
                        ) /
                            dayMs
                    )

                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = HomeCard
                        ),
                    shape =
                        RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            project.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            project.packageName,
                            color = HomeTextSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            "$remainingDays gün sonra otomatik silinecek",
                            color = HomePurple,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    if (
                                        ProjectLibrary.restoreDeleted(
                                            context,
                                            project.id
                                        )
                                    ) {
                                        deletedProjects =
                                            ProjectLibrary.loadTrash(
                                                context
                                            )

                                        onMessage(
                                            "Proje geri yüklendi: ${project.name}"
                                        )
                                    }
                                },
                                modifier =
                                    Modifier.weight(1f)
                            ) {
                                Text("Geri yükle")
                            }

                            TextButton(
                                onClick = {
                                    permanentDeleteCandidate =
                                        project
                                },
                                modifier =
                                    Modifier.weight(1f)
                            ) {
                                Text(
                                    "Kalıcı sil",
                                    color = Color(0xFFFF7171)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    permanentDeleteCandidate?.let {
        project ->

        AlertDialog(
            onDismissRequest = {
                permanentDeleteCandidate =
                    null
            },
            title = {
                Text("Kalıcı olarak silinsin mi?")
            },
            text = {
                Text(
                    "${project.name} geri alınamayacak."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ProjectLibrary.deletePermanently(
                            context,
                            project.id
                        )

                        deletedProjects =
                            ProjectLibrary.loadTrash(
                                context
                            )

                        permanentDeleteCandidate =
                            null

                        onMessage(
                            "Proje kalıcı olarak silindi."
                        )
                    }
                ) {
                    Text(
                        "Kalıcı sil",
                        color = Color(0xFFFF7171)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        permanentDeleteCandidate =
                            null
                    }
                ) {
                    Text("Vazgeç")
                }
            }
        )
    }
}

@Composable
private fun StudioAnimatedBackground() {
    val transition =
        rememberInfiniteTransition(
            label =
                "appforge-home-bg"
        )

    val phase by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis =
                                18000,
                            easing =
                                LinearEasing
                        ),
                    repeatMode =
                        RepeatMode.Restart
                ),
            label = "ambient-phase"
        )

    Canvas(
        modifier =
            Modifier.fillMaxSize()
    ) {
        drawRect(
            color =
                HomeBackground
        )

        val angle =
            phase *
                Math.PI.toFloat() *
                2f

        val radiusLarge =
            size.minDimension *
                0.72f

        val radiusMedium =
            size.minDimension *
                0.56f

        val cyanCenter =
            Offset(
                x =
                    size.width *
                        (
                            0.18f +
                                0.10f *
                                cos(angle)
                        ),
                y =
                    size.height *
                        (
                            0.55f +
                                0.10f *
                                sin(angle)
                        )
            )

        val purpleCenter =
            Offset(
                x =
                    size.width *
                        (
                            0.78f +
                                0.08f *
                                sin(angle)
                        ),
                y =
                    size.height *
                        (
                            0.67f +
                                0.08f *
                                cos(angle)
                        )
            )

        val blueCenter =
            Offset(
                x =
                    size.width *
                        (
                            0.64f +
                                0.06f *
                                cos(
                                    angle +
                                        1.8f
                                )
                        ),
                y =
                    size.height *
                        (
                            0.20f +
                                0.05f *
                                sin(
                                    angle +
                                        1.8f
                                )
                        )
            )

        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            Color(
                                0xFF10B981
                            ).copy(
                                alpha = 0.16f
                            ),
                            Color.Transparent
                        ),
                    center =
                        cyanCenter,
                    radius =
                        radiusLarge
                ),
            radius =
                radiusLarge,
            center =
                cyanCenter
        )

        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            Color(
                                0xFFFF7A18
                            ).copy(
                                alpha = 0.14f
                            ),
                            Color.Transparent
                        ),
                    center =
                        purpleCenter,
                    radius =
                        radiusLarge
                ),
            radius =
                radiusLarge,
            center =
                purpleCenter
        )

        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            Color(
                                0xFFEC4899
                            ).copy(
                                alpha = 0.11f
                            ),
                            Color.Transparent
                        ),
                    center =
                        blueCenter,
                    radius =
                        radiusMedium
                ),
            radius =
                radiusMedium,
            center =
                blueCenter
        )
    }
}

@Composable
private fun ProjectHomeCard(
    project: SavedProject,
    menuExpanded: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    HomeCard.copy(
                        alpha = 0.94f
                    )
            ),
        shape =
            RoundedCornerShape(
                24.dp
            )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 18.dp,
                        vertical = 18.dp
                    ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            ProjectAvatar(
                project.name
            )

            Spacer(
                modifier =
                    Modifier.size(
                        16.dp
                    )
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {
                Text(
                    text =
                        project.name,
                    fontSize = 17.sp,
                    fontWeight =
                        FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )

                Spacer(
                    Modifier.height(
                        4.dp
                    )
                )

                Text(
                    text =
                        formatProjectDate(
                            project.updatedAt
                        ),
                    fontSize = 12.sp,
                    color =
                        HomeTextSecondary
                )

                Text(
                    text =
                        projectVersion(
                            project
                        ),
                    fontSize = 12.sp,
                    color =
                        HomeTextSecondary
                )
            }

            Box {
                LabeledActionButton(
                    icon = "⋮",
                    label = "Menü",
                    onClick = onMenu
                )

                DropdownMenu(
                    expanded =
                        menuExpanded,
                    onDismissRequest =
                        onDismissMenu
                ) {
                    DropdownMenuItem(
                        text = {
                            Text("Aç")
                        },
                        onClick =
                            onOpen
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                "Sil",
                                color =
                                    Color(
                                        0xFFFF7171
                                    )
                            )
                        },
                        onClick =
                            onDelete
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectAvatar(
    name: String
) {
    val seed =
        name.hashCode()

    val first =
        name
            .trim()
            .firstOrNull()
            ?.uppercase()
            ?: "A"

    val c1 =
        when (
            kotlin.math.abs(seed) %
                4
        ) {
            0 ->
                Color(0xFF5D9DFF)

            1 ->
                Color(0xFF8D62FF)

            2 ->
                Color(0xFF22B9C8)

            else ->
                Color(0xFFCC5FFF)
        }

    Box(
        modifier =
            Modifier
                .size(62.dp)
                .clip(
                    RoundedCornerShape(
                        18.dp
                    )
                )
                .background(
                    Brush.linearGradient(
                        listOf(
                            c1,
                            c1.copy(
                                alpha =
                                    0.28f
                            )
                        )
                    )
                ),
        contentAlignment =
            Alignment.Center
    ) {
        Text(
            text = first,
            color = Color.White,
            fontSize = 27.sp,
            fontWeight =
                FontWeight.ExtraBold
        )
    }
}

@Composable
private fun EmptyProjectsCard() {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    HomeCard.copy(
                        alpha = 0.90f
                    )
            ),
        shape =
            RoundedCornerShape(
                24.dp
            )
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        24.dp
                    ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Text(
                text = "◇",
                fontSize = 42.sp,
                color = HomeBlue
            )

            Text(
                text =
                    "Henüz proje yok",
                fontWeight =
                    FontWeight.Bold,
                fontSize = 18.sp
            )

            Spacer(
                Modifier.height(
                    5.dp
                )
            )

            Text(
                text =
                    "İlk uygulamanı oluşturmak için yukarıdaki Proje Oluştur butonuna dokun.",
                color =
                    HomeTextSecondary,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun StudioBottomNavigation(
    onProjects: () -> Unit,
    onAi: () -> Unit,
    onTemplates: () -> Unit,
    onSettings: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val compact = screenWidthDp < 380
    val tablet = minOf(screenWidthDp, screenHeightDp) >= 600
    val navMaxWidth = if (screenWidthDp >= 600) 760.dp else 10000.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                horizontal = when {
                    compact -> 8.dp
                    tablet -> 24.dp
                    else -> 16.dp
                },
                vertical = 10.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = navMaxWidth)
                .fillMaxWidth(),
            shape = RoundedCornerShape(if (compact) 28.dp else 34.dp),
            color = Color(0xF20C1216),
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = if (compact) 7.dp else 9.dp,
                        horizontal = if (compact) 2.dp else 8.dp
                    ),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BottomNavigationItem("⌂", "Projeler", true, compact, onProjects)
                BottomNavigationItem("✨", "AI Asistan", false, compact, onAi)
                BottomNavigationItem("◇", "Şablonlar", false, compact, onTemplates)
                BottomNavigationItem("⚙", "Ayarlar", false, compact, onSettings)
            }
        }
    }
}

@Composable
private fun BottomNavigationItem(
    icon: String,
    label: String,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (compact) 4.dp else 10.dp,
                vertical = if (compact) 5.dp else 7.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (selected) Color(0xFF213341) else Color.Transparent
                )
                .padding(
                    horizontal = if (compact) 8.dp else 13.dp,
                    vertical = if (compact) 3.dp else 4.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                color = if (selected) HomeBlue else HomeTextSecondary,
                fontSize = if (compact) 22.sp else 26.sp
            )
        }

        Text(
            text = label,
            fontSize = if (compact) 9.sp else 11.sp,
            color = if (selected) HomeBlue else HomeTextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onQuick: () -> Unit,
    onAdvanced: () -> Unit,
    onConversion: () -> Unit
) {
    val compact = LocalConfiguration.current.screenWidthDp < 380

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(if (compact) 24.dp else 28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101617))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (compact) 18.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
            ) {
                Text(
                    text = "Nasıl oluşturmak istersin?",
                    color = Color.White,
                    fontSize = if (compact) 20.sp else 23.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                ModeCard("ϟ", "Hızlı Oluştur", "Sadece isim, içerik ve ikon. Gerisini AppForge otomatik ayarlar.", onQuick)
                ModeCard("☷", "Gelişmiş Oluştur", "Paket adı, SDK, tema, izinler, imzalama ve gelişmiş ayarlar.", onAdvanced)
                ModeCard("↔", "Dönüşüm", "APK → Windows EXE veya EXE → Android APK dönüşüm araçları.", onConversion)
            }
        }
    }
}

@Composable
private fun ModeCard(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    val compact = LocalConfiguration.current.screenWidthDp < 380

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 18.dp else 22.dp),
        colors = CardDefaults.cardColors(containerColor = HomeCardStrong)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 14.dp else 18.dp,
                    vertical = if (compact) 14.dp else 18.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 42.dp else 48.dp)
                    .clip(RoundedCornerShape(if (compact) 12.dp else 14.dp))
                    .background(Color(0xFF172A34)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    fontSize = if (compact) 25.sp else 29.sp,
                    color = HomeBlue
                )
            }

            Spacer(Modifier.size(if (compact) 12.dp else 16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = if (compact) 15.sp else 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = description,
                    color = HomeTextSecondary,
                    fontSize = if (compact) 11.sp else 12.sp,
                    lineHeight = if (compact) 15.sp else 16.sp
                )
            }

            Text(
                text = "›",
                fontSize = if (compact) 24.sp else 28.sp,
                color = HomeTextSecondary
            )
        }
    }
}

private fun projectVersion(
    project: SavedProject
): String =
    runCatching {
        val json =
            JSONObject(
                project.json
            )

        val versionName =
            json.optString(
                "versionName",
                "1.0.0"
            )

        val versionCode =
            json.optInt(
                "versionCode",
                1
            )

        "$versionName ($versionCode)"
    }.getOrDefault(
        "1.0.0 (1)"
    )

private fun formatProjectDate(
    timestamp: Long
): String =
    runCatching {
        DateFormat
            .getDateInstance(
                DateFormat.MEDIUM
            )
            .format(
                Date(timestamp)
            )
    }.getOrDefault(
        ""
    )
