package com.vanexa.supercalculator.presentation.size

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom Garment Icons for the Size Guide.
 * Each icon matches the visual identity of its garment type.
 */
object GarmentIcons {

    // ─── T-Shirt (existing, classic shape) ───
    val Tshirt: ImageVector
        get() = ImageVector.Builder(
            name = "Tshirt",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(21.99f, 7.99f)
                lineTo(17.0f, 3.0f)
                horizontalLineTo(7.0f)
                lineTo(2.01f, 7.99f)
                lineTo(4.0f, 10.0f)
                lineTo(6.0f, 8.0f)
                verticalLineToRelative(13.0f)
                horizontalLineToRelative(12.0f)
                verticalLineTo(8.0f)
                lineToRelative(2.0f, 2.0f)
                lineToRelative(1.99f, -2.01f)
                close()
            }
        }.build()

    // ─── Shirt (formal/dress shirt from Shirt.svg – 48x48 stroked) ───
    val Shirt: ImageVector
        get() = ImageVector.Builder(
            name = "Shirt",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 48f,
            viewportHeight = 48f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Main body: m9 9l9-5h12l9 5l4 15l-8 6v14H13V30l-8-6z
                moveTo(9f, 9f)
                lineToRelative(9f, -5f)
                horizontalLineToRelative(12f)
                lineToRelative(9f, 5f)
                lineToRelative(4f, 15f)
                lineToRelative(-8f, 6f)
                verticalLineToRelative(14f)
                horizontalLineTo(13f)
                verticalLineTo(30f)
                lineToRelative(-8f, -6f)
                close()
            }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Left detail: m13 31v-7
                moveTo(13f, 31f)
                verticalLineToRelative(-7f)
            }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Right detail: m35 31v-7
                moveTo(35f, 31f)
                verticalLineToRelative(-7f)
            }
        }.build()

    // ─── Polo Shirt (from game-icons--polo-shirt.svg – 512x512 filled) ───
    val PoloShirt: ImageVector
        get() = ImageVector.Builder(
            name = "PoloShirt",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 512f,
            viewportHeight = 512f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Main polo body
                moveTo(256f, 33.978f)
                curveToRelative(-12.67f, 0f, -25.34f, 2.52f, -38.01f, 7.535f)
                curveToRelative(1.27f, 3.61f, 3.864f, 7.593f, 8.373f, 12.102f)
                curveTo(232.68f, 59.93f, 242.613f, 66.83f, 256f, 73.847f)
                curveToRelative(13.386f, -7.018f, 23.32f, -13.916f, 29.637f, -20.232f)
                curveToRelative(4.51f, -4.51f, 7.103f, -8.492f, 8.373f, -12.102f)
                curveToRelative(-12.67f, -5.016f, -25.34f, -7.535f, -38.01f, -7.535f)
                // Collar & body left
                moveTo(201.88f, 49.1f)
                lineToRelative(-16.144f, 8.073f)
                curveToRelative(1.306f, 5.74f, 4.437f, 13.84f, 9.752f, 21.813f)
                curveToRelative(6.26f, 9.39f, 15.44f, 18.664f, 27.082f, 25.695f)
                lineToRelative(18.475f, -18.473f)
                curveToRelative(-11.317f, -6.394f, -20.465f, -12.923f, -27.408f, -19.866f)
                curveToRelative(-5.34f, -5.34f, -9.265f, -11.148f, -11.756f, -17.24f)
                close()
                // Collar & body right
                moveTo(310.12f, 49.1f)
                curveToRelative(-2.492f, 6.092f, -6.417f, 11.9f, -11.757f, 17.24f)
                curveToRelative(-6.943f, 6.944f, -16.09f, 13.473f, -27.408f, 19.867f)
                lineToRelative(18.475f, 18.474f)
                curveToRelative(11.64f, -7.03f, 20.823f, -16.305f, 27.082f, -25.694f)
                curveToRelative(5.315f, -7.973f, 8.446f, -16.072f, 9.752f, -21.813f)
                close()
                // Shoulders and main body
                moveTo(169.52f, 66.875f)
                lineToRelative(-36.918f, 9.23f)
                lineToRelative(-71.875f, 71.873f)
                lineTo(112f, 199.252f)
                lineToRelative(9.637f, -9.637f)
                lineToRelative(16.738f, -16.738f)
                lineTo(128f, 467.977f)
                curveToRelative(76.448f, 13.61f, 193.653f, 13.173f, 256f, 0f)
                lineToRelative(-10.375f, -295.1f)
                lineTo(400f, 199.252f)
                lineToRelative(51.273f, -51.274f)
                lineToRelative(-71.875f, -71.873f)
                lineToRelative(-36.92f, -9.23f)
                curveToRelative(-2.243f, 7.068f, -5.946f, 14.53f, -10.99f, 22.095f)
                curveToRelative(-8.768f, 13.152f, -21.853f, 26.254f, -39.463f, 35.06f)
                lineToRelative(-5.802f, 2.9f)
                lineTo(265f, 105.706f)
                verticalLineToRelative(122.27f)
                horizontalLineToRelative(-18f)
                verticalLineToRelative(-122.27f)
                lineToRelative(-21.223f, 21.222f)
                lineToRelative(-5.802f, -2.9f)
                curveToRelative(-17.61f, -8.806f, -30.695f, -21.908f, -39.463f, -35.06f)
                curveToRelative(-5.044f, -7.566f, -8.747f, -15.027f, -10.99f, -22.095f)
                close()
                // Sleeve detail left
                moveTo(48f, 160.705f)
                lineToRelative(-19.273f, 19.273f)
                lineTo(80f, 231.252f)
                lineToRelative(19.273f, -19.274f)
                close()
                // Sleeve detail right
                moveTo(464f, 160.705f)
                lineToRelative(-51.273f, 51.273f)
                lineTo(432f, 231.252f)
                lineToRelative(51.273f, -51.274f)
                close()
            }
        }.build()

    // ─── Hoodie (from hoodie.svg – 24x24 stroked) ───
    val Hoodie: ImageVector
        get() = ImageVector.Builder(
            name = "Hoodie",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Body
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5.998f, 12f)
                verticalLineToRelative(5.684f)
                curveToRelative(0f, 1.811f, 0f, 2.717f, 0.585f, 3.28f)
                curveToRelative(0.665f, 0.639f, 3.032f, 1.015f, 5.41f, 1.035f)
                curveToRelative(2.316f, 0.02f, 4.64f, -0.297f, 5.41f, -1.035f)
                curveToRelative(0.584f, -0.563f, 0.584f, -1.469f, 0.584f, -3.28f)
                verticalLineTo(12f)
            }
            // Hood & shoulders
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5.722f, 14.022f)
                curveToRelative(-0.865f, -0.314f, -2.492f, -0.583f, -3.226f, -1.319f)
                curveToRelative(-1.01f, -1.013f, -0.357f, -2.383f, 0.881f, -4.964f)
                curveToRelative(0.557f, -1.162f, 1.624f, -2.017f, 2.867f, -2.354f)
                curveToRelative(0.161f, -0.044f, 0.306f, -0.148f, 0.412f, -0.297f)
                lineToRelative(1.283f, -2f)
                curveToRelative(0.044f, -0.05f, 0.09f, -0.097f, 0.142f, -0.137f)
                curveTo(8.659f, 2.6f, 10.092f, 2f, 11.993f, 2f)
                reflectiveCurveToRelative(3.24f, 0.602f, 3.817f, 0.95f)
                curveToRelative(0.052f, 0.04f, 0.099f, 0.087f, 0.142f, 0.139f)
                lineToRelative(1.32f, 1.98f)
                curveToRelative(0.106f, 0.149f, 0.251f, 0.253f, 0.412f, 0.297f)
                curveToRelative(1.243f, 0.338f, 2.261f, 1.084f, 2.819f, 2.246f)
                curveToRelative(1.39f, 2.53f, 2.008f, 4.059f, 0.997f, 5.072f)
                curveToRelative(-0.733f, 0.736f, -2.383f, 1.028f, -3.248f, 1.341f)
            }
            // Hood strings & collar
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Hood V shape
                moveTo(6.501f, 5.5f)
                lineToRelative(4.234f, 3.293f)
                curveToRelative(0.606f, 0.471f, 0.909f, 0.707f, 1.266f, 0.707f)
                reflectiveCurveToRelative(0.66f, -0.236f, 1.267f, -0.707f)
                lineTo(17.5f, 5.5f)
                // Left string
                moveTo(9.501f, 3f)
                lineToRelative(1.5f, 6f)
                // Right string
                moveTo(14.501f, 3f)
                lineToRelative(-1.5f, 6f)
            }
        }.build()

    // ─── Sweater (from icon-park-outline--clothes-sweater.svg – 48x48 stroked) ───
    val Sweater: ImageVector
        get() = ImageVector.Builder(
            name = "Sweater",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 48f,
            viewportHeight = 48f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Right sleeve
                moveTo(37f, 19f)
                verticalLineToRelative(18f)
                // Bottom hem
                moveTo(11f, 37f)
                verticalLineToRelative(7f)
                horizontalLineToRelative(26f)
                verticalLineToRelative(-7f)
                // Left side with collar/sleeves
                moveTo(11f, 37f)
                horizontalLineTo(4f)
                verticalLineTo(12f)
                lineToRelative(5f, -5f)
                lineToRelative(7f, -3f)
                horizontalLineToRelative(16f)
                lineToRelative(7f, 3f)
                lineToRelative(5f, 5f)
                verticalLineToRelative(25f)
                horizontalLineToRelative(-7f)
                // Left side body
                moveTo(11f, 37f)
                verticalLineTo(19f)
                // Center line
                moveTo(24f, 21f)
                verticalLineToRelative(23f)
                // Shoulder lines
                moveTo(9f, 7f)
                lineToRelative(15f, 14f)
                moveTo(16f, 4f)
                lineToRelative(8f, 17f)
                moveTo(32f, 4f)
                lineToRelative(-8f, 17f)
                moveTo(39f, 7f)
                lineTo(24f, 21f)
            }
        }.build()

    // ─── Blazer/Jacket (from oxford-wave-blazer svg – simplified outline) ───
    val Blazer: ImageVector
        get() = ImageVector.Builder(
            name = "Blazer",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Left shoulder to body
                moveTo(8.5f, 2f)
                lineToRelative(-3f, 1.5f)
                lineToRelative(-2.5f, 5f)
                lineToRelative(-1f, 5f)
                lineToRelative(2f, 1.5f)
                verticalLineToRelative(7f)
                horizontalLineToRelative(16f)
                verticalLineToRelative(-7f)
                lineToRelative(2f, -1.5f)
                lineToRelative(-1f, -5f)
                lineToRelative(-2.5f, -5f)
                lineToRelative(-3f, -1.5f)
                // Collar right
                lineToRelative(-1.5f, 2f)
                lineTo(12f, 5f)
                // Collar left
                lineTo(10f, 4f)
                lineToRelative(-1.5f, -2f)
                close()
                // Lapel left
                moveTo(10f, 4f)
                lineToRelative(-1f, 3.5f)
                lineToRelative(3f, 4f)
                // Lapel right
                moveTo(14f, 4f)
                lineToRelative(1f, 3.5f)
                lineToRelative(-3f, 4f)
                // Center line
                moveTo(12f, 11.5f)
                verticalLineTo(22f)
                // Button
                moveTo(12f, 15f)
                lineToRelative(0.1f, 0f)
            }
        }.build()

    // ─── Trousers/Pants pattern (keep existing) ───
    val Pants: ImageVector
        get() = ImageVector.Builder(
            name = "Pants",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(18.0f, 2.0f)
                horizontalLineTo(6.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(16.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-9.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(9.0f)
                horizontalLineToRelative(4.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(4.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
            }
        }.build()

    // ─── Shorts pattern (keep existing) ───
    val Shorts: ImageVector
        get() = ImageVector.Builder(
            name = "Shorts",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(18.0f, 2.0f)
                horizontalLineTo(6.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(10.0f)
                horizontalLineToRelative(6.0f)
                verticalLineTo(8.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(6.0f)
                verticalLineTo(4.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
            }
        }.build()
}
