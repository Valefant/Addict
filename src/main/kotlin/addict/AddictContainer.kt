package addict

import addict.exceptions.InterpolationException
import addict.exceptions.SameBindingNotAllowedException
import java.util.*
import kotlin.reflect.KClass

/**
 * Basic IoC container
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
     * Reads properties from a source and makes them available to the container instance.
     * Property File Syntax: key=value
     * @param name The name of the property source
     */
    fun propertySource(name: String) {
        // kotlins let is used because we need the context of `this` for the classloader
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
                // matches following expressions ${example} ${example.name}
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
     * @param scope Scope of the binding
     */
    fun <I : Any> bind(kInterface: KClass<I>, kClass: KClass<out I>, scope: Scope = Scope.SINGLETON) {
        if (kInterface == kClass) {
            throw SameBindingNotAllowedException("You cannot bind to the same instance of ${kInterface.qualifiedName}")
        }

        activeModule.bind(kInterface, kClass, scope)
    }

    /**
     * Assembles an object with all its dependencies.
     * @return The type T which is specified
     */
    inline fun <reified T : Any> assemble(): T {
        return assemble(T::class)
    }

    /**
     * Assembles an object with all its dependencies.
     * @param kClass The class to assemble
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