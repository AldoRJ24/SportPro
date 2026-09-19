package com.example.sportpro.ui.prototypes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportpro.model.CommunityPost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun US06CommunityScreen(
    onBack: () -> Unit = {}
) {
    var newPostContent by remember { mutableStateOf("") }
    var posts by remember {
        mutableStateOf(
            listOf(
                CommunityPost(
                    "1", "Entrenador Carlos", "DT Sub-15", "Hace 2 horas",
                    "¡Excelente entrenamiento el de hoy muchachos! A mantener la concentración para el partido del sábado. 💪⚽",
                    likesCount = 14, reportsCount = 0
                ),
                CommunityPost(
                    "2", "María Rodríguez", "Apoderado", "Hace 4 horas",
                    "Comparto las fotos de la jornada deportiva del fin de semana pasado. ¡Gran esfuerzo del equipo!",
                    likesCount = 8, reportsCount = 0
                ),
                CommunityPost(
                    "3", "Usuario Anónimo", "Jugador", "Hace 6 horas",
                    "Publicación con contenido inadecuado o spam que infringe las normas comunitarias.",
                    likesCount = 1, reportsCount = 2 // Al llegar a 3 se ocultará
                )
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("US-06: Muro de la Comunidad") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Crear Publicación
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Crear publicación en el muro", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = newPostContent,
                        onValueChange = { newPostContent = it },
                        placeholder = { Text("¿Qué quieres compartir hoy?") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                if (newPostContent.isNotBlank()) {
                                    val newPost = CommunityPost(
                                        id = System.currentTimeMillis().toString(),
                                        authorName = "Yo (Jugador)",
                                        authorRole = "Sub-15",
                                        timeAgo = "Ahora",
                                        content = newPostContent,
                                        likesCount = 0,
                                        reportsCount = 0
                                    )
                                    posts = listOf(newPost) + posts
                                    newPostContent = ""
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Publicar")
                        }
                    }
                }
            }

            Text(
                "Publicaciones Recientes (Moderación Automática tras 3 reportes)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(posts) { post ->
                    val isAutoHidden = post.reportsCount >= 3 || post.isHidden

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAutoHidden) Color.LightGray.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(post.authorName, fontWeight = FontWeight.Bold)
                                    Text("${post.authorRole} • ${post.timeAgo}", style = MaterialTheme.typography.bodySmall)
                                }

                                if (post.reportsCount > 0 && !isAutoHidden) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("${post.reportsCount}/3 Reportes") },
                                        leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100)) }
                                    )
                                }
                            }

                            if (isAutoHidden) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "⚠ Contenido ocultado automáticamente al alcanzar 3 reportes comunitarios.",
                                        color = Color.Red,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                Text(post.content, style = MaterialTheme.typography.bodyMedium)

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = {
                                        posts = posts.map {
                                            if (it.id == post.id) it.copy(likesCount = it.likesCount + 1) else it
                                        }
                                    }) {
                                        Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("${post.likesCount} Me gusta")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            posts = posts.map {
                                                if (it.id == post.id) {
                                                    val newReports = it.reportsCount + 1
                                                    it.copy(
                                                        reportsCount = newReports,
                                                        isHidden = newReports >= 3
                                                    )
                                                } else it
                                            }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color.Red
                                        )
                                    ) {
                                        Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reportar (${post.reportsCount})")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun US06CommunityScreenPreview() {
    US06CommunityScreen()
}
