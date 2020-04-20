package org.c_3po.util

/**
 * Utility class for common string operations.
 * A lot of third-party substitutes are available (e.g. apache commons' StringUtils)
 * but by now it's not worth to drag in an additional dependency.
 */
object StringUtils {
    @JvmStatic
    fun isBlank(s: String?) = s == null || s.trim().isEmpty()

    /**
     * Joins the passed strings with the given delimiter but trims delimiter occurences
     * in input strings to ensure that only one delimiter instance is between two
     * consecutive strings
     *
     * @param delimiter
     * @param strings
     * @return a string resulting from joining strings with the given delimiter without duplicate delimiters
     */
    @JvmStatic
    fun trimmedJoin(delimiter: String, vararg strings: String) = strings
            .map {
                it.removePrefix(delimiter).removeSuffix(delimiter)
            }.joinToString(delimiter)
}
