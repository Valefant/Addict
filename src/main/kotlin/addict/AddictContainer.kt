package addict

import addict.exceptions.SameBindingNotAllowedException
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
     * Reads properties from a source and makes them available to the container instance.
     * Property File Syntax: key=value
     * @param name The name of the property source
     */
    fun propertySource(name: String) {
        val text = this::class.java.classLoader.getResource(name)?.readText()
        text
            ?.lines()
            ?.forEach { line ->
                if (line.isEmpty() || line.trim().startsWith("#")) return@forEach
                val (key, value) = line.split("=").map { it.trim() }
                val resolved =
                    value
                        .split(" ")
                        .map {
                            if (it.startsWith("\${") && it.endsWith("}"))
                                properties[it.substringAfter("{").substringBefore("}")]
                            else
                                it
                        }
                        .joinToString(separator=" ")

                properties[key] = resolved
            }
    }

    /**
     * Modules provide a separation for the bindings.
     */
    private val modules = mutableMapOf<String, AddictModule>()

    /**
     * The current active module.
     */
    var activeModule: AddictModule = modules.getOrPut(DEFAULT_MODULE) { AddictModule(properties) }

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
     * @return The type T which you have specified
     */
    inline fun <reified T : Any> assemble(): T {
        return activeModule.assemble()
    }

    companion object {
        const val DEFAULT_MODULE = "default"
    }
}