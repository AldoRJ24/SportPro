package com.sportpro.app.core

import android.content.Context
import com.sportpro.app.SupabaseProvider
import com.sportpro.app.core.data.offline.PendingEventQueue
import com.sportpro.app.core.data.repo.AccountRepository
import com.sportpro.app.core.data.repo.AuthRepository
import com.sportpro.app.core.data.repo.ClubRepository
import com.sportpro.app.core.data.repo.FeedRepository
import com.sportpro.app.core.data.repo.MatchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

 /**
  * Contenedor de dependencias manual (simple y explícito; sin anotaciones ni Hilt).
  * Una sola instancia por proceso, creada en [com.sportpro.app.SportProApp].
  * Las pantallas lo obtienen con LocalAppContainer y se lo pasan a sus ViewModels.
  * appScope vive más que las pantallas: la cola sin conexión sigue enviando aunque cierres el partido.
  */
class AppContainer(context: Context) {
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = SupabaseProvider.client

    val auth: AuthRepository = AuthRepository(client)
    val club: ClubRepository = ClubRepository(client)
    val matches: MatchRepository = MatchRepository(client)
    val account: AccountRepository = AccountRepository(client)
    val feed: FeedRepository = FeedRepository(client)
    val queue: PendingEventQueue = PendingEventQueue(context.applicationContext, matches, appScope)
}
