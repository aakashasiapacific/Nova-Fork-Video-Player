package com.aakash.novafork.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * SF Symbols–style glyphs that res/drawable does not have, drawn at SF weights on a 24×24 grid.
 * Use with Icon(NovaGlyphs.X, contentDescription, tint = …) or the glass buttons' ImageVector
 * overloads. Everything else comes from the Material Round drawables (docs/ARCHITECTURE.md).
 */
object NovaGlyphs {
    private fun build(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private val ink = SolidColor(Color.Black)

    /** person.fill: the account button. */
    val Person: ImageVector = build("person") {
        path(fill = ink) {
            moveTo(7.6f, 7.4f)
            arcTo(4.4f, 4.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 16.4f, 7.4f)
            arcTo(4.4f, 4.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 7.6f, 7.4f)
            close()
            moveTo(3.6f, 20.2f)
            curveTo(3.6f, 16.4f, 7.3f, 13.9f, 12f, 13.9f)
            curveTo(16.7f, 13.9f, 20.4f, 16.4f, 20.4f, 20.2f)
            curveTo(20.4f, 21f, 19.9f, 21.4f, 19.1f, 21.4f)
            lineTo(4.9f, 21.4f)
            curveTo(4.1f, 21.4f, 3.6f, 21f, 3.6f, 20.2f)
            close()
        }
    }

    /** chevron.left (back). */
    val Back: ImageVector = build("chevron.left") {
        path(stroke = ink, strokeLineWidth = 2.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(15f, 4.5f)
            lineTo(7.5f, 12f)
            lineTo(15f, 19.5f)
        }
    }

    /** chevron.right: row headers, navigable list rows. */
    val ChevronRight: ImageVector = build("chevron.right") {
        path(stroke = ink, strokeLineWidth = 2.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(9f, 4.5f)
            lineTo(16.5f, 12f)
            lineTo(9f, 19.5f)
        }
    }

    /** chevron.down: pull-down buttons ("Sort ▾", "Season 1 ▾"). */
    val ChevronDown: ImageVector = build("chevron.down") {
        path(stroke = ink, strokeLineWidth = 2.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4.5f, 9f)
            lineTo(12f, 16.5f)
            lineTo(19.5f, 9f)
        }
    }

    /** ellipsis: "more" menus. */
    val Ellipsis: ImageVector = build("ellipsis") {
        path(fill = ink) {
            dot(5f, 12f, 1.9f)
            dot(12f, 12f, 1.9f)
            dot(19f, 12f, 1.9f)
        }
    }

    /** plus. */
    val Plus: ImageVector = build("plus") {
        path(stroke = ink, strokeLineWidth = 2.4f, strokeLineCap = StrokeCap.Round) {
            moveTo(12f, 4.5f)
            lineTo(12f, 19.5f)
            moveTo(4.5f, 12f)
            lineTo(19.5f, 12f)
        }
    }

    /** magnifyingglass: Search tab, search field. */
    val Search: ImageVector = build("magnifyingglass") {
        path(stroke = ink, strokeLineWidth = 2.4f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(3.8f, 10.4f)
            arcTo(6.6f, 6.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, 17f, 10.4f)
            arcTo(6.6f, 6.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3.8f, 10.4f)
            moveTo(15.2f, 15.2f)
            lineTo(20.4f, 20.4f)
        }
    }

    /** checkmark: selected rows and menu items. */
    val Check: ImageVector = build("checkmark") {
        path(stroke = ink, strokeLineWidth = 2.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4.5f, 12.8f)
            lineTo(9.6f, 18f)
            lineTo(19.5f, 6.5f)
        }
    }

    /** xmark: close buttons. */
    val Close: ImageVector = build("xmark") {
        path(stroke = ink, strokeLineWidth = 2.6f, strokeLineCap = StrokeCap.Round) {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }

    /** play.fill with rounded corners: primary buttons. */
    val Play: ImageVector = build("play.fill") {
        path(
            fill = ink,
            stroke = ink,
            strokeLineWidth = 2.2f,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(7.2f, 4.6f)
            lineTo(19.4f, 12f)
            lineTo(7.2f, 19.4f)
            close()
        }
    }

    /** star.fill: ratings. */
    val Star: ImageVector = build("star.fill") {
        path(fill = ink, stroke = ink, strokeLineWidth = 1.2f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 2.8f)
            lineTo(14.7f, 8.9f)
            lineTo(21.2f, 9.5f)
            lineTo(16.3f, 13.9f)
            lineTo(17.7f, 20.4f)
            lineTo(12f, 17.1f)
            lineTo(6.3f, 20.4f)
            lineTo(7.7f, 13.9f)
            lineTo(2.8f, 9.5f)
            lineTo(9.3f, 8.9f)
            close()
        }
    }

    /** info.circle: details / information buttons. */
    val Info: ImageVector = build("info.circle") {
        path(stroke = ink, strokeLineWidth = 2f) {
            moveTo(2.8f, 12f)
            arcTo(9.2f, 9.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 21.2f, 12f)
            arcTo(9.2f, 9.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 2.8f, 12f)
        }
        path(stroke = ink, strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round) {
            moveTo(12f, 11f)
            lineTo(12f, 16.5f)
        }
        path(fill = ink) {
            dot(12f, 7.6f, 1.35f)
        }
    }
}

/** A filled circle as two half arcs (PathBuilder has no circle primitive). */
private fun androidx.compose.ui.graphics.vector.PathBuilder.dot(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, cx + r, cy)
    arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, cx - r, cy)
    close()
}
