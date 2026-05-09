package family.loult.app.di

import org.koin.core.module.Module

/**
 * Per-platform bindings (Settings backing, future audio actuals).
 * Implementations live in androidMain / iosMain.
 */
expect fun platformModule(): Module
