package addict

/**
 * Implementing this interfaces will execute methods during the lifecycle of an object requested by the [AddictContainer].
 */
interface Lifecycle {
    /**
     * Will be executed after an object was successfully assembled.
     * The entire object properties are in a valid state and thus can be requested.
     */
    fun postCreationHook()
}