package addict

import addict.exceptions.CircularDependencyDetectedException
import addict.exceptions.NoBindingFoundException
import addict.exceptions.PropertyDoesNotExistException
import javax.annotation.PostConstruct
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure

/**
 * Represents a module to separate environments within the Addict container.
 */
internal class AddictModule(private val properties: Map<String, Any>){
    /**
     * The bindings of interfaces to classes.
     */
    val bindings = mutableMapOf<KClass<*>, KClass<*>>()

    /**
     * Scoping of the binding classes.
     */
    val scopes = mutableMapOf<KClass<*>, Scope>()

    /**
     * Collection of singletons which get instantiated for the selected module.
     */
    val singletons = mutableMapOf<KClass<*>, Any>()

    /**
     * Track classes which are wired to a requested object for the circular dependency detection.
     */
    var seen = mutableSetOf<KClass<*>>()

    /**
     * Binding on module level.
     */
    fun <I : Any> bind(kInterface: KClass<I>, kClass: KClass<out I>, scope: Scope) {
        bindings[kInterface] = kClass
        scopes[kClass] = scope
    }

    /**
     * Entry point for the assembling to take place.
     */
    inline fun <reified T : Any> assemble(): T {
        // the circular dependency detection takes place for one instance
        seen.clear()

        // resolving needs to be done here because the singleton map contains the implementation
        val kClass = resolveBinding(T::class)
        if (scopes[kClass] == Scope.SINGLETON && kClass in singletons) {
            return singletons[kClass] as T
        }

        return assembleInternal(kClass)
    }

    /**
     * Uses recursion for building the dependency graph.
     * @param kClass By using the class we can avoid to inline the class and therefore not using private members.
     * @return The requested type with all the dependencies injected
     */
    fun <T : Any> assembleInternal(kClass: KClass<*>): T {
        val resolvedKClass = resolveBinding(kClass)

        if (resolvedKClass in seen) {
            throw CircularDependencyDetectedException("Detected a circular dependency: ${kClass.qualifiedName}")
        }

        seen.add(resolvedKClass)

        val constructor = resolvedKClass.primaryConstructor ?: resolvedKClass.constructors.first()
        val params = constructor.parameters

        // when no annotation or parameter is found we can create the instance without further work
        if (constructor.findAnnotation<Inject>() == null || params.isEmpty()) {
            return createInstance(constructor, emptyArray())
        }

        val args = params.map { assembleInternal<T>(it.type.jvmErasure) }
        return createInstance(constructor, (args as ArrayList).toArray())
    }

    /**
     * Creates an instance by calling the constructor of the given type T.
     * @param constructor The constructor to call
     * @param args The arguments to pass to the constructor
     */
    private fun <T : Any> createInstance(constructor: KFunction<*>, args: Array<Any>): T {
        val instance = constructor.call(*args) as T
        injectProperties(instance)
        executePostConstruct(instance)

        if (scopes[instance::class] == Scope.SINGLETON) {
            singletons[instance::class] = instance
        }

        return instance
    }

    /**
     * Inject properties to the [instance] annotated with [@Value].
     * @param instance The instance to inject the properties to
     */
    private fun <T : Any> injectProperties(instance: T) {
        instance::class
            .memberProperties
            // setter can only be called on mutable properties
            .filterIsInstance<KMutableProperty<*>>()
            .map { property -> property to property.findAnnotation<Value>() }
            .forEach { (property, annotation) ->
                property.isAccessible = true
                if (annotation?.value != null) {
                    if (properties[annotation.value] == null) {
                        throw PropertyDoesNotExistException(
                            "Property ${annotation.value} does not exist within the property source file")
                    }

                    property.setter.call(instance, properties[annotation.value])
                }
            }
    }

    /**
     * Executes the member function annotated with [@PostConstruct].
     * @param instance The instance to call the function for
     */
    private fun <T : Any> executePostConstruct(instance: T) {
        instance::class
            .memberFunctions
            .firstOrNull { function -> function.findAnnotation<PostConstruct>() != null }
            ?.call(instance)
    }

    /**
     * The interface to class relation is setup in the binding process.
     * Therefore by requesting the assembly of an object you would use the interface to work with but it may not always be the case.
     * Thus the assemble function can also work by requesting only the implementation.
     * @param kClass The class to check the binding for
     * @return The resolved class to continue work with
     */
    fun resolveBinding(kClass: KClass<*>): KClass<*> {
        isPartOfBindings(kClass)
        return bindings[kClass] ?: kClass
    }

    /**
     * Checks if [kClass] is part of the [bindings] map.
     * @param kClass The interface or class to check for
     */
    private fun isPartOfBindings(kClass: KClass<*>) {
        if (!bindings.containsKey(kClass) && !bindings.containsValue(kClass)) {
            throw NoBindingFoundException("No binding could be found for ${kClass.qualifiedName}")
        }
    }
}