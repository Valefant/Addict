package addict

/**
 * The instances managed by the container can have different user managed scopes.
 */
enum class Scope {
    SINGLETON,
    NEW_INSTANCE
}