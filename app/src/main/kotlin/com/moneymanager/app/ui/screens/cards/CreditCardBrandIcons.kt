package com.moneymanager.app.ui.screens.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.ui.theme.OneUiTheme

/**
 * High-fidelity brand emblems for Indian credit card issuers matching the CRED dark aesthetic.
 */
@Composable
fun CreditCardBrandEmblem(
    bankName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val nameLower = bankName.lowercase()
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            nameLower.contains("hdfc") -> HdfcBrandEmblem(size)
            nameLower.contains("amazon") -> AmazonPayBrandEmblem(size)
            nameLower.contains("icici") -> IciciBrandEmblem(size)
            nameLower.contains("axis") -> AxisBrandEmblem(size)
            nameLower.contains("baroda") || nameLower.contains("bob") -> BankOfBarodaBrandEmblem(size)
            nameLower.contains("federal") -> FederalBankBrandEmblem(size)
            else -> GenericCardEmblem(size)
        }
    }
}

@Composable
fun HdfcBrandEmblem(size: Dp) {
    // HDFC signature: Blue rounded card with center red square cross
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF003366)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.58f)) {
            val s = this.size.width
            val barW = s * 0.30f

            // Red geometric grid cross
            drawRect(
                color = Color(0xFFED1C24),
                topLeft = Offset((s - barW) / 2f, 0f),
                size = Size(barW, s)
            )
            drawRect(
                color = Color(0xFFED1C24),
                topLeft = Offset(0f, (s - barW) / 2f),
                size = Size(s, barW)
            )
            // Center white square
            drawRect(
                color = Color.White,
                topLeft = Offset((s - barW * 0.7f) / 2f, (s - barW * 0.7f) / 2f),
                size = Size(barW * 0.7f, barW * 0.7f)
            )
        }
    }
}

@Composable
fun AmazonPayBrandEmblem(size: Dp) {
    // Amazon dark surface with curved orange smile arc
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E222D)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "a",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                fontSize = (size.value * 0.44f).sp
            )
            Canvas(modifier = Modifier.size(size * 0.45f, size * 0.16f)) {
                val cw = this.size.width
                val ch = this.size.height
                val path = Path().apply {
                    moveTo(0f, ch * 0.3f)
                    quadraticTo(
                        cw * 0.5f, ch,
                        cw, ch * 0.2f
                    )
                }
                drawPath(
                    path = path,
                    color = Color(0xFFFF9900),
                    style = Stroke(width = cw * 0.14f)
                )
            }
        }
    }
}

@Composable
fun IciciBrandEmblem(size: Dp) {
    // ICICI signature warm orange badge with stylized 'i'
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFB02A00)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size * 0.72f)
                .clip(RoundedCornerShape(size * 0.36f))
                .background(Color(0xFFF35C16)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "i",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif,
                fontSize = (size.value * 0.46f).sp
            )
        }
    }
}

@Composable
fun AxisBrandEmblem(size: Dp) {
    // Axis Bank signature burgundy maroon badge with triangular chevron
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF861F41)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.52f)) {
            val cw = this.size.width
            val ch = this.size.height
            val path = Path().apply {
                moveTo(cw * 0.5f, 0f)
                lineTo(cw, ch)
                lineTo(cw * 0.7f, ch)
                lineTo(cw * 0.5f, ch * 0.42f)
                lineTo(cw * 0.3f, ch)
                lineTo(0f, ch)
                close()
            }
            drawPath(path = path, color = Color.White)
        }
    }
}

@Composable
fun BankOfBarodaBrandEmblem(size: Dp) {
    // Bank of Baroda bright vermillion orange badge
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF26522)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "BOB",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.28f).sp
        )
    }
}

@Composable
fun FederalBankBrandEmblem(size: Dp) {
    // Federal Bank navy blue and gold shield badge
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D234A)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "FB",
            color = Color(0xFFFFD700),
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.34f).sp
        )
    }
}

@Composable
fun GenericCardEmblem(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OneUiTheme.colors.badgeIndigo),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.CreditCard,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}
