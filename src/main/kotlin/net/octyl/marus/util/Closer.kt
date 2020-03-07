/*
 * Copyright (c) Octavia Togami <https://octyl.net>
 * Copyright (c) contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.octyl.marus.util

fun <R> closer(block: CloserScope.() -> R): R {
    val scope = CloserScope()
    try {
        return scope.run(block).also {
            scope.close()?.let { throw it }
        }
    } catch (e: Throwable) {
        scope.thrownException = e
        throw scope.close()!!
    }
}

class CloserScope {

    private val resources = mutableSetOf<AutoCloseable>()
    @PublishedApi
    internal var thrownException: Throwable? = null

    fun <T : AutoCloseable> register(closeable: T): T {
        resources.add(closeable)
        return closeable
    }

    fun <T> register(resource: T, cleanup: (T) -> Unit): T {
        register(AutoCloseable { cleanup(resource) })
        return resource
    }

    @JvmName("registerExt")
    fun <T : AutoCloseable> T.register() = register(this)

    @JvmName("registerExt")
    fun <T> T.register(cleanup: (T) -> Unit) = register(this, cleanup)

    @PublishedApi
    internal fun close(): Throwable? {
        var error: Throwable? = thrownException
        for (resource in resources) {
            try {
                resource.close()
            } catch (e: Throwable) {
                when (error) {
                    null -> error = e
                    else -> error.addSuppressed(e)
                }
            }
        }
        return error
    }

}
