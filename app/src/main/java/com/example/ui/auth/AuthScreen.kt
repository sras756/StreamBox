package com.example.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.ui.theme.BroadcastCyan
import com.example.ui.theme.ElectricCrimson
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceCard

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = onAuthSuccess,
      onUnauthenticated = {},
      scope = coroutineScope,
    )
  }

  Box(modifier = Modifier.fillMaxSize().background(ObsidianBg)) {
    Image(
      painter = painterResource(id = R.drawable.img_stream_hero),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
      alpha = 0.55f,
    )

    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors =
                listOf(
                  ObsidianBg.copy(alpha = 0.35f),
                  ObsidianBg.copy(alpha = 0.80f),
                  ObsidianBg,
                )
            )
          )
    )

    Column(
      modifier =
        Modifier.fillMaxSize()
          .windowInsetsPadding(WindowInsets.statusBars)
          .windowInsetsPadding(WindowInsets.navigationBars)
          .padding(horizontal = 24.dp, vertical = 28.dp)
          .widthIn(max = 520.dp)
          .align(Alignment.Center),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier =
            Modifier.size(44.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(
                Brush.linearGradient(listOf(ElectricCrimson, BroadcastCyan))
              ),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
          )
        }
        Spacer(Modifier.width(12.dp))
        Column {
          Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = "LIVE HD STREAMING STUDIO",
            style = MaterialTheme.typography.labelSmall,
            color = BroadcastCyan,
          )
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Surface(
          color = ElectricCrimson.copy(alpha = 0.16f),
          shape = CircleShape,
          modifier =
            Modifier.border(
              1.dp,
              ElectricCrimson.copy(alpha = 0.45f),
              CircleShape,
            ),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier.size(8.dp).clip(CircleShape).background(ElectricCrimson)
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = "1080P LIVE WATCH ROOMS",
              style = MaterialTheme.typography.labelMedium,
              color = Color.White,
            )
          }
        }

        Text(
          text = stringResource(R.string.auth_headline),
          style = MaterialTheme.typography.displaySmall,
          color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
          text = stringResource(R.string.auth_subtitle),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          FeaturePill(
            icon = Icons.Filled.Hd,
            label = "1080p MP4/DASH",
            modifier = Modifier.weight(1f),
          )
          FeaturePill(
            icon = Icons.Filled.ChatBubble,
            label = "Real-Time Chat",
            modifier = Modifier.weight(1f),
          )
          FeaturePill(
            icon = Icons.Filled.LiveTv,
            label = "Live Rooms",
            modifier = Modifier.weight(1f),
          )
        }

        if (errorMessage != null) {
          Surface(
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
            shape = MaterialTheme.shapes.small,
          ) {
            Text(
              text = errorMessage!!,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        Button(
          onClick = {
            errorMessage = null
            isLoading = true
            onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
              },
              onAuthError = { msg ->
                isLoading = false
                errorMessage = msg
              },
              scope = coroutineScope,
              onAuthCancelled = { isLoading = false },
            )
          },
          enabled = !isLoading,
          shape = MaterialTheme.shapes.medium,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = ElectricCrimson,
              contentColor = Color.White,
            ),
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("google_sign_in_button"),
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = Color.White,
              strokeWidth = 2.5.dp,
            )
          } else {
            Text(
              text = stringResource(R.string.sign_in_google),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun FeaturePill(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
  Surface(
    color = SurfaceCard.copy(alpha = 0.85f),
    shape = MaterialTheme.shapes.small,
    modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = BroadcastCyan,
        modifier = Modifier.size(20.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
      )
    }
  }
}
