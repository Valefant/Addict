package addict

import addict.exceptions.CircularDependencyDetectedException
import addict.exceptions.NoBindingFoundException
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

/**
 * Represents a module to separate environments within the Addict container.
 */
internal class AddictModule(private val properties: Map<String, Any>){
    /**
     * The bindings of interfaces to classes.
     */
    private val bindings = mutableMapOf<KClass<*>, KClass<*>>()

    /**
     * Scoping of the binding classes.
     */
    private val scopes = mutableMapOf<KClass<*>, Scope>()

    /**
     * Collection of singletons which get instantiated for the selected module.
     */
    private val singletons = mutableMapOf<KClass<*>, Any>()

    /**
     * Collection of properties which are injected during the assemble process of the requested object.
     */
    private val propertiesForClass = mutableMapOf<KClass<*>, Map<String, Any>>()

    /**
     * Track classes which are wired to a requested object for the circular dependency detection.
     */
    private var seen = mutableSetOf<KClass<*>>()

    /**
     * Binding on module level.
     */
    fun <I : Any, C> bind(
        kInterface: KClass<I>,
        kClass: KClass<C>,
        properties: Map<String, Any>,
        scope: Scope
    ) where C : I {
        bindings[kInterface] = kClass
        propertiesForClass[kClass] = properties
        scopes[kClass] = scope
    }

    /**
     * Entry point for the assembling to take place.
     */
    fun <T : Any> assemble(kClass: KClass<T>): T {
        // the circular dependency detection takes place for each request
        seen.clear()

        // resolving needs to be done here because the singleton map could already contain the implementation
        val resolvedRootClass = resolveBinding(kClass)

        return if (scopes[resolvedRootClass] == Scope.SINGLETON && resolvedRootClass in singletons)
            singletons[resolvedRootClass] as T
        else
            assembleInternal(resolvedRootClass)
    }

    /**
     * Uses recursion for building the dependency graph.
     * @param kClass By using the class we can avoid to inline the class and therefore not using private members.
     * @return The requested type with all the dependencies injected
     */
    private fun <T : Any> assembleInternal(kClass: KClass<*>): T {
        // the resolving needs here still to be done because of the recursion
        val resolvedKClass = resolveBinding(kClass)

        if (resolvedKClass in seen) {
            throw CircularDependencyDetectedException("Detected a circular dependency: ${kClass.qualifiedName}")
        }

        seen.add(resolvedKClass)

        val constructor = resolvedKClass.primaryConstructor ?: resolvedKClass.constructors.first()
        val params = constructor.parameters
        val args = params
            .associateWith { param ->
                propertiesForClass[resolvedKClass]?.let { properties ->
                    val value = properties[param.name]
                    if (param.isOptional) {
                        /**
                         * Optional parameters already have a value or we provide one.
                         * `null` is in this case a valid value.
                         * Therefore we have return to the caller lambda else the recursion will continue
                         */
                        return@associateWith value
                    }
                    value
                } ?: assembleInternal<T>(param.type.jvmErasure)
            }
            .filterValues { it != null }
        return instantiate(constructor, args)
    }

    /**
     * Creates an instance by calling the constructor of the given type T.
     * Optional and mismatching values are handled by [KFunction.callBy].
     * @param constructor The constructor to call
     * @param args A map containing the parameter types and their respective values to pass to the constructor
     */
    private fun <T : Any> instantiate(constructor: KFunction<*>, args: Map<KParameter, Any?>): T {
        val instance = constructor.callBy(args) as T
        invokePostCreationHook(instance)

        val kClass = instance::class
        if (scopes[kClass] == Scope.SINGLETON) {
            singletons[kClass] = instance
        }

        return instance
    }

    /**
     * Invokes the member function [Lifecycle.postCreationHook]
     * @param instance The instance to call the function for
     */
    private fun <T : Any> invokePostCreationHook(instance: T) {
        instance::class
        .memberFunctions
        .firstOrNull { function -> function.name == "postCreationHook" }
        // the instance is the first parameter of the function call
        ?.call(instance)
    }

    /**
     * The interface and the implementing class is setup in the binding process.
     * Therefore by requesting the assembly of an object you would use the interface to work with but it may not always be the case.
     * Thus the assemble function can also work by requesting only the implementation.
     * @param kClass The class to resolve the binding for
     * @return The resolved class to continue work with
     */
    private fun resolveBinding(kClass: KClass<*>): KClass<*> {
        val resolvedClass = bindings[kClass] ?: kClass
        if (resolvedClass.isInterface()) {
            throw NoBindingFoundException("No binding could be found for ${resolvedClass.qualifiedName}")
        }
        return resolvedClass
    }

    /**
     * Convenient interface check for kotlin classes
     */
    private fun KClass<*>.isInterface() = this.java.isInterface
}