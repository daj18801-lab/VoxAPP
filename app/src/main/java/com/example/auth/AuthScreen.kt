package com.example.auth

import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.AppConfig
import com.example.security.DeviceBanManager
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald

@Composable
fun AuthScreen(
    appConfig: AppConfig = AppConfig(),
    trialNoticeMessage: String? = null,
    pendingAdminRelogin: Boolean = false,
    onAdminReloginHandled: () -> Unit = {},
    onAuthSuccess: () -> Unit,
    onTrialLoginStarted: (TrialSession) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var isCheckingAutoSignIn by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 0 = Personal, 1 = Business
    var selectedAccountMode by remember { mutableIntStateOf(0) }
    var showBusinessRegisterPage by remember { mutableStateOf(false) }

    var trialNameInput by remember {
        mutableStateOf(TrialSessionManager.loadSession(context)?.displayName.orEmpty())
    }
    var trialError by remember { mutableStateOf<String?>(null) }

    // Business -> Employee Company Code + PIN + First & Last Name inputs
    var empFirstName by remember { mutableStateOf("") }
    var empLastName by remember { mutableStateOf("") }
    var empCompanyCode by remember { mutableStateOf("") }
    var empCompanyPin by remember { mutableStateOf("") }
    var empError by remember { mutableStateOf<String?>(null) }

    // Business Registration ($99.99/mo) page inputs
    var bizEmailInput by remember { mutableStateOf("") }
    var bizCompanyNameInput by remember { mutableStateOf("") }
    var bizCompanyCodeInput by remember { mutableStateOf("") }
    var bizCompanyPinInput by remember { mutableStateOf("") }
    var bizRegisterError by remember { mutableStateOf<String?>(null) }

    val hasExpiredTrial = remember(context, trialNoticeMessage) {
        TrialSessionManager.hasTrialExpired(context)
    }
    val isLocallyDeviceBanned = remember(context) {
        DeviceBanManager.isLocallyDeviceBanned(context)
    }
    val localDeviceBanReason = remember(context) {
        DeviceBanManager.getLocalDeviceBanReason(context)
    }

    LaunchedEffect(pendingAdminRelogin) {
        if (pendingAdminRelogin) {
            isCheckingAutoSignIn = false
            isLoading = true
            onAdminReloginHandled()
            onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    isLoading = false
                    TrialSessionManager.deactivateTrial(context)
                    onAuthSuccess()
                },
                onAuthError = { msg ->
                    isLoading = false
                    errorMessage = msg
                },
                scope = scope,
                onAuthCancelled = {
                    isLoading = false
                }
            )
        } else {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    isCheckingAutoSignIn = false
                    onAuthSuccess()
                },
                onUnauthenticated = {
                    isCheckingAutoSignIn = false
                },
                scope = scope
            )
        }
    }

    if (showBusinessRegisterPage) {
        BackHandler { showBusinessRegisterPage = false }
        BusinessAccountRegistrationPage(
            bizEmail = bizEmailInput,
            onBizEmailChange = {
                bizEmailInput = it.take(120)
                bizRegisterError = null
            },
            companyName = bizCompanyNameInput,
            onCompanyNameChange = {
                bizCompanyNameInput = it.take(60)
                bizRegisterError = null
            },
            companyCode = bizCompanyCodeInput,
            onCompanyCodeChange = {
                bizCompanyCodeInput = SubAccountLoginPrefs.normalizeCompanyCode(it)
                bizRegisterError = null
            },
            companyPin = bizCompanyPinInput,
            onCompanyPinChange = {
                bizCompanyPinInput = SubAccountLoginPrefs.normalizePin(it)
                bizRegisterError = null
            },
            isLoading = isLoading,
            errorMessage = bizRegisterError ?: errorMessage,
            onBack = { showBusinessRegisterPage = false },
            onSubmitPayment = {
                val cleanEmail = bizEmailInput.trim()
                val cleanCompany = bizCompanyNameInput.trim()
                val cleanCode = SubAccountLoginPrefs.normalizeCompanyCode(bizCompanyCodeInput)
                val cleanPin = SubAccountLoginPrefs.normalizePin(bizCompanyPinInput)
                when {
                    cleanEmail.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() -> {
                        bizRegisterError = "Please enter a valid business email address so the Business Account can go under it."
                    }
                    cleanCompany.length < 2 -> {
                        bizRegisterError = "Please enter your Company / Business name."
                    }
                    cleanCode.isNotEmpty() && cleanCode.length < 4 -> {
                        bizRegisterError = "Company Code must be at least 4 letters/numbers (or leave blank to set later)."
                    }
                    cleanPin.isNotEmpty() && cleanPin.length < 4 -> {
                        bizRegisterError = "Company PIN must be at least 4 digits/characters (or leave blank to set later)."
                    }
                    (cleanCode.isNotEmpty() && cleanPin.isEmpty()) || (cleanCode.isEmpty() && cleanPin.isNotEmpty()) -> {
                        bizRegisterError = "Provide both a Company Code and a PIN together, or leave both blank to set after checkout."
                    }
                    else -> {
                        bizRegisterError = null
                        errorMessage = null
                        SubAccountLoginPrefs.savePendingBusinessRegistration(
                            context = context,
                            businessEmail = cleanEmail,
                            companyName = cleanCompany,
                            companyCode = cleanCode,
                            pin = cleanPin
                        )
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                TrialSessionManager.deactivateTrial(context)
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                bizRegisterError = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            }
                        )
                    }
                }
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Atmospheric hero studio artwork at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_lounge_hero_1795298732457),
                contentDescription = "VOX live broadcast lounge background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.80f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .widthIn(max = 520.dp)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Spacer(modifier = Modifier.height(10.dp))

                // Admin News & Updates Banner (editable in Admin Console)
                if (appConfig.hasVisibleBanner()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .testTag("login_admin_news_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Campaign,
                                    contentDescription = "App news and updates",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = appConfig.bannerTitle.ifBlank { "VOX News & Updates" },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (appConfig.bannerMessage.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = appConfig.bannerMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Brand badge
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PRIVATE CHATS · FRIENDS · LIVE ROOMS · BUSINESS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = "VOX emblem",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.auth_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Personal vs Business Segmented Switcher
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedAccountMode == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedAccountMode = 0 }
                                .testTag("login_mode_personal")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = "Personal Account",
                                    tint = if (selectedAccountMode == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Personal",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = if (selectedAccountMode == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedAccountMode == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedAccountMode = 1 }
                                .testTag("login_mode_business")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Business,
                                    contentDescription = "Business Account",
                                    tint = if (selectedAccountMode == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Business",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = if (selectedAccountMode == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                if (isLocallyDeviceBanned) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = LiveCoral.copy(alpha = 0.16f),
                        border = BorderStroke(1.2.dp, LiveCoral.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("auth_device_banned_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Device Banned from VOX",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = LiveCoral
                            )
                            Text(
                                text = buildString {
                                    append("This device is banned and cannot sign in with alternate emails or trial accounts.")
                                    if (localDeviceBanReason.isNotBlank()) {
                                        append(" ($localDeviceBanReason)")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (!trialNoticeMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ModAmber.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = trialNoticeMessage,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                if (selectedAccountMode == 0) {
                    // --- PERSONAL LOGIN FLOW ---
                    FeatureHighlightRow(
                        icon = Icons.Rounded.Mic,
                        accent = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.auth_feature_voice),
                        subtitle = "Private messaging, Push-to-Talk, background calls, and voice stages."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FeatureHighlightRow(
                        icon = Icons.Rounded.Videocam,
                        accent = MaterialTheme.colorScheme.secondary,
                        title = stringResource(R.string.auth_feature_video),
                        subtitle = "Jump into live camera chat rooms from a single tap on the Rooms tab."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FeatureHighlightRow(
                        icon = Icons.Rounded.Security,
                        accent = ModAmber,
                        title = stringResource(R.string.auth_feature_safety),
                        subtitle = "Role-coded Admin, Support & Room Admin controls with real-time moderation."
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            errorMessage = null
                            SubAccountLoginPrefs.clearAll(context)
                            isLoading = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isLoading = false
                                    TrialSessionManager.deactivateTrial(context)
                                    onAuthSuccess()
                                },
                                onAuthError = { msg ->
                                    isLoading = false
                                    errorMessage = msg
                                },
                                scope = scope,
                                onAuthCancelled = {
                                    isLoading = false
                                }
                            )
                        },
                        enabled = !isLoading && !isCheckingAutoSignIn,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        if (isLoading || isCheckingAutoSignIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (isCheckingAutoSignIn) "Checking session…" else "Signing in…",
                                style = MaterialTheme.typography.labelLarge
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.auth_sign_in_google),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = "OR 1-DAY TRIAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1-Day Trial Login Card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (!appConfig.trialLoginsEnabled) Icons.Rounded.Lock else Icons.Rounded.HourglassBottom,
                                    contentDescription = "1-Day Trial Login",
                                    tint = if (!appConfig.trialLoginsEnabled || hasExpiredTrial) LiveCoral else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Try VOX for 1 Day",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            when {
                                isLocallyDeviceBanned -> {
                                    Text(
                                        text = "Trial logins are disabled because this device is banned from VOX.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LiveCoral,
                                        modifier = Modifier.testTag("trial_device_banned_notice")
                                    )
                                }
                                !appConfig.trialLoginsEnabled -> {
                                    Text(
                                        text = "Trial logins are currently disabled by the Administrator. Please sign in with your Google email above.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LiveCoral,
                                        modifier = Modifier.testTag("trial_disabled_notice")
                                    )
                                }
                                hasExpiredTrial -> {
                                    Text(
                                        text = "Your 1-day trial period has ended. Sign in with your Google email above to continue using VOX.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LiveCoral,
                                        modifier = Modifier.testTag("trial_expired_notice")
                                    )
                                }
                                else -> {
                                    Text(
                                        text = "Explore public rooms and community posts with a trial name for 24 hours. Buying points and posting require Google Sign-In.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        value = trialNameInput,
                                        onValueChange = {
                                            trialNameInput = it.take(30)
                                            trialError = null
                                        },
                                        label = { Text("Trial Display Name") },
                                        placeholder = { Text("e.g., GuestAlex") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.PersonOutline,
                                                contentDescription = null
                                            )
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("trial_name_input")
                                    )
                                    if (trialError != null) {
                                        Text(
                                            text = trialError!!,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            trialError = null
                                            TrialSessionManager.startOrResumeTrial(
                                                context = context,
                                                trialName = trialNameInput
                                            ).onSuccess { session ->
                                                onTrialLoginStarted(session)
                                            }.onFailure { err ->
                                                trialError = err.message ?: "Could not start trial"
                                            }
                                        },
                                        enabled = !isLoading && !isCheckingAutoSignIn,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("start_trial_button")
                                    ) {
                                        Text(
                                            text = "Start 1-Day Trial",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // --- BUSINESS LOGIN FLOW ---
                    // 1. Employee Sub-Account Login (Company Code + PIN + First & Last Name)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Badge,
                                    contentDescription = "Employee Sub-Account Login",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = "Employee Sub-Account Login",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Sign in with your Company Code, your unique Employee PIN, and First & Last Name.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = empFirstName,
                                    onValueChange = {
                                        empFirstName = it.take(20)
                                        empError = null
                                    },
                                    label = { Text("First Name") },
                                    placeholder = { Text("Jordan") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("employee_first_name_input")
                                )
                                OutlinedTextField(
                                    value = empLastName,
                                    onValueChange = {
                                        empLastName = it.take(20)
                                        empError = null
                                    },
                                    label = { Text("Last Name") },
                                    placeholder = { Text("Smith") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("employee_last_name_input")
                                )
                            }

                            val previewUsername = "${empFirstName.trim()} ${empLastName.trim()}".trim()
                            if (previewUsername.isNotEmpty()) {
                                Text(
                                    text = "Your company username will be: $previewUsername",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            OutlinedTextField(
                                value = empCompanyCode,
                                onValueChange = {
                                    empCompanyCode = SubAccountLoginPrefs.normalizeCompanyCode(it)
                                    empError = null
                                },
                                label = { Text("Company Code") },
                                placeholder = { Text("e.g., ACME2026") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Rounded.Key, contentDescription = null)
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("employee_company_code_input")
                            )

                            OutlinedTextField(
                                value = empCompanyPin,
                                onValueChange = {
                                    empCompanyPin = SubAccountLoginPrefs.normalizePin(it)
                                    empError = null
                                },
                                label = { Text("Unique Employee PIN") },
                                placeholder = { Text("6-digit PIN from company owner") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Rounded.Pin, contentDescription = null)
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("employee_company_pin_input")
                            )

                            if (empError != null) {
                                Text(
                                    text = empError!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Button(
                                onClick = {
                                    val first = empFirstName.trim()
                                    val last = empLastName.trim()
                                    val code = SubAccountLoginPrefs.normalizeCompanyCode(empCompanyCode)
                                    val pin = SubAccountLoginPrefs.normalizePin(empCompanyPin)
                                    when {
                                        first.isEmpty() || last.isEmpty() -> {
                                            empError = "Please enter both your First Name and Last Name."
                                        }
                                        code.length < 4 -> {
                                            empError = "Enter a valid Company Code (at least 4 characters)."
                                        }
                                        pin.length < 4 -> {
                                            empError = "Enter the unique Employee PIN given by your company owner."
                                        }
                                        else -> {
                                            empError = null
                                            errorMessage = null
                                            SubAccountLoginPrefs.savePendingCompanyCodeLogin(
                                                context = context,
                                                companyCode = code,
                                                pin = pin,
                                                firstName = first,
                                                lastName = last
                                            )
                                            isLoading = true
                                            onGoogleSignInClicked(
                                                context = context,
                                                credentialManager = credentialManager,
                                                onAuthSuccess = {
                                                    isLoading = false
                                                    TrialSessionManager.deactivateTrial(context)
                                                    onAuthSuccess()
                                                },
                                                onAuthError = { msg ->
                                                    isLoading = false
                                                    errorMessage = msg
                                                },
                                                scope = scope,
                                                onAuthCancelled = {
                                                    isLoading = false
                                                }
                                            )
                                        }
                                    }
                                },
                                enabled = !isLoading && !isCheckingAutoSignIn,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("employee_code_pin_login_button")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Signing in…")
                                } else {
                                    Text(
                                        text = "Sign In as Employee",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Register a New Business Account ($99.99/mo)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.2.dp, ModAmber.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Business,
                                    contentDescription = "Register Business Account",
                                    tint = ModAmber,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Create a Business Account",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "$99.99/mo · Up to 50 employee sub-accounts, Company Code + PIN, full owner controls & DM monitoring.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    bizRegisterError = null
                                    errorMessage = null
                                    showBusinessRegisterPage = true
                                },
                                enabled = !isLoading && !isCheckingAutoSignIn,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ModAmber,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("open_business_register_button")
                            ) {
                                Text(
                                    text = "Register Business Account ($99.99/mo)",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    errorMessage = null
                                    SubAccountLoginPrefs.clearAll(context)
                                    isLoading = true
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            isLoading = false
                                            TrialSessionManager.deactivateTrial(context)
                                            onAuthSuccess()
                                        },
                                        onAuthError = { msg ->
                                            isLoading = false
                                            errorMessage = msg
                                        },
                                        scope = scope,
                                        onAuthCancelled = {
                                            isLoading = false
                                        }
                                    )
                                },
                                enabled = !isLoading && !isCheckingAutoSignIn,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("business_owner_sign_in_button")
                            ) {
                                Text("Already a Business Owner? Sign In")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Protected by zero-trust room rules & verified Google accounts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun BusinessAccountRegistrationPage(
    bizEmail: String,
    onBizEmailChange: (String) -> Unit,
    companyName: String,
    onCompanyNameChange: (String) -> Unit,
    companyCode: String,
    onCompanyCodeChange: (String) -> Unit,
    companyPin: String,
    onCompanyPinChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onSubmitPayment: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 540.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("business_register_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to Login"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Register Business Account",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                    )
                    Text(
                        text = "$99.99 / month · Up to 50 Employee Sub-Accounts",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ModAmber
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "What's Included ($99.99/mo)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)
                    )
                    listOf(
                        "Create your own Company Code + PIN for employee sub-account logins",
                        "Up to 50 active employee sub-accounts (with 60s PTT & 1-on-1 company DMs)",
                        "Invisible or Visible owner access to sub-account 1-on-1 DMs",
                        "Full Owner Controls: Remove, Ban, Suspend, Urgent Alerts, File Notes & Buzz Phone",
                        "All VOX Pro & VIP Pro perks included"
                    ).forEach { perk ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = SafetyEmerald,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(16.dp)
                            )
                            Text(
                                text = perk,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Business Account Details",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = bizEmail,
                        onValueChange = onBizEmailChange,
                        label = { Text("Business Email Address *") },
                        placeholder = { Text("owner@yourcompany.com") },
                        leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("business_register_email_input")
                    )
                    Text(
                        text = "Sign in with this Google account on the next step so your Business Account goes under this email.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = companyName,
                        onValueChange = onCompanyNameChange,
                        label = { Text("Company / Business Name *") },
                        placeholder = { Text("e.g., Acme Logistics") },
                        leadingIcon = { Icon(Icons.Rounded.Business, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("business_register_company_name_input")
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = "Company Code & PIN (for Employee Sub-Accounts)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Set your Company Code & PIN now (or anytime later in your Company Console).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = companyCode,
                            onValueChange = onCompanyCodeChange,
                            label = { Text("Company Code") },
                            placeholder = { Text("ACME2026") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("business_register_code_input")
                        )
                        OutlinedTextField(
                            value = companyPin,
                            onValueChange = onCompanyPinChange,
                            label = { Text("PIN (4+ chars)") },
                            placeholder = { Text("4829") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("business_register_pin_input")
                        )
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Button(
                        onClick = onSubmitPayment,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ModAmber,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("business_register_pay_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connecting to Google & Checkout…", fontWeight = FontWeight.Bold)
                        } else {
                            Text(
                                text = "Pay $99.99/mo & Register Business",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureHighlightRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
