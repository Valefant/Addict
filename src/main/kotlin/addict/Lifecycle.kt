package addict

/**
 * Implementing this interface will execute the implemented functions
 * during the lifecycle of an object requested by [AddictContainer].
 */
interface Lifecycle {
    /**
     * Will be executed after an object was successfully assembled.
     * The entire object properties are in a valid state and thus can be requested.
     */
    fun postCreationHook()
}