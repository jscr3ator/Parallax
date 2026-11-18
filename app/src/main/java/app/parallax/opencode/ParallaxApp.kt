package app.parallax.opencode

import android.app.Application
import app.parallax.opencode.core.data.ServerStore
import app.parallax.opencode.core.data.live.LiveAgentRepository
import app.parallax.opencode.core.net.ServerAuth
import app.parallax.opencode.core.repo.AgentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual DI: one instance of each long-lived object, reachable from anywhere via [ParallaxApp.container]. */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val store = ServerStore(app)
    val repository: AgentRepository by lazy { LiveAgentRepository(app, store, appScope) }
}

class ParallaxApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        container.appScope.launch { ServerAuth.dedupeKey = container.store.deviceKey() }
    }

    companion object {
        lateinit var instance: ParallaxApp
            private set
        val container: AppContainer get() = instance.container
    }
}
