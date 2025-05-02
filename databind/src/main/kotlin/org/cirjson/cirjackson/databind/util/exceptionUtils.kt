package org.cirjson.cirjackson.databind.util

/**
 * It is important never to catch all `Throwables`. Some like [InterruptedException] should be rethrown.
 *
 * This method should be used with care.
 *
 * If the `Throwable` is fatal, it is rethrown; otherwise, this method just returns. The input throwable is thrown if it
 * is an `Error` or a `RuntimeException`. Otherwise, the method wraps the throwable in a `RuntimeException` and throws
 * that.
 *
 * @throws Error the input throwable if it is fatal and an instance of `Error`.
 *
 * @throws RuntimeException the input throwable if it is fatal - throws the original throwable if it is a
 * `RuntimeException`. Otherwise, wraps the throwable in a `RuntimeException`.
 */
@Throws(Error::class, RuntimeException::class)
fun Throwable.rethrowIfFatal() {
    if (!isFatal) {
        return
    }

    if (this is Error) {
        throw this
    }

    if (this is RuntimeException) {
        throw this
    }

    throw RuntimeException(this)
}

/**
 * It is important never to catch all `Throwables`. Some like [InterruptedException] should be rethrown.
 *
 * @return whether the `Throwable` is a fatal error
 */
private val Throwable.isFatal: Boolean
    get() = this is VirtualMachineError || this is ThreadDeath || this is InterruptedException ||
            this is ClassCircularityError || this is ClassFormatError || this is IncompatibleClassChangeError ||
            this is BootstrapMethodError || this is VerifyError


