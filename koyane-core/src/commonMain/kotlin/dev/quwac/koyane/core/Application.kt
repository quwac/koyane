package dev.quwac.koyane.core

/** Plugin keys use instance identity; retain the instance for installation and lookup. */
interface Plugin<Configuration : Any, Extension : Any> {
    /** Creates a fresh configuration for one installation, before the application's configuration block runs. */
    fun createConfiguration(): Configuration

    /** Builds the extension from [configuration]; failures abort installation and invalidate the builder. */
    fun install(configuration: Configuration): Extension

    /** Runs once after storing the extension; dependencies must already be installed. */
    fun onInstall(scope: PluginScope, extension: Extension) {}
}

internal class InstalledPlugin<C : Any, E : Any>(val plugin: Plugin<C, E>, val extension: E)

/** A configured application whose installed extensions are retrieved by plugin instance identity. */
class Application internal constructor(private val extensions: List<InstalledPlugin<*, *>>) {
    /** Application-wide typed values shared by plugins and calls. */
    val attributes = Attributes()

    /**
     * Returns the extension installed with this exact [plugin] instance.
     *
     * @throws IllegalStateException if [plugin] is not installed.
     */
    @Suppress("UNCHECKED_CAST") // The same invariant plugin instance associates insertion and lookup types.
    operator fun <C : Any, E : Any> get(plugin: Plugin<C, E>): E =
        checkNotNull(extensions.firstOrNull { it.plugin === plugin }?.extension) { "Plugin is not installed" } as E
}

/** Protocols can expose installation hooks as extensions on this scope. */
class PluginScope internal constructor(private val owner: Application) {
    private var active = true

    /** The application being configured; access after the installation hook returns throws [IllegalStateException]. */
    val application: Application
        get() {
            check(active) { "Plugin installation scope is closed" }
            return owner
        }

    internal fun close() {
        active = false
    }
}

/** Installs plugins in order during [koyane]; the builder closes when configuration completes or installation fails. */
class ApplicationBuilder internal constructor() {
    private val extensions = mutableListOf<InstalledPlugin<*, *>>()
    private val installing = mutableListOf<Plugin<*, *>>()
    private val application = Application(extensions)
    private var active = true

    /** The attribute store of the application being built. */
    val attributes: Attributes get() = application.attributes

    /**
     * Configures and installs [plugin], then runs its installation hook with the stored extension available.
     * Dependencies used by the hook must already be installed.
     * Exceptions from configuration, plugin installation, or the hook close this builder.
     *
     * @throws IllegalStateException if the builder is closed or this plugin instance is already being installed or installed.
     */
    @Suppress("TooGenericExceptionCaught") // Any failed installation invalidates the builder.
    fun <C : Any, E : Any> install(plugin: Plugin<C, E>, configure: C.() -> Unit = {}) {
        check(active) { "Application builder is closed" }
        check(extensions.none { it.plugin === plugin } && installing.none { it === plugin }) {
            "Plugin is already installed"
        }
        installing += plugin
        val scope = PluginScope(application)
        try {
            val configuration = plugin.createConfiguration().apply(configure)
            val extension = plugin.install(configuration)
            extensions += InstalledPlugin(plugin, extension)
            plugin.onInstall(scope, extension)
        } catch (cause: Throwable) {
            active = false
            throw cause
        } finally {
            scope.close()
            installing.removeAll { it === plugin }
        }
    }

    internal fun build(): Application {
        check(active) { "Application builder is closed" }
        active = false
        return application
    }
}

/** Builds an application by running [configure] once; configuration and installation failures propagate to the caller. */
fun koyane(configure: ApplicationBuilder.() -> Unit): Application = ApplicationBuilder().apply(configure).build()
