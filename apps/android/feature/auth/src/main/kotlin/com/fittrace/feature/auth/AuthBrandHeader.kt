package com.fittrace.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fittrace.core.designsystem.R as DesignR

/** 邮箱与指纹共用品牌区；插画保持原始比例，键盘展开时收起。 */
@Composable
internal fun AuthBrandHeader(compact: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("循形Fitness", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            BoxWithConstraints {
                Text(
                    "记录一点，慢慢变好",
                    fontSize = if (maxWidth < 300.dp) 24.sp else 27.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "饮食、运动和身体变化，都记在这里",
                Modifier.padding(top = 6.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!compact)
            Image(
                painterResource(DesignR.drawable.login_illustration),
                null,
                Modifier.padding(top = 6.dp)
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 304.dp)
                    .fillMaxWidth()
                    .height(133.dp),
            )
        else Spacer(Modifier.height(16.dp))
    }
}
