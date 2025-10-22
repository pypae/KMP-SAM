package ch.slf.whiterisk.kmpsam

/**
 * Platform-specific context provider.
 * Android needs Context, iOS doesn't need anything.
 */
expect object PlatformContext {
    /**
     * Gets the platform-specific context.
     * On Android: returns Application Context
     * On iOS: returns Unit
     */
    fun get(): Any
}

