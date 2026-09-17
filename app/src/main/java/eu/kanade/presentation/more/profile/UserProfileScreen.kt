package eu.kanade.presentation.more.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.data.profile.YomotsuLevelManager
import eu.kanade.tachiyomi.data.profile.YomotsuAchievement
import tachiyomi.presentation.core.components.material.Scaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    navigateUp: () -> Unit,
    totalXp: Long,
    totalChaptersRead: Int,
    totalMangas: Int,
    unlockedAchievements: List<YomotsuAchievement>,
    lockedAchievements: List<YomotsuAchievement>
) {
    val currentLevel = YomotsuLevelManager.calculateLevelFromXp(totalXp)
    val currentTitle = YomotsuLevelManager.getCurrentTitleByLevel(currentLevel)
    val currentLevelXp = YomotsuLevelManager.getXpRequiredForLevel(currentLevel)
    val nextLevelXp = YomotsuLevelManager.getXpRequiredForLevel(currentLevel + 1)
    
    val progress = if (nextLevelXp > currentLevelXp) {
        (totalXp - currentLevelXp).toFloat() / (nextLevelXp - currentLevelXp).toFloat()
    } else {
        1f
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil Yomotsu") },
                navigationIcon = {
                    IconButton(onClick = navigateUp) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            // BANNER E AVATAR
            Box(
                modifier = Modifier.fillMaxWidth().height(240.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
                            )
                        )
                )
                Box(
                    modifier = Modifier.size(120.dp).align(Alignment.BottomCenter).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant).border(4.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("V", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Veterano Yomotsu", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            
            Text(
                text = currentTitle.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = androidx.compose.ui.text.TextStyle(brush = Brush.horizontalGradient(colors = listOf(currentTitle.colorStart, currentTitle.colorEnd)))
            )

            Spacer(modifier = Modifier.height(24.dp))

            // BARRA DE XP
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Nível $currentLevel", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Nível ${currentLevel + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)),
                    color = currentTitle.colorEnd,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("$totalXp / $nextLevelXp XP", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ESTATÍSTICAS
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBox(Icons.Outlined.MenuBook, totalChaptersRead.toString(), "Lidos")
                StatBox(Icons.Outlined.CollectionsBookmark, totalMangas.toString(), "Na Biblioteca")
            }

            Spacer(modifier = Modifier.height(32.dp))

            // CONQUISTAS
            Text(
                text = "Sala de Troféus (${unlockedAchievements.size}/${unlockedAchievements.size + lockedAchievements.size})",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                unlockedAchievements.forEach { achievement ->
                    AchievementItem(achievement, isUnlocked = true)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                lockedAchievements.forEach { achievement ->
                    AchievementItem(achievement, isUnlocked = false)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
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
    val containerColor = if (isUnlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val contentColor = if (isUnlocked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val icon = if (isUnlocked) achievement.category.icon else Icons.Outlined.Lock

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(achievement.name, fontWeight = FontWeight.Bold, color = contentColor, fontSize = 16.sp)
                Text(achievement.description, fontSize = 12.sp, color = contentColor)
            }
        }
    }
}
