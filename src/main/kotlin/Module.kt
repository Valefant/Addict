package addict

import javax.inject.Inject

class Module {
    /**
     * The bindings of interfaces to classes.
     */
    val bindings = mutableMapOf<Class<*>, Class<*>>()

    fun <I> bind(interfaze: Class<I>, clazz: Class<out I>) {
        bindings[interfaze] = clazz
    }

    inline fun <reified T> assemble(): T {
        return assembleInternal(T::class.java)
    }

    fun <T> assembleInternal(clazz: Class<*>): T {
        checkBindings(clazz)

        // the class to assemble does not have to be an interface
        val resolvedClazz = bindings[clazz] ?: clazz
        val constructors = resolvedClazz.constructors.filter{ it.isAnnotationPresent(Inject::class.java) }
        if (constructors.isEmpty()) {
            return resolvedClazz.getDeclaredConstructor().newInstance() as T
        }

        val params = constructors.first().parameterTypes
        val objects = params.map { assembleInternal<T>(it) }

        return constructors.first().newInstance(*(objects as ArrayList).toArray()) as T
    }

    private fun checkBindings(clazz: Class<*>) {
        if (!bindings.containsKey(clazz) && !bindings.containsValue(clazz)) {
            throw NoBindingFoundException("No binding could be found for ${clazz.canonicalName}")
        }
    }
}