package addict

import addict.exceptions.EqualBindingNotAllowedException
import addict.exceptions.InterpolationException
import java.util.*
import kotlin.reflect.KClass

/**
 * Basic IoC container with module capabilities.
 */
class AddictContainer {
    /**
     * Contains the parsed keys and values of the property source file.
     */
    val properties = mutableMapOf<String, Any>()

    /**
     * Modules provide a separation for the bindings.
     */
    private val modules = mutableMapOf<String, AddictModule>()

    /**
     * The current active module.
     */
    private var activeModule: AddictModule = modules.getOrPut(DEFAULT_MODULE) { AddictModule(properties) }

    /**
     * Reads a properties file from the resource folder and makes them available to the container instance.
     * The default file to look for is "application.properties".
     * @param name The name of the property source
     */
    fun readPropertySource(name: String = "application.properties") {
        // kotlin let is used because we need the context of `this` for the classloader
        val unresolvedProperties = Properties().let {
            it.load(this::class.java.classLoader.getResourceAsStream(name))
            // can be safely casted as the initial keys and values are available as strings
            it.toMap() as Map<String, String>
        }

        unresolvedProperties
        .mapValues { property ->
            property
            .value
            .split(" ")
            .map { word->
                // matches following expressions ${example}, ${example.name} ...
                val regex = """\$\{(\w+(?:\.\w+)*)}""".toRegex()

                regex.find(word)?.let {
                    val variable = it.destructured.component1()
                    unresolvedProperties[variable] ?: InterpolationException("$variable does not exist in $name")
                } ?: word
            }.joinToString(" ")
        }.also { properties.putAll(it) }
    }

    /**
     * Changes the module of the current container instance.
     * If the modules does not exists it will be created.
     * @param name The name of the module to change or create
     */
    fun changeModule(name: String) {
        activeModule = modules.getOrPut(name) { AddictModule(properties) }
    }

    /**
     * Binds an interface to a class so it can be injected as dependency when assembling an object.
     * Use a different module if you want to wire different implementations
     * Note: Using the same binding with another implementation will overwrite it.
     * @param kInterface The interface to bind for the [activeModule]
     * @param kClass The class which implements the interface
     * @param properties The properties to inject into [kClass]
     * @param scope Scoping of the binding
     */
    fun <I : Any, C> bind(
        kInterface: KClass<I>,
        kClass: KClass<C>,
        properties: Map<String, Any> = emptyMap(),
        scope: Scope = Scope.SINGLETON
    ) where C : I {
        if (kInterface == kClass) {
            throw EqualBindingNotAllowedException("You cannot bind to the same instance of ${kInterface.qualifiedName}")
        }

        activeModule.bind(kInterface, kClass, properties, scope)
    }

    /**
     * Assembles an object with all its dependencies.
     * The type is provided within the angle brackets.
     * @return The type T which is specified
     */
    inline fun <reified T : Any> assemble(): T {
        return assemble(T::class)
    }

    /**
     * Assembles an object with all its dependencies.
     * @param kClass The class provides the type to assemble.
     * @return The type T which is specified
     */
    fun <T : Any> assemble(kClass: KClass<T>): T {
        return activeModule.assemble(kClass)
    }

    /**
     * CONSTANTS
     */
    companion object {
        const val DEFAULT_MODULE = "default"
    }
}