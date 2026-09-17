package eu.kanade.presentation.more.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.tachiyomi.data.profile.YomotsuLevelManager
import eu.kanade.tachiyomi.data.profile.YomotsuAchievement
import eu.kanade.tachiyomi.data.profile.YomotsuTitle
import tachiyomi.presentation.core.components.material.Scaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    navigateUp: () -> Unit,
    username: String,
    totalXp: Long,
    totalChaptersRead: Int,
    totalMangas: Int,
    unlockedAchievements: List<YomotsuAchievement>,
    lockedAchievements: List<YomotsuAchievement>,
    equippedTitle: YomotsuTitle,
    unlockedTitles: List<YomotsuTitle>,
    avatarUri: String?,
    bannerUri: String?,
    onUsernameChanged: (String) -> Unit,
    onTitleSelected: (YomotsuTitle) -> Unit,
    onAvatarSelected: (String?) -> Unit,
    onBannerSelected: (String?) -> Unit
) {
    val context = LocalContext.current
    var showTitleDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(username) }

    val currentLevel = YomotsuLevelManager.calculateLevelFromXp(totalXp)
    val currentLevelXp = YomotsuLevelManager.getXpRequiredForLevel(currentLevel)
    val nextLevelXp = YomotsuLevelManager.getXpRequiredForLevel(currentLevel + 1)

    val progress = if (nextLevelXp > currentLevelXp) {
        (totalXp - currentLevelXp).toFloat() / (nextLevelXp - currentLevelXp).toFloat()
    } else {
        1f
    }

    val bannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { onBannerSelected(it.toString()) }
    }
    val avatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { onAvatarSelected(it.toString()) }
    }


    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Mudar Nome de Caçador") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Nome") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if(newName.isNotBlank()) onUsernameChanged(newName.trim())
                    showNameDialog = false
                }) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showTitleDialog) {
        AlertDialog(
            onDismissRequest = { showTitleDialog = false },
            title = { Text("Escolha seu Título") },
            text = {
                LazyColumn {
                    items(unlockedTitles) { title ->
                        Text(
                            text = title.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onTitleSelected(title)
                                    showTitleDialog = false
                                }
                                .padding(16.dp),
                            style = androidx.compose.ui.text.TextStyle(brush = Brush.horizontalGradient(colors = listOf(title.colorStart, title.colorEnd))),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTitleDialog = false }) { Text("Fechar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil Yomotsu") },
                navigationIcon = {
                    IconButton(onClick = navigateUp) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Voltar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // BANNER E AVATAR
            item {
                Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                    // BANNER
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp).clickable { bannerLauncher.launch("image/*") }) {
                        if (bannerUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(Uri.parse(bannerUri)).crossfade(true).build(),
                                contentDescription = "Banner",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color(0xFF1E1E1E), Color(0xFF000000)))))
                        }
                        IconButton(
                            onClick = { bannerLauncher.launch("image/*") },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Editar Banner", tint = Color.White.copy(alpha = 0.7f))
                        }
                    }

                    // AVATAR
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .align(Alignment.BottomCenter)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(4.dp, MaterialTheme.colorScheme.background, CircleShape)
                            .clickable { avatarLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(Uri.parse(avatarUri)).crossfade(true).build(),
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("V", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // NOME E TÍTULO
            item {
                Row(modifier = Modifier.fillMaxWidth().clickable { showNameDialog = true }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(username, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Outlined.Edit, contentDescription = "Editar Nome", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = equippedTitle.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.fillMaxWidth().clickable { showTitleDialog = true }.padding(vertical = 4.dp),
                    textAlign = TextAlign.Center,
                    style = androidx.compose.ui.text.TextStyle(brush = Brush.horizontalGradient(colors = listOf(equippedTitle.colorStart, equippedTitle.colorEnd)))
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // BARRA DE XP
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Nível $currentLevel", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Nível ${currentLevel + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)),
                        color = equippedTitle.colorEnd,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("$totalXp / $nextLevelXp XP", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // ESTATÍSTICAS
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatBox(Icons.Outlined.MenuBook, totalChaptersRead.toString(), "Lidos")
                    StatBox(Icons.Outlined.CollectionsBookmark, totalMangas.toString(), "Na Biblioteca")
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // TÍTULO CONQUISTAS
            item {
                Text(
                    text = "Sala de Troféus (${unlockedAchievements.size}/${unlockedAchievements.size + lockedAchievements.size})",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // CONQUISTAS DESBLOQUEADAS
            items(unlockedAchievements) { achievement ->
                Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                    AchievementItem(achievement, isUnlocked = true)
                }
            }

            // CONQUISTAS BLOQUEADAS
            items(lockedAchievements) { achievement ->
                Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                    AchievementItem(achievement, isUnlocked = false)
                }
            }
        }
    }
}

@Composable
fun StatBox(icon: ImageVector, value: String, label: String) {
    Card(
        modifier = Modifier.width(140.dp).height(100.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AchievementItem(achievement: YomotsuAchievement, isUnlocked: Boolean) {
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isUnlocked) 0.1f else 0.05f)
    val contentColor = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val icon = if (isUnlocked) achievement.category.icon else Icons.Outlined.Lock

    val modifier = Modifier.fillMaxWidth()

    if (isUnlocked && achievement.tier.isGradient) {
        // Conquistas Hardcore (Tier Rubi) com borda gradiente neon!
        val gradient = Brush.horizontalGradient(listOf(achievement.tier.color, achievement.tier.colorEnd))
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = androidx.compose.foundation.BorderStroke(2.dp, gradient)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                // Truque para pintar o ícone com gradiente
                Icon(icon, contentDescription = null, tint = achievement.tier.color, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(achievement.name, fontWeight = FontWeight.Bold, color = contentColor, fontSize = 16.sp)
                    Text(achievement.description, fontSize = 12.sp, color = contentColor)
                }
            }
        }
    } else {
        // Conquistas normais
        val iconTint = if (isUnlocked) achievement.tier.color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = if (isUnlocked) androidx.compose.foundation.BorderStroke(1.dp, achievement.tier.color.copy(alpha = 0.5f)) else null
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(achievement.name, fontWeight = FontWeight.Bold, color = contentColor, fontSize = 16.sp)
                    Text(achievement.description, fontSize = 12.sp, color = contentColor)
                }
            }
        }
    }
}
