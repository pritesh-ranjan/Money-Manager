package com.moneymanager.app.domain.statement.pdf

import kotlin.math.max
import kotlin.math.min

data class BoundingBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height

    fun contains(px: Float, py: Float): Boolean {
        return px in x..right && py in y..bottom
    }

    fun intersects(other: BoundingBox): Boolean {
        return x < other.right && right > other.x && y < other.bottom && bottom > other.y
    }

    companion object {
        fun fromPoints(minX: Float, minY: Float, maxX: Float, maxY: Float): BoundingBox {
            return BoundingBox(
                x = minX,
                y = minY,
                width = max(0f, maxX - minX),
                height = max(0f, maxY - minY)
            )
        }
    }
}

data class SpatialTextElement(
    val text: String,
    val bounds: BoundingBox,
    val fontSize: Float = 0f
)

data class SpatialLine(
    val elements: List<SpatialTextElement>,
    val bounds: BoundingBox
) {
    val text: String get() = elements.joinToString(" ") { it.text }.trim()

    val y: Float get() = bounds.y

    /**
     * Extracts text elements that fall horizontally within [minX, maxX].
     */
    fun getTextInRange(minX: Float, maxX: Float): String {
        return elements
            .filter { el ->
                val centerX = el.bounds.x + (el.bounds.width / 2f)
                centerX in minX..maxX
            }
            .joinToString(" ") { it.text }
            .trim()
    }

    /**
     * Returns true if any text element matches the provided regex or substring.
     */
    fun containsText(pattern: String, ignoreCase: Boolean = true): Boolean {
        return text.contains(pattern, ignoreCase = ignoreCase)
    }
}

data class SpatialPage(
    val pageNumber: Int,
    val width: Float,
    val height: Float,
    val lines: List<SpatialLine>
) {
    val fullText: String get() = lines.joinToString("\n") { it.text }

    fun findLines(predicate: (SpatialLine) -> Boolean): List<SpatialLine> {
        return lines.filter(predicate)
    }

    fun findFirstLine(predicate: (SpatialLine) -> Boolean): SpatialLine? {
        return lines.firstOrNull(predicate)
    }

    fun findLinesBetweenY(minY: Float, maxY: Float): List<SpatialLine> {
        return lines.filter { it.y in minY..maxY }
    }
}
