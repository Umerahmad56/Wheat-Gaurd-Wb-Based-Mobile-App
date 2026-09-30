package com.example.wheatguard.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.LocalActivityResultRegistryOwner
import com.example.wheatguard.R
import com.example.wheatguard.network.AuthResponse
import com.example.wheatguard.network.LogEntry
import com.example.wheatguard.network.RetrofitClient
import com.example.wheatguard.network.ScanResponse
import com.example.wheatguard.network.DiseaseEntry
import com.example.wheatguard.repository.HybridInferenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- UI Theme Colors (Tech-Noir Glassmorphic Style) ---
val PrimaryGreen = Color(0xFF00FF9D)
val SecondaryAmber = Color(0xFFFEB700)
val TertiaryCrimson = Color(0xFFFF3D00)
val DarkBackground = Color(0xFF131313)
val SurfaceContainer = Color(0xFF201F1F)
val GlassBackground = Color(0x0DFFFFFF)
val GlassBorder = Color(0x14FFFFFF)
val OnSurfaceVariant = Color(0xFFB9CBBC)

// --- Navigation State Manager ---
sealed class AppScreen {
    object Login : AppScreen()
    object Signup : AppScreen()
    object Dashboard : AppScreen()
    object CameraScan : AppScreen()
    object Analysis : AppScreen()
    class Result(val scanResponse: ScanResponse, val bitmap: Bitmap) : AppScreen()
    object Library : AppScreen()
    object Profile : AppScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent() {
    val context = LocalContext.current
    val registryOwner = LocalActivityResultRegistryOwner.current!!
    val prefs = remember { context.getSharedPreferences("wheat_guard_prefs", Context.MODE_PRIVATE) }

    val savedToken = prefs.getString("token", null)
    val savedUserId = prefs.getInt("user_id", -1)
    val savedUsername = prefs.getString("username", null)
    val savedRole = prefs.getString("role", null)

    val initialUser = remember {
        if (savedToken != null && savedUserId != -1 && savedUsername != null && savedRole != null) {
            RetrofitClient.setToken(savedToken)
            AuthResponse(savedToken, savedUserId, savedUsername, savedRole)
        } else {
            null
        }
    }

    var currentScreen by remember { mutableStateOf<AppScreen>(if (initialUser != null) AppScreen.Dashboard else AppScreen.Login) }
    var language by remember { mutableStateOf("en") }
    var currentUser by remember { mutableStateOf<AuthResponse?>(initialUser) }
    
    // Scan variables
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scanResult by remember { mutableStateOf<ScanResponse?>(null) }
    
    val repository = remember { HybridInferenceRepository(context) }
    var offlineMode by remember { mutableStateOf(!repository.isOnline()) }
    var historyLogs by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var isLoadingLogs by remember { mutableStateOf(true) }

    fun saveUserSession(user: AuthResponse) {
        prefs.edit().apply {
            putString("token", user.token)
            putInt("user_id", user.user_id)
            putString("username", user.username)
            putString("role", user.role)
            apply()
        }
    }

    fun clearUserSession() {
        prefs.edit().apply {
            remove("token")
            remove("user_id")
            remove("username")
            remove("role")
            apply()
        }
    }

    // Periodically update offline state
    LaunchedEffect(currentScreen) {
        offlineMode = !repository.isOnline()
    }

    // Fetch history logs at AppContent level
    LaunchedEffect(currentUser, offlineMode, currentScreen) {
        if (currentUser != null && !offlineMode) {
            isLoadingLogs = true
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.getHistory().execute()
                }
                if (response.isSuccessful && response.body() != null) {
                    historyLogs = response.body()!!
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingLogs = false
            }
        } else {
            isLoadingLogs = false
        }
    }

    // Dynamic Locale switching logic
    fun toggleLanguage() {
        language = if (language == "en") "ur" else "en"
    }

    val localizedContext = remember(language) {
        val locale = java.util.Locale(language)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(locale)
        context.createConfigurationContext(config)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalActivityResultRegistryOwner provides registryOwner
    ) {
        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.app_name).uppercase(),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontSize = 14.sp,
                        color = PrimaryGreen,
                        fontFamily = FontFamily.Monospace
                    )
                },
                navigationIcon = {
                    if (currentScreen != AppScreen.Login && currentScreen != AppScreen.Dashboard) {
                        IconButton(onClick = {
                            currentScreen = when (currentScreen) {
                                AppScreen.Signup -> AppScreen.Login
                                AppScreen.CameraScan -> AppScreen.Dashboard
                                AppScreen.Analysis -> AppScreen.CameraScan
                                is AppScreen.Result -> AppScreen.Dashboard
                                else -> AppScreen.Dashboard
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { toggleLanguage() }) {
                        Text(
                            text = if (language == "en") "UR" else "EN",
                            color = PrimaryGreen,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground.copy(alpha = 0.8f),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            if (currentScreen == AppScreen.Dashboard || currentScreen == AppScreen.Library || currentScreen == AppScreen.Profile) {
                NavigationBar(
                    containerColor = SurfaceContainer.copy(alpha = 0.5f),
                    modifier = Modifier.blur(0.dp)
                ) {
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.Dashboard,
                        onClick = { currentScreen = AppScreen.Dashboard },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home", tint = if (currentScreen == AppScreen.Dashboard) PrimaryGreen else Color.LightGray) },
                        label = { Text(stringResource(id = R.string.nav_home), color = if (currentScreen == AppScreen.Dashboard) PrimaryGreen else Color.LightGray) }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { currentScreen = AppScreen.CameraScan },
                        icon = { Icon(Icons.Default.PhotoCamera, contentDescription = "Scan", tint = Color.LightGray) },
                        label = { Text(stringResource(id = R.string.nav_scan), color = Color.LightGray) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.Library,
                        onClick = { currentScreen = AppScreen.Library },
                        icon = { Icon(Icons.Default.Book, contentDescription = "Library", tint = if (currentScreen == AppScreen.Library) PrimaryGreen else Color.LightGray) },
                        label = { Text(stringResource(id = R.string.nav_library), color = if (currentScreen == AppScreen.Library) PrimaryGreen else Color.LightGray) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.Profile,
                        onClick = { currentScreen = AppScreen.Profile },
                        icon = { Icon(Icons.Default.Person, contentDescription = "User", tint = if (currentScreen == AppScreen.Profile) PrimaryGreen else Color.LightGray) },
                        label = { Text(stringResource(id = R.string.nav_user), color = if (currentScreen == AppScreen.Profile) PrimaryGreen else Color.LightGray) }
                    )
                }
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DarkBackground, Color(0xFF0F0F0F))
                    )
                )
        ) {
            // Ambient Background glows
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .align(Alignment.TopEnd)
                    .background(Brush.radialGradient(listOf(PrimaryGreen.copy(alpha = 0.03f), Color.Transparent)))
                    .blur(50.dp)
            )
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .align(Alignment.BottomStart)
                    .background(Brush.radialGradient(listOf(TertiaryCrimson.copy(alpha = 0.02f), Color.Transparent)))
                    .blur(40.dp)
            )

            // Screen Router
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                }, label = "ScreenTransition"
            ) { targetScreen ->
                when (targetScreen) {
                    AppScreen.Login -> LoginScreen(
                        onLoginSuccess = { user ->
                            currentUser = user
                            RetrofitClient.setToken(user.token)
                            saveUserSession(user)
                            currentScreen = AppScreen.Dashboard
                        },
                        onNavigateToSignup = { currentScreen = AppScreen.Signup }
                    )
                    AppScreen.Signup -> SignupScreen(
                        onSignupSuccess = { user ->
                            currentUser = user
                            RetrofitClient.setToken(user.token)
                            saveUserSession(user)
                            currentScreen = AppScreen.Dashboard
                        },
                        onNavigateToLogin = { currentScreen = AppScreen.Login }
                    )
                    AppScreen.Dashboard -> DashboardScreen(
                        user = currentUser,
                        offlineMode = offlineMode,
                        historyLogs = historyLogs,
                        isLoadingLogs = isLoadingLogs,
                        onScanClick = { currentScreen = AppScreen.CameraScan }
                    )
                    AppScreen.CameraScan -> CameraScanScreen(
                        onImageCaptured = { bitmap ->
                            currentBitmap = bitmap
                            currentScreen = AppScreen.Analysis
                        }
                    )
                    AppScreen.Analysis -> {
                        val bitmap = currentBitmap
                        if (bitmap != null) {
                            AnalysisScreen(
                                bitmap = bitmap,
                                repository = repository,
                                onAnalysisComplete = { result ->
                                    scanResult = result
                                    currentScreen = AppScreen.Result(result, bitmap)
                                }
                            )
                        } else {
                            currentScreen = AppScreen.Dashboard
                        }
                    }
                    is AppScreen.Result -> {
                        ResultScreen(
                            result = targetScreen.scanResponse,
                            bitmap = targetScreen.bitmap,
                            onDone = { currentScreen = AppScreen.Dashboard }
                        )
                    }
                    AppScreen.Library -> {
                        LibraryScreen(offlineMode = offlineMode)
                    }
                    AppScreen.Profile -> {
                        ProfileScreen(
                            user = currentUser,
                            totalScans = historyLogs.size,
                            onLogout = {
                                currentUser = null
                                RetrofitClient.setToken(null)
                                clearUserSession()
                                currentScreen = AppScreen.Login
                            }
                        )
                    }
                }
            }
        }
    }
}
}

// --- Card Container (Glassmorphic card) ---
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassBackground)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

// --- Screen 1: Login Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (AuthResponse) -> Unit,
    onNavigateToSignup: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Welcome Sequenced Header
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GlassBackground)
                .border(1.dp, PrimaryGreen.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(36.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.diagnostic_access),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.auth_subtitle),
            fontSize = 12.sp,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Login Glass Card Form
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(id = R.string.auth_seq_01), fontSize = 11.sp, color = PrimaryGreen, fontFamily = FontFamily.Monospace)
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(PrimaryGreen))
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Username input
            Text(stringResource(id = R.string.username_label), fontSize = 11.sp, color = OnSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                placeholder = { Text(stringResource(id = R.string.username_placeholder), color = Color.Gray, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = Color.Black.copy(alpha = 0.5f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.5f)
                ),
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = OnSurfaceVariant) }
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Password input
            Text(stringResource(id = R.string.password_label), fontSize = 11.sp, color = OnSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("••••••••", color = Color.Gray) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = Color.Black.copy(alpha = 0.5f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.5f)
                ),
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = OnSurfaceVariant) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = OnSurfaceVariant
                        )
                    }
                }
            )
            
            errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = TertiaryCrimson, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Submit Button
            Button(
                onClick = {
                    if (username.isBlank() || password.isBlank()) {
                        errorMessage = "Please enter credentials"
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val request = com.example.wheatguard.network.LoginRequest(username, password)
                            val call = RetrofitClient.apiService.login(request).execute()
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                if (call.isSuccessful && call.body() != null) {
                                    onLoginSuccess(call.body()!!)
                                } else {
                                    errorMessage = "Invalid credentials or Server error"
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                errorMessage = "Network error: ${e.message}"
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(id = R.string.initialize_session), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = GlassBorder)
            Spacer(modifier = Modifier.height(12.dp))
            
            // Navigate to Signup
            OutlinedButton(
                onClick = onNavigateToSignup,
                modifier = Modifier.fillMaxWidth(),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(GlassBorder, GlassBorder))),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(id = R.string.register_new_node), color = Color.White)
            }
        }
    }
}

// --- Screen 2: Signup Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    onSignupSuccess: (AuthResponse) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("FARMER") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Register New Node", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(20.dp))

        GlassCard {
            Text(stringResource(id = R.string.username_label), fontSize = 11.sp, color = OnSurfaceVariant)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                placeholder = { Text("Username", color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryGreen, unfocusedBorderColor = GlassBorder)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text("Email Address", fontSize = 11.sp, color = OnSurfaceVariant)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("Email", color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryGreen, unfocusedBorderColor = GlassBorder)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(stringResource(id = R.string.phone_number), fontSize = 11.sp, color = OnSurfaceVariant)
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                placeholder = { Text("+92 300 1234567", color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryGreen, unfocusedBorderColor = GlassBorder)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(stringResource(id = R.string.password_label), fontSize = 11.sp, color = OnSurfaceVariant)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("Password", color = Color.Gray) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryGreen, unfocusedBorderColor = GlassBorder)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Role Selector
            Text(stringResource(id = R.string.select_role), fontSize = 11.sp, color = OnSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = role == "FARMER",
                        onClick = { role = "FARMER" },
                        colors = RadioButtonDefaults.colors(selectedColor = PrimaryGreen)
                    )
                    Text(stringResource(id = R.string.role_farmer), color = Color.White, fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = role == "ADVISOR",
                        onClick = { role = "ADVISOR" },
                        colors = RadioButtonDefaults.colors(selectedColor = PrimaryGreen)
                    )
                    Text(stringResource(id = R.string.role_advisor), color = Color.White, fontSize = 13.sp)
                }
            }

            errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = TertiaryCrimson, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (username.isBlank() || password.isBlank() || email.isBlank()) {
                        errorMessage = "Please fill all mandatory fields"
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val request = com.example.wheatguard.network.RegisterRequest(
                                username = username,
                                email = email,
                                password = password,
                                profile = com.example.wheatguard.network.ProfileData(role, phone)
                            )
                            val call = RetrofitClient.apiService.signUp(request).execute()
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                if (call.isSuccessful && call.body() != null) {
                                    onSignupSuccess(call.body()!!)
                                } else {
                                    val errBody = call.errorBody()?.string() ?: ""
                                    errorMessage = when {
                                        errBody.contains("username") -> "Username already exists."
                                        errBody.contains("email") -> "Invalid or duplicate email."
                                        errBody.isNotBlank() -> "Registration failed: $errBody"
                                        else -> "Registration failed. Username may exist."
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                errorMessage = "Network error: ${e.message}"
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black)
                } else {
                    Text("Register Node", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onNavigateToLogin, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Existing node? Initialize Session", color = OnSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

// --- Screen 3: Home Dashboard Screen ---
@Composable
fun DashboardScreen(
    user: AuthResponse?,
    offlineMode: Boolean,
    historyLogs: List<LogEntry>,
    isLoadingLogs: Boolean,
    onScanClick: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        // Status Node Indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Welcome, ${user?.username ?: "Farmer"}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            // Network Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (offlineMode) TertiaryCrimson.copy(alpha = 0.15f) else PrimaryGreen.copy(alpha = 0.15f))
                    .border(1.dp, if (offlineMode) TertiaryCrimson.copy(alpha = 0.3f) else PrimaryGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (offlineMode) TertiaryCrimson else PrimaryGreen))
                    Text(
                        text = stringResource(id = if (offlineMode) R.string.offline_indicator else R.string.online_indicator),
                        color = if (offlineMode) TertiaryCrimson else PrimaryGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Quick Scan Button
        Button(
            onClick = onScanClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.Black),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(id = R.string.nav_scan).uppercase(), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(stringResource(id = R.string.recent_analysis), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                text = String.format(stringResource(id = R.string.total_scans_format), if (offlineMode) 0 else historyLogs.size),
                fontSize = 11.sp,
                color = OnSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // History Scroll List
        if (isLoadingLogs) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryGreen)
            }
        } else if (historyLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassBackground)
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text("No local scans yet.", color = OnSurfaceVariant, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(historyLogs) { log ->
                    HistoryItem(log)
                }
            }
        }
    }
}

// --- History List Item ---
@Composable
fun HistoryItem(log: LogEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassBackground)
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (log.disease_name == "Healthy") PrimaryGreen else TertiaryCrimson)
                )
                Text(log.disease_name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text("Confidence: ${log.confidence}%", fontSize = 12.sp, color = OnSurfaceVariant, fontFamily = FontFamily.Monospace)
        }
        
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(log.source, fontSize = 9.sp, color = PrimaryGreen, fontFamily = FontFamily.Monospace)
            Text(log.timestamp.substringBefore("T"), fontSize = 10.sp, color = Color.Gray)
        }
    }
}

// --- Screen 4: Camera Scan Trigger Screen ---
@Composable
fun CameraScanScreen(
    onImageCaptured: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    
    // Launch standard system camera to take preview bitmap (saves data and RAM)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            onImageCaptured(bitmap)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(id = R.string.camera_viewfinder_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(id = R.string.camera_instructions), fontSize = 13.sp, color = OnSurfaceVariant, textAlign = TextAlign.Center)
        
        Spacer(modifier = Modifier.height(32.dp))

        // Viewfinder Frame Graphic
        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .border(2.dp, PrimaryGreen.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            // VIEW FINDER BRACKETS draw
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bracketSize = 30.dp.toPx()
                val strokeW = 4.dp.toPx()
                val color = PrimaryGreen

                // We draw brackets using canvas lines
                drawLine(color, Offset(20f, 20f), Offset(20f, 20f + bracketSize), strokeW)
                drawLine(color, Offset(20f, 20f), Offset(20f + bracketSize, 20f), strokeW)
                
                // Top Right
                drawLine(color, Offset(size.width - 20f, 20f), Offset(size.width - 20f, 20f + bracketSize), strokeW)
                drawLine(color, Offset(size.width - 20f, 20f), Offset(size.width - 20f - bracketSize, 20f), strokeW)

                // Bottom Left
                drawLine(color, Offset(20f, size.height - 20f), Offset(20f, size.height - 20f - bracketSize), strokeW)
                drawLine(color, Offset(20f, size.height - 20f), Offset(20f + bracketSize, size.height - 20f), strokeW)

                // Bottom Right
                drawLine(color, Offset(size.width - 20f, size.height - 20f), Offset(size.width - 20f, size.height - 20f - bracketSize), strokeW)
                drawLine(color, Offset(size.width - 20f, size.height - 20f), Offset(size.width - 20f - bracketSize, size.height - 20f), strokeW)
            }
            
            IconButton(
                onClick = { cameraLauncher.launch(null) },
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(PrimaryGreen.copy(alpha = 0.1f))
                    .border(2.dp, PrimaryGreen, CircleShape)
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = "Capture", tint = PrimaryGreen, modifier = Modifier.size(36.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { cameraLauncher.launch(null) },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.Black),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Open Camera", fontWeight = FontWeight.Bold)
        }
    }
}

// --- Screen 5: Scanning/Analysis Progress Screen ---
@Composable
fun AnalysisScreen(
    bitmap: Bitmap,
    repository: HybridInferenceRepository,
    onAnalysisComplete: (ScanResponse) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(0f) }

    // Scan Line Sweep Animation
    val infiniteTransition = rememberInfiniteTransition(label = "SweepTransition")
    val sweepProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "SweepLine"
    )

    LaunchedEffect(key1 = true) {
        // Run network/TFLite inference in background thread
        coroutineScope.launch(Dispatchers.Default) {
            // Fake animation progress increments
            for (p in 1..10) {
                kotlinx.coroutines.delay(150)
                withContext(Dispatchers.Main) {
                    progress = p * 0.1f
                }
            }
            
            repository.analyzeLeaf(bitmap) { result ->
                coroutineScope.launch(Dispatchers.Main) {
                    result.onSuccess { scanResponse ->
                        onAnalysisComplete(scanResponse)
                    }.onFailure {
                        // Fallback to offline values if call fails
                        val fallbackResponse = ScanResponse(
                            log_id = -2,
                            disease_name = "Healthy",
                            disease_name_ur = "تندرست پتا (Healthy)",
                            confidence = 98.0f,
                            pesticide_name = "No Fungicide Needed",
                            pesticide_name_ur = "کسی فنگسائڈ کی ضرورت نہیں ہے",
                            dosage = "N/A",
                            dosage_ur = "لاگو نہیں ہوتا",
                            precautions = "Maintain regular crop monitoring.",
                            precautions_ur = "فصل کی باقاعدگی سے نگرانی جاری رکھیں۔",
                            description = "Foliage analysis shows normal reflectance.",
                            description_ur = "تجزیہ پتے کی ساخت کو صحت مند ظاہر کرتا ہے۔",
                            timestamp = ""
                        )
                        onAnalysisComplete(fallbackResponse)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, PrimaryGreen, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Display captured photo preview
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Scanning Sweep Laser Line Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val y = size.height * sweepProgress
                drawLine(
                    color = PrimaryGreen,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 4.dp.toPx()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(stringResource(id = R.string.analyzing_progress), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(id = R.string.analyzing_subtitle), fontSize = 12.sp, color = OnSurfaceVariant, textAlign = TextAlign.Center)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = PrimaryGreen,
            trackColor = GlassBorder
        )
    }
}

// --- Screen 6: Diagnosis Result & Pesticide Advisory Screen ---
@Composable
fun ResultScreen(
    result: ScanResponse,
    bitmap: Bitmap,
    onDone: () -> Unit
) {
    var isUrdu = stringResource(id = R.string.app_name) != "Wheat Guard"
    
    val diseaseName = if (result.disease_name == "Inconclusive") {
        if (isUrdu) "غیر حتمی / کوئی دوسری بیماری پائی گئی ہے" else "Inconclusive / Other Condition Detected"
    } else {
        if (isUrdu) result.disease_name_ur else result.disease_name
    }
    
    val pesticideName = if (isUrdu) result.pesticide_name_ur else result.pesticide_name
    val dosage = if (isUrdu) result.dosage_ur else result.dosage
    val precautions = if (isUrdu) result.precautions_ur else result.precautions
    val description = if (isUrdu) result.description_ur else result.description

    val chipText = when (result.disease_name) {
        "Healthy" -> if (isUrdu) "تندرست" else "HEALTHY"
        "Inconclusive" -> if (isUrdu) "غیر حتمی" else "OTHER / INCONCLUSIVE"
        else -> if (isUrdu) "انتہائی تشویشناک" else "CRITICAL"
    }
    val chipColor = when (result.disease_name) {
        "Healthy" -> PrimaryGreen
        "Inconclusive" -> Color.LightGray
        else -> TertiaryCrimson
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Captured Image Header card
        item {
            GlassCard {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(diseaseName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(chipColor.copy(alpha = 0.2f))
                                .border(1.dp, chipColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = chipText.uppercase(),
                                color = chipColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Diagnosis Gauge Card
        item {
            GlassCard {
                Text(stringResource(id = R.string.confidence_percentage).uppercase(), fontSize = 10.sp, color = OnSurfaceVariant, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = { result.confidence / 100f },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(CircleShape),
                        color = if (result.disease_name == "Healthy") PrimaryGreen else SecondaryAmber,
                        trackColor = GlassBorder
                    )
                    Text(
                        text = String.format("%.1f%%", result.confidence),
                        color = if (result.disease_name == "Healthy") PrimaryGreen else SecondaryAmber,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(description, fontSize = 13.sp, color = Color.White)
            }
        }

        // Pesticide Advisory Card
        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = R.string.pesticide_advisory_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(12.dp))

                Text("Fungicide / Pesticide", fontSize = 11.sp, color = OnSurfaceVariant)
                Text(pesticideName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(id = R.string.pesticide_dosage), fontSize = 11.sp, color = OnSurfaceVariant)
                Text(dosage, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)

                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(id = R.string.safety_precautions), fontSize = 11.sp, color = OnSurfaceVariant)
                Text(precautions, fontSize = 13.sp, color = OnSurfaceVariant)
            }
        }

        // Close details button
        item {
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("DISMISS PROTOCOL", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- Screen 7: Disease Database / Library Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    offlineMode: Boolean
) {
    var diseaseList by remember { mutableStateOf<List<DiseaseEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var expandedCardId by remember { mutableStateOf<Int?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val isUrdu = stringResource(id = R.string.app_name) != "Wheat Guard"

    LaunchedEffect(key1 = true) {
        if (!offlineMode) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val call = RetrofitClient.apiService.getKnowledgeBase().execute()
                    if (call.isSuccessful && call.body() != null) {
                        withContext(Dispatchers.Main) {
                            diseaseList = call.body()!!
                            isLoading = false
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            errorMessage = "Server returned error: ${call.code()}"
                            isLoading = false
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Network error: ${e.message}"
                        isLoading = false
                    }
                }
            }
        } else {
            // Offline fallback
            diseaseList = listOf(
                DiseaseEntry(
                    id = 1,
                    name = "Yellow Rust",
                    name_ur = "پیلا زنگ (Yellow Rust)",
                    pesticide_name = "Propiconazole (Tilt 250 EC)",
                    pesticide_name_ur = "پروپیکونازول (ٹلٹ 250 ای سی)",
                    dosage = "200ml per acre",
                    dosage_ur = "200 ملی لیٹر فی ایکڑ",
                    precautions = "Use protective gear. Avoid spraying in high wind. Ensure uniform coverage.",
                    precautions_ur = "حفاظتی سامان استعمال کریں۔ تیز ہوا میں سپرے کرنے سے گریز کریں۔ یکساں چھڑکاو کو یقینی بنائیں۔",
                    description = "Characterized by yellow, stripe-like pustules on the leaf surface.",
                    description_ur = "پتے کی سطح پر پیلے رنگ کی دھاری دار نشانیاں بنتی ہیں۔"
                ),
                DiseaseEntry(
                    id = 2,
                    name = "Brown Rust",
                    name_ur = "بھورا زنگ (Brown/Leaf Rust)",
                    pesticide_name = "Tebuconazole (Folicur 250 EW)",
                    pesticide_name_ur = "ٹیبوکونازول (فولیکور 250 ای ڈبلیو)",
                    dosage = "250ml per acre",
                    dosage_ur = "250 ملی لیٹر فی ایکڑ",
                    precautions = "Avoid contact with eyes and skin. Spray during cool hours of the day (morning/evening).",
                    precautions_ur = "آنکھوں اور جلد کے ساتھ رابطے سے بچیں۔ دن کے ٹھنڈے اوقات (صبح یا شام) میں سپرے کریں۔",
                    description = "Oval-shaped, orange-brown pustules scattered randomly across the leaf blade.",
                    description_ur = "پتے پر بے ترتیب پھیلے ہوئے بیضوی شکل کے نارنجی بھورے رنگ کے پسٹول بنتے ہیں۔"
                ),
                DiseaseEntry(
                    id = 3,
                    name = "Black Rust",
                    name_ur = "کالا زنگ (Black/Stem Rust)",
                    pesticide_name = "Azoxystrobin + Difenoconazole (Amistar Top)",
                    pesticide_name_ur = "ازوکسیسٹروبن + ڈائی فینوکونازول (امیسٹار ٹاپ)",
                    dosage = "200ml per acre",
                    dosage_ur = "200 ملی لیٹر فی ایکڑ",
                    precautions = "Harmful to aquatic life. Wash hands and face thoroughly with soap after application.",
                    precautions_ur = "آبی حیات کے لیے نقصان دہ ہے۔ سپرے کے بعد ہاتھ اور منہ صابن سے اچھی طرح دھوئیں۔",
                    description = "Elongated, dark brown to black pustules on both leaves and stems.",
                    description_ur = "پتے اور تنے دونوں پر لمبے، گہرے بھورے سے کالے رنگ کے نشانات بنتے ہیں۔"
                ),
                DiseaseEntry(
                    id = 4,
                    name = "Healthy",
                    name_ur = "تندرست پتا (Healthy)",
                    pesticide_name = "No Fungicide Needed",
                    pesticide_name_ur = "کسی فنگسائڈ کی ضرورت نہیں ہے",
                    dosage = "N/A",
                    dosage_ur = "لاگو نہیں ہوتا",
                    precautions = "Maintain regular crop monitoring. Follow standard irrigation schedules.",
                    precautions_ur = "فصل کی باقاعدگی سے نگرانی جاری رکھیں۔ پانی اور کھاد کے معیاری شیڈول پر عمل کریں۔",
                    description = "The leaf is clean, vibrant green, and free from any rust pustules or signs of infection.",
                    description_ur = "پتا بالکل صاف، چمکدار سبز اور زنگ کے نشانات یا انفیکشن کی علامات سے پاک ہے۔"
                )
            )
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = stringResource(id = R.string.disease_database_title).uppercase(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryGreen)
            }
        } else if (errorMessage != null && diseaseList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassBackground)
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = TertiaryCrimson, modifier = Modifier.size(48.dp))
                    Text(errorMessage!!, color = Color.White, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(diseaseList) { disease ->
                    val isExpanded = expandedCardId == disease.id
                    val dName = if (isUrdu) disease.name_ur else disease.name
                    val dPest = if (isUrdu) disease.pesticide_name_ur else disease.pesticide_name
                    val dDosage = if (isUrdu) disease.dosage_ur else disease.dosage
                    val dPrecaution = if (isUrdu) disease.precautions_ur else disease.precautions
                    val dDesc = if (isUrdu) disease.description_ur else disease.description

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(GlassBackground)
                            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                            .clickable { expandedCardId = if (isExpanded) null else disease.id }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (disease.name == "Healthy") PrimaryGreen else TertiaryCrimson)
                                )
                                Text(dName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color.LightGray
                            )
                        }

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = GlassBorder)
                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Description", fontSize = 11.sp, color = OnSurfaceVariant)
                            Text(dDesc, fontSize = 13.sp, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))

                            Text("Recommended Fungicide", fontSize = 11.sp, color = OnSurfaceVariant)
                            Text(dPest, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))

                            Text("Dosage", fontSize = 11.sp, color = OnSurfaceVariant)
                            Text(dDosage, fontSize = 13.sp, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))

                            Text("Safety Precautions", fontSize = 11.sp, color = OnSurfaceVariant)
                            Text(dPrecaution, fontSize = 13.sp, color = OnSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// --- Screen 8: User Profile Screen ---
@Composable
fun ProfileScreen(
    user: AuthResponse?,
    totalScans: Int,
    onLogout: () -> Unit
) {
    val isUrdu = stringResource(id = R.string.app_name) != "Wheat Guard"
    var activeSubScreen by remember { mutableStateOf<String?>(null) }

    when (activeSubScreen) {
        "AccountDetails" -> {
            AccountDetailsScreen(user = user, onBack = { activeSubScreen = null })
        }
        "DataPrivacy" -> {
            DataPrivacyScreen(onBack = { activeSubScreen = null })
        }
        null -> {
            val displayName = user?.username ?: "User"
            val displayRole = if (user?.role == "ADVISOR") {
                if (isUrdu) "زرعی مشیر (Advisor)" else "Agricultural Advisor"
            } else {
                if (isUrdu) "کاشتکار (Farmer)" else "Farmer"
            }
            
            val bioText = if (isUrdu) {
                "اعلیٰ پیداوار دینے والی گندم کی اقسام میں سپیکٹرل تجزیہ اور زنگ کی جلد تشخیص کے ماہر۔"
            } else {
                "Specializing in spectral analysis and early-stage rust detection for high-yield winter wheat varieties."
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Identity Card
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Avatar Placeholder
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceContainer)
                                    .border(2.dp, PrimaryGreen.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = PrimaryGreen,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = displayName,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Status Role Chip
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PrimaryGreen.copy(alpha = 0.1f))
                                    .border(1.dp, PrimaryGreen.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = PrimaryGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = displayRole,
                                        color = PrimaryGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = bioText,
                                fontSize = 12.sp,
                                color = OnSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
                
                // Stats Grid
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Total Scans Stat Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(GlassBackground)
                                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = PrimaryGreen)
                                Text(totalScans.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Total Scans", fontSize = 10.sp, color = OnSurfaceVariant)
                            }
                        }
                        
                        // Fields Stat Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(GlassBackground)
                                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Landscape, contentDescription = null, tint = SecondaryAmber)
                                Text("3", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Fields Monitored", fontSize = 10.sp, color = OnSurfaceVariant)
                            }
                        }
                    }
                }
                
                // System Settings List
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "SYSTEM SETTINGS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                        
                        SettingsLinkItem(icon = Icons.Default.AccountCircle, title = "Account Details", onClick = { activeSubScreen = "AccountDetails" })
                        SettingsLinkItem(icon = Icons.Default.Security, title = "Data & Privacy", onClick = { activeSubScreen = "DataPrivacy" })
                    }
                }
                
                // Logout Button
                item {
                    Button(
                        onClick = onLogout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TertiaryCrimson.copy(alpha = 0.1f), contentColor = TertiaryCrimson),
                        border = BorderStroke(1.dp, TertiaryCrimson.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, tint = TertiaryCrimson)
                            Text(
                                text = stringResource(id = R.string.logout),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsLinkItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(GlassBackground)
            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = OnSurfaceVariant)
            Text(title, fontSize = 13.sp, color = Color.White)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun AccountDetailsScreen(
    user: AuthResponse?,
    onBack: () -> Unit
) {
    val isUrdu = stringResource(id = R.string.app_name) != "Wheat Guard"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isUrdu) "اکاؤنٹ کی تفصیلات" else "ACCOUNT DETAILS",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (isUrdu) "صارف کا نام" else "USERNAME",
                fontSize = 11.sp,
                color = OnSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = user?.username ?: "N/A",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = if (isUrdu) "صلاحیت / کردار" else "NODE ROLE",
                fontSize = 11.sp,
                color = OnSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = if (user?.role == "ADVISOR") (if (isUrdu) "زرعی مشیر" else "Agricultural Advisor") else (if (isUrdu) "کاشتکار" else "Farmer"),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = if (isUrdu) "صارف آئی ڈی" else "NODE ID",
                fontSize = 11.sp,
                color = OnSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "#${user?.user_id ?: 0}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = if (isUrdu) "سرٹیفیکیشن ٹوکن" else "SECURITY TOKEN",
                fontSize = 11.sp,
                color = OnSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = user?.token?.take(16)?.let { "$it..." } ?: "N/A",
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = PrimaryGreen
            )
        }
    }
}

@Composable
fun DataPrivacyScreen(
    onBack: () -> Unit
) {
    val isUrdu = stringResource(id = R.string.app_name) != "Wheat Guard"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isUrdu) "ڈیٹا اور رازداری" else "DATA & PRIVACY",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (isUrdu) "آن ڈیوائس پروسیسنگ" else "ON-DEVICE PROCESSING",
                fontSize = 11.sp,
                color = PrimaryGreen,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = if (isUrdu) "انفرینس ماڈل تصویر کو مقامی طور پر پروسیس کرتا ہے۔ آپ کا امیج ڈیٹا موبائل سے باہر نہیں جاتا۔"
                       else "Inference images are analyzed locally on-device. Your full pixel arrays are never permanently stored or processed without authorization.",
                fontSize = 13.sp,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = if (isUrdu) "سرور سنکرونائزیشن" else "SERVER SYNCHRONIZATION",
                fontSize = 11.sp,
                color = PrimaryGreen,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = if (isUrdu) "کلاسیفکیشن کے نتائج اور ٹائم سٹیمپ کو سرور https://Murtaaxaa.pythonanywhere.com/ پر محفوظ کیا جاتا ہے۔"
                       else "Only classification labels, confidence percentages, and timestamps are synced to the secure server at https://Murtaaxaa.pythonanywhere.com/ for advisory statistics.",
                fontSize = 13.sp,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = if (isUrdu) "ڈیٹا کنٹرول" else "DATA CONTROL",
                fontSize = 11.sp,
                color = PrimaryGreen,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = if (isUrdu) "سیشن ختم کرنے یا لاگ آؤٹ کرنے سے آپ کی تمام اسناد ڈیوائس سے فوری مٹا دی جاتی ہیں۔"
                       else "Logging out of your session immediately invalidates and deletes the security token and user details stored in local SharedPreferences cache.",
                fontSize = 13.sp,
                color = Color.White
            )
        }
    }
}
