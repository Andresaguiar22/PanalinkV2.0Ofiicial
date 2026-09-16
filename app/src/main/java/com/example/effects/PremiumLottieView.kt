package com.example.effects

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * Carga una animación Lottie (vectorial, alta resolución) desde una URL
 * remota o un recurso `raw` local, en bucle.
 *
 * Si no hay fuente configurada o la composición falla al cargar, el
 * composable no dibuja nada: el [PremiumEffectView] procedural sigue
 * funcionando como fallback, garantizando zero-regression sin assets.
 */
@Composable
fun PremiumLottieView(
    lottieUrl: String?,
    lottieRawRes: String?,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center
) {
    val context = LocalContext.current

    val spec: LottieCompositionSpec? = when {
        !lottieUrl.isNullOrBlank() -> LottieCompositionSpec.Url(lottieUrl)
        !lottieRawRes.isNullOrBlank() -> {
            val resId = context.resources.getIdentifier(
                lottieRawRes.substringBeforeLast('.'),
                "raw",
                context.packageName
            )
            if (resId != 0) LottieCompositionSpec.RawRes(resId) else null
        }
        else -> null
    }
    if (spec == null) return

    val compositionResult = rememberLottieComposition(spec)
    val composition = compositionResult.value
    if (composition == null) return

    LottieAnimation(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        alignment = contentAlignment
    )
}