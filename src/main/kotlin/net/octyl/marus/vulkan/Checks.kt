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

package net.octyl.marus.vulkan

import org.lwjgl.vulkan.VK10.VK_SUCCESS
import org.slf4j.Logger

val VK_STD_SUCCESS_CODES = setOf(VK_SUCCESS)

inline fun checkedCreate(description: () -> String, successCodes: Set<Int> = VK_STD_SUCCESS_CODES, block: () -> Int) =
    checkedAction({ "create ${description()}" }, successCodes, block)

inline fun checkedGet(description: () -> String, successCodes: Set<Int> = VK_STD_SUCCESS_CODES, block: () -> Int) =
    checkedAction({ "get ${description()}" }, successCodes, block)

inline fun checkedAction(action: () -> String, successCodes: Set<Int> = VK_STD_SUCCESS_CODES, block: () -> Int): Int {
    val err = block()
    check(err in successCodes) {
        failureMessage(action(), err)
    }
    return err
}

inline fun Logger.logFailure(action: () -> String, successCodes: Set<Int> = VK_STD_SUCCESS_CODES, block: () -> Int): Int? {
    val err = block()
    if (err !in successCodes) {
        warn(failureMessage(action(), err))
        return err
    }
    return null
}

@PublishedApi
@OptIn(ExperimentalUnsignedTypes::class)
internal fun failureMessage(action: String, error: Int): String {
    var hex = error.toString(radix = 16)
    if (hex[0] == '-') {
        hex = "-0x${hex.substring(1)}"
    }
    return "Failed to $action: $error ($hex)"
}
