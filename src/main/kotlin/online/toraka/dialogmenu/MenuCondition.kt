package online.toraka.dialogmenu

/** Every clause must hold; a clause naming an unresolved placeholder never holds. */
data class MenuCondition(val clauses: List<Clause>) {
    data class Clause(val name: String, val operator: String, val value: String) {
        fun matches(values: Map<String, String>): Boolean {
            val actual = values[name] ?: return false
            return when (operator) {
                "=" -> actual == value
                "!=" -> actual != value
                else -> {
                    val number = actual.toDoubleOrNull() ?: return false
                    val limit = value.toDouble()
                    when (operator) {
                        ">" -> number > limit
                        ">=" -> number >= limit
                        "<" -> number < limit
                        else -> number <= limit
                    }
                }
            }
        }

        /** `=` compares text, so a numeric match also implies that exact number. */
        internal fun range(): Range? {
            val number = value.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
            val infinity = Double.POSITIVE_INFINITY
            return when (operator) {
                "=" -> Range(number, false, number, false)
                ">" -> Range(number, true, infinity, true)
                ">=" -> Range(number, false, infinity, true)
                "<" -> Range(-infinity, true, number, true)
                "<=" -> Range(-infinity, true, number, false)
                else -> null
            }
        }
    }

    internal data class Range(
        val low: Double,
        val lowOpen: Boolean,
        val high: Double,
        val highOpen: Boolean,
    ) {
        fun contains(value: Double) =
            (value > low || value == low && !lowOpen) &&
                (value < high || value == high && !highOpen)

        fun intersects(other: Range): Boolean {
            val low = maxOf(this.low, other.low)
            val high = minOf(this.high, other.high)
            return low < high || low == high && contains(low) && other.contains(low)
        }
    }

    fun matches(values: Map<String, String>) = clauses.all { it.matches(values) }

    /** True only when no single set of values can satisfy both conditions. */
    fun excludes(other: MenuCondition) = clauses.any { a ->
        other.clauses.any { b -> a.name == b.name && disjoint(a, b) }
    }

    companion object {
        private val operators = listOf("!=", ">=", "<=", "=", ">", "<")

        fun clause(text: String): Clause? {
            val index = text.indexOfFirst { it in "!=<>" }
            if (index <= 0) return null
            val rest = text.substring(index)
            val operator = operators.firstOrNull { rest.startsWith(it) } ?: return null
            return Clause(
                text.substring(0, index).trim(),
                operator,
                rest.substring(operator.length).trim(),
            )
        }

        private fun disjoint(a: Clause, b: Clause): Boolean {
            if (a.operator == "=" && b.operator == "=") return a.value != b.value
            if (setOf(a.operator, b.operator) == setOf("=", "!=")) return a.value == b.value
            val first = a.range() ?: return false
            val second = b.range() ?: return false
            return !first.intersects(second)
        }
    }
}
