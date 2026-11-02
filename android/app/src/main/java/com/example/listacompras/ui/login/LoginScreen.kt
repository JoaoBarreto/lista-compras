package com.example.listacompras.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.listacompras.R
import com.example.listacompras.domain.repository.LoginError
import com.example.listacompras.ui.theme.ListaComprasTheme

@Composable
fun LoginRoute(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoginScreen(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onLoginClick = viewModel::onLoginClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Near-black at the bottom (cheap on OLED) with a warm glow from the icon's orange at the top.
    val dark = isSystemInDarkTheme()
    val backdrop = Brush.verticalGradient(
        if (dark) listOf(Color(0xFF3A1A0A), Color(0xFF140B06), Color(0xFF0B0907))
        else listOf(Color(0xFFFFD9BF), Color(0xFFFFEFE3), Color(0xFFFFF9F4)),
    )
    Scaffold(
        modifier = modifier.fillMaxSize().background(backdrop),
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                // Scrolls when the keyboard leaves too little room; still centred when there is plenty.
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Compact header while typing: the logo and tagline make way for the fields.
            val keyboardOpen = WindowInsets.isImeVisible
            if (!keyboardOpen) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(112.dp).clip(CircleShape).background(Color(0xFFFA5803)),
                ) {
                    // Decorative: the name below says the same. Oversized and cropped to fill the circle.
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.requiredSize(168.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            if (!keyboardOpen) {
                Text(
                    text = stringResource(R.string.login_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(if (keyboardOpen) 16.dp else 32.dp))
            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = { Text(stringResource(R.string.login_email_label)) },
                singleLine = true,
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            var showPassword by rememberSaveable { mutableStateOf(false) }
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.login_password_label)) },
                singleLine = true,
                enabled = !state.isLoading,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(
                            stringResource(
                                if (showPassword) R.string.login_password_hide else R.string.login_password_show,
                            ),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onLoginClick() }),
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(error.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onLoginClick,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(text = stringResource(R.string.login_button_enter))
                }
            }
        }
    }
}

private fun LoginError.messageRes(): Int = when (this) {
    LoginError.InvalidCredentials -> R.string.login_error_invalid_credentials
    LoginError.Network -> R.string.login_error_network
    LoginError.Unknown -> R.string.login_error_unknown
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    ListaComprasTheme {
        LoginScreen(
            state = LoginUiState(email = "ana@exemplo.pt", error = LoginError.Network),
            onEmailChange = {},
            onPasswordChange = {},
            onLoginClick = {},
        )
    }
}
