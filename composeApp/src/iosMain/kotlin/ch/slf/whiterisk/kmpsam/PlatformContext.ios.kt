package ch.slf.whiterisk.kmpsam

/**
 * iOS implementation of PlatformContext.
 * iOS doesn't need context, so we return Unit.
 */
actual object PlatformContext {
    /**
     * Returns Unit (iOS doesn't need context).
     */
    actual fun get(): Any = Unit
}

