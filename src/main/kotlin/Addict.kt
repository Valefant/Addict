package addict

/**
 * Basic IoC container
 */
class Addict {
    /**
     * Modules provide a separation for the bindings.
     */
    private val modules = mutableMapOf<String, Module>()

    /**
     * The current active module.
     */
    var activeModule: Module = modules.getOrPut(DEFAULT_MODULE) { Module() }

    /**
     * Changes the module of the current container instance.
     * If the modules does not exists it will be created.
     * @param name The name of the module to change or create
     */
    fun changeModule(name: String) {
        activeModule = modules.getOrPut(name) { Module() }
    }

    /**
     * Binds an interface to a class so it can be injected as dependency when assembling an object.
     * @param interfaze The interface to bind for the [activeModule]
     * @param clazz The class which implements the interface
     */
    fun <I> bind(interfaze: Class<I>, clazz: Class<out I>, scope: Scope = Scope.SINGLETON) {
        activeModule.bind(interfaze, clazz)
    }

    /**
     * Assembles an object with all its dependencies.
     * @return The type which you have specified.
     */
    inline fun <reified T> assemble(): T {
        return activeModule.assemble()
    }

    companion object {
        const val DEFAULT_MODULE = "default"
    }
}