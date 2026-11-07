package com.sportpro.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.FeedPost
import com.sportpro.app.core.data.PostComment
import com.sportpro.app.core.data.friendlyMessage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedUi(
    val loading: Boolean = true,
    val posts: List<FeedPost> = emptyList(),
    val error: String? = null,
    /** Publicación con la hoja de comentarios abierta (null = cerrada). */
    val openMatchId: String? = null,
    val comments: List<PostComment> = emptyList(),
    val commentsLoading: Boolean = false,
    val sending: Boolean = false,
) {
    val openPost: FeedPost? get() = posts.firstOrNull { it.matchId == openMatchId }
}

/**
 * Feed de Inicio estilo Instagram: publicaciones (resúmenes IA aprobados), me gusta y comentarios.
 * - Escucha Realtime: si alguien da like o comenta, se recarga (debounce para agrupar ráfagas).
 * - El like es «optimista»: se pinta al instante y luego se corrige con la respuesta del servidor.
 * - La moderación (eliminar comentarios, desactivarlos) la valida el servidor: aquí solo se llama.
 */
@OptIn(FlowPreview::class)
class FeedViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(FeedUi())
    val ui: StateFlow<FeedUi> = _ui.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        load()
        viewModelScope.launch {
            container.feed.observe().debounce(400).catch { }.collect {
                load()
                _ui.value.openMatchId?.let { loadComments(it) }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            try {
                val posts = container.feed.feed()
                _ui.update { it.copy(loading = false, posts = posts, error = null) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }

    fun toggleLike(post: FeedPost) {
        updatePost(post.matchId) {
            it.copy(liked = !it.liked, likes = (it.likes + (if (it.liked) -1 else 1)).coerceAtLeast(0))
        }
        viewModelScope.launch {
            try {
                val r = container.feed.toggleLike(post.matchId)
                updatePost(post.matchId) { it.copy(liked = r.liked, likes = r.likes) }
            } catch (e: Exception) {
                updatePost(post.matchId) { it.copy(liked = post.liked, likes = post.likes) }
                _messages.tryEmit(e.friendlyMessage())
            }
        }
    }

    fun openComments(post: FeedPost) {
        _ui.update { it.copy(openMatchId = post.matchId, comments = emptyList(), commentsLoading = true) }
        loadComments(post.matchId)
    }

    fun closeComments() {
        _ui.update { it.copy(openMatchId = null, comments = emptyList()) }
    }

    private fun loadComments(matchId: String) {
        viewModelScope.launch {
            try {
                val list = container.feed.comments(matchId)
                if (_ui.value.openMatchId == matchId) _ui.update { it.copy(comments = list, commentsLoading = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(commentsLoading = false) }
                _messages.tryEmit(e.friendlyMessage())
            }
        }
    }

    // Publicar comentario; onSent limpia la caja de texto solo si el servidor lo aceptó.
    fun send(text: String, onSent: () -> Unit) {
        val matchId = _ui.value.openMatchId ?: return
        val body = text.trim()
        if (body.isEmpty()) return
        viewModelScope.launch {
            _ui.update { it.copy(sending = true) }
            try {
                container.feed.addComment(matchId, body)
                onSent()
                updatePost(matchId) { it.copy(comments = it.comments + 1) }
                loadComments(matchId)
            } catch (e: Exception) {
                _messages.tryEmit(e.friendlyMessage())
            } finally {
                _ui.update { it.copy(sending = false) }
            }
        }
    }

    // Eliminar (mío, o de otros si modero). El servidor vuelve a verificar el permiso.
    fun delete(comment: PostComment) {
        val matchId = _ui.value.openMatchId ?: return
        viewModelScope.launch {
            try {
                container.feed.deleteComment(comment.id)
                _ui.update { s -> s.copy(comments = s.comments.filterNot { it.id == comment.id }) }
                updatePost(matchId) { it.copy(comments = (it.comments - 1).coerceAtLeast(0)) }
                _messages.tryEmit("Comentario eliminado")
            } catch (e: Exception) {
                _messages.tryEmit(e.friendlyMessage())
            }
        }
    }

    fun setCommentsEnabled(post: FeedPost, enabled: Boolean) {
        viewModelScope.launch {
            try {
                container.feed.setCommentsEnabled(post.matchId, enabled)
                updatePost(post.matchId) { it.copy(commentsEnabled = enabled) }
                _messages.tryEmit(if (enabled) "Comentarios activados" else "Comentarios desactivados")
            } catch (e: Exception) {
                _messages.tryEmit(e.friendlyMessage())
            }
        }
    }

    private fun updatePost(matchId: String, f: (FeedPost) -> FeedPost) {
        _ui.update { s -> s.copy(posts = s.posts.map { if (it.matchId == matchId) f(it) else it }) }
    }
}
