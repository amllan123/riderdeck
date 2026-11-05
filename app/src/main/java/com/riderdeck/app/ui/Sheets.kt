package com.riderdeck.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TireRepair
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riderdeck.app.Calls
import com.riderdeck.app.Contact
import com.riderdeck.app.Hub
import com.riderdeck.app.MainActivity
import com.riderdeck.app.Media
import com.riderdeck.app.Nav
import com.riderdeck.app.Navigator
import com.riderdeck.app.Perms
import com.riderdeck.app.Pin
import com.riderdeck.app.Place
import com.riderdeck.app.PlaceSearch
import com.riderdeck.app.RiderApp
import com.riderdeck.app.RiderNotificationListener
import com.riderdeck.app.Speaker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/* ======================= bottom sheets ======================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheets(name: String, act: MainActivity, open: (String) -> Unit, close: () -> Unit) {
    val p = LocalPal.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = state,
        containerColor = p.sheet,
        contentColor = p.fg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 14.dp).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(4.dp)).background(p.faint))
        },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(bottom = 28.dp).navigationBarsPadding()
        ) {
            when (name) {
                "near" -> NearbySheet(act, close)
                "music" -> MusicSheet(act)
                "fuel" -> FuelSheet()
                "more" -> MoreSheet(act, open, close)
                "ride" -> RideSheet(act)
                "settings" -> SettingsSheet(act)
                "docs" -> DocsSheet()
                "sos" -> SosSheet(act, close)
                "perms" -> {
                    SheetHeader("Permissions", "Turn on what you want RiderDeck to do")
                    PermissionList(act)
                }
            }
        }
    }
}

@Composable
private fun riderFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LocalPal.current.fg,
    unfocusedTextColor = LocalPal.current.fg,
    disabledTextColor = LocalPal.current.muted,
    focusedBorderColor = LocalPal.current.blue,
    unfocusedBorderColor = LocalPal.current.stroke,
    disabledBorderColor = LocalPal.current.stroke,
    cursorColor = LocalPal.current.blue,
    focusedLabelColor = LocalPal.current.blue,
    unfocusedLabelColor = LocalPal.current.muted,
)

/**
 * Full-screen search (like Google Maps): search box pinned at the top, results scroll underneath, and the
 * keyboard gets out of the way — it hides when you scroll, press search or pick a place. In landscape it only
 * opens when you tap the box, because there it would cover most of the screen.
 */
@Composable
fun SearchScreen(act: MainActivity, open: (String) -> Unit, close: () -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val rideState = Hub.ride.collectAsState()
    val fast by remember { derivedStateOf { rideState.value.speedKmh > 10f } }
    val s by RiderApp.prefs.s.collectAsState()
    val google by PlaceSearch.google.collectAsState()
    val park by RiderApp.prefs.parking.collectAsState()
    var q by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf("") }
    val locked = s.rideLock && fast
    val focus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val list = androidx.compose.foundation.lazy.rememberLazyListState()

    fun hideKeyboard() {
        keyboard?.hide()
        focusManager.clearFocus()
    }
    androidx.activity.compose.BackHandler(onBack = close)

    LaunchedEffect(q) {
        val text = q.trim()
        if (text.length < 2) {
            results = emptyList(); searched = ""; return@LaunchedEffect
        }
        delay(320)
        busy = true
        results = PlaceSearch.find(ctx, text)
        searched = text
        busy = false
    }
    LaunchedEffect(list.isScrollInProgress) {
        if (list.isScrollInProgress) hideKeyboard()
    }
    fun go(place: String) {
        if (place.isBlank()) return
        close()
        Navigator.previewQuery(ctx, place, autoStart = false)
    }
    fun show(pl: Place) {
        hideKeyboard()
        close()
        Navigator.showPlace(ctx, pl)
    }
    fun inGoogleMaps(what: String) {
        close()
        Nav.searchNearby(act, what)
        Hub.toast("Found it? Tap Share → RiderDeck to ride it here")
    }

    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxSize().background(p.bg).safeDrawingPadding(),
    ) {
        val portrait = maxHeight > maxWidth
        LaunchedEffect(Unit) {
            PlaceSearch.newSession()
            if (portrait && !locked) {
                delay(200)
                try { focus.requestFocus() } catch (_: Exception) { }
            }
        }
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = 780.dp).fillMaxSize().padding(horizontal = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = if (portrait) 8.dp else 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PopIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", kind = PopKind.Secondary, size = 46.dp, onClick = close)
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, enabled = !locked, singleLine = true,
                    placeholder = { Text(if (locked) "Locked while moving · use the mic" else "Search a place, shop, address") },
                    leadingIcon = { Icon(if (locked) Icons.Rounded.Lock else Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (q.isNotEmpty()) {
                            Box(Modifier.size(40.dp).clip(CircleShape).clickable { q = "" }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Close, "Clear")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { hideKeyboard() }),
                    shape = RoundedCornerShape(50), colors = riderFieldColors(),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                PopIcon(Icons.Rounded.Mic, "Say a place", kind = PopKind.Accent, size = 46.dp) { close(); act.startVoice() }
            }

            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = list,
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    busy && results.isEmpty() -> item {
                        Text("Searching…", fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(8.dp))
                    }
                    results.isNotEmpty() -> {
                        // landscape: every pixel above the keyboard goes to results
                        if (portrait) item { Label(if (google == PlaceSearch.Google.OK) "Results · Google" else "Results", Modifier.padding(start = 6.dp, top = 4.dp)) }
                        items(results.size) { i ->
                            val pl = results[i]
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.glass)) {
                                ResultRow(pl, compact = !portrait, onShow = { show(pl) }, onGo = { hideKeyboard(); close(); Navigator.previewPlace(ctx, pl) })
                            }
                        }
                    }
                    searched.isNotEmpty() -> item {
                        Column(Modifier.padding(horizontal = 6.dp)) {
                            Text("No matches for “$searched”.", fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(top = 6.dp))
                            PillButton("Find “$searched” in Google Maps", Modifier.fillMaxWidth().padding(top = 8.dp), primary = false, icon = Icons.Rounded.Search) {
                                inGoogleMaps(searched)
                            }
                            Text(
                                "Then tap Share → RiderDeck in Google Maps and the route opens here.",
                                fontSize = 12.5.sp, color = p.muted, modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                if (google == PlaceSearch.Google.OFF && q.isNotBlank()) item {
                    Text(
                        "Google search is off for your Maps key, so these come from OpenStreetMap. Turn on “Places API (New)” for the key to get Google Maps results here.",
                        fontSize = 12.sp, color = p.warn, lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
                if (q.isBlank()) {
                    item { Label("Saved", Modifier.padding(start = 6.dp, top = 4.dp)) }
                    park?.let { pk -> item { ParkingTile(pk, close) } }
                    item {
                        Tile(Icons.Rounded.Home, p.blue, "Home", s.home.ifBlank { "Tap to set your home address" }) {
                            if (s.home.isBlank()) open("settings") else go(s.home)
                        }
                    }
                    item {
                        Tile(Icons.Rounded.Work, p.cyan, "Office", s.office.ifBlank { "Tap to set your office address" }) {
                            if (s.office.isBlank()) open("settings") else go(s.office)
                        }
                    }
                    item {
                        Tile(Icons.Rounded.LocalGasStation, p.warn, "Nearby", "Petrol, puncture shop, air, mechanic, hospital") { open("near") }
                    }
                    item {
                        Text(
                            "“Go” plans a two-wheeler route on the RiderDeck map with voice turn-by-turn.",
                            fontSize = 12.5.sp, color = p.muted, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(pl: Place, compact: Boolean = false, onShow: () -> Unit, onGo: () -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onShow)
            .padding(start = 14.dp, end = 8.dp, top = if (compact) 6.dp else 10.dp, bottom = if (compact) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Place, null, tint = p.bad)
        Column(Modifier.weight(1f)) {
            Text(pl.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val dist = pl.distanceKm?.let { formatKm(it) + " · " } ?: ""
            Text(dist + pl.sub, fontSize = 12.sp, color = p.muted, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
        }
        PopButton("Map", kind = PopKind.Secondary, height = 36.dp, onClick = onShow)
        PopButton("Go", kind = PopKind.Accent, height = 36.dp, onClick = onGo)
    }
}

@Composable
private fun NearbySheet(act: MainActivity, close: () -> Unit) {
    val p = LocalPal.current
    SheetHeader("Nearby", "Opens in Google Maps")
    val items = listOf(
        Triple(Icons.Rounded.TireRepair, p.blue, "Puncture repair shop"),
        Triple(Icons.Rounded.LocalGasStation, p.warn, "Petrol pump"),
        Triple(Icons.Rounded.Air, p.cyan, "Air filling"),
        Triple(Icons.Rounded.Build, p.ok, "Bike mechanic"),
        Triple(Icons.Rounded.LocalHospital, p.bad, "Hospital"),
        Triple(Icons.Rounded.LocalCafe, p.muted, "Tea stall"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (icon, tint, what) ->
            Tile(icon, tint, what, "Near me") {
                close()
                Nav.searchNearby(act, "$what near me")
            }
        }
    }
}

@Composable
private fun MusicSheet(act: MainActivity) {
    val p = LocalPal.current
    val m by Hub.media.collectAsState()
    val listener by Hub.listenerConnected.collectAsState()
    SheetHeader("Music", if (m.connected) "Playing in ${m.appName.ifBlank { "your music app" }}" else "Control YouTube Music without leaving the dashboard")
    if (!listener) {
        if (RiderApp.isLite) {
            Text(
                "This is the Lite edition. The play, pause and skip buttons control YouTube Music, but song names, artwork and the queue need the full RiderDeck app.",
                fontSize = 14.sp, color = p.muted, lineHeight = 20.sp,
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                CtrlButton(Icons.Rounded.SkipPrevious, "Previous") { Media.prev() }
                PlayButton(false, 64)
                CtrlButton(Icons.Rounded.SkipNext, "Next") { Media.next() }
            }
            PillButton("Open YouTube Music", Modifier.fillMaxWidth(), primary = false, icon = Icons.Rounded.MusicNote) { Media.openYtMusic(act) }
        } else {
            Tile(Icons.Rounded.Notifications, p.warn, "Allow notification access", "Needed to see and control what's playing") {
                Perms.openListenerSettings(act)
            }
        }
        return
    }
    if (m.connected && m.title.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val art = m.art
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(p.glass), contentAlignment = Alignment.Center) {
                if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxSize()) else Icon(Icons.Rounded.MusicNote, null)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(m.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(m.artist, color = p.muted, fontSize = 13.sp, maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            CtrlButton(Icons.Rounded.SkipPrevious, "Previous") { Media.prev() }
            PlayButton(m.playing, 64)
            CtrlButton(Icons.Rounded.SkipNext, "Next") { Media.next() }
        }
    }
    val queue = remember(m.title) { RiderNotificationListener.instance?.queue().orEmpty().take(25) }
    if (queue.isNotEmpty()) {
        SectionTitle("Up next")
        Group {
            queue.forEach { item ->
                Text(
                    item.description.title?.toString() ?: "Unknown",
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().clickable { Media.skipTo(item.queueId) }.padding(horizontal = 14.dp, vertical = 13.dp),
                )
            }
        }
    }
    PillButton("Open YouTube Music", Modifier.fillMaxWidth().padding(top = 14.dp), primary = false, icon = Icons.Rounded.MusicNote) {
        Media.openYtMusic(act)
    }
}

@Composable
private fun NumberField(label: String, initial: Float, modifier: Modifier, onValid: (Float) -> Unit) {
    var text by remember { mutableStateOf(if (initial % 1f == 0f) initial.toInt().toString() else initial.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toFloatOrNull()?.takeIf { v -> v > 0f }?.let(onValid)
        },
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(16.dp), colors = riderFieldColors(), modifier = modifier,
    )
}

@Composable
private fun FuelSheet() {
    val p = LocalPal.current
    val s by RiderApp.prefs.s.collectAsState()
    val ride by Hub.ride.collectAsState()
    val full = s.tankL * s.mileage
    val range = RiderApp.prefs.rangeKm(ride.fuelKm)
    SheetHeader("Fuel & service", "Range comes from your tank size, mileage and the km ridden since filling up")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Hero("$range", "km range left", Modifier.weight(1f), if (range < 25) p.warn else p.fg)
        Hero("%.0f".format(ride.fuelKm), "km since fill-up", Modifier.weight(1f))
        Hero("%.0f".format(s.mileage), "km per litre", Modifier.weight(1f))
    }
    PillButton("I just filled a full tank", Modifier.fillMaxWidth().padding(top = 12.dp), icon = Icons.Rounded.LocalGasStation) {
        RiderApp.prefs.kmSinceFill = 0f
        Hub.ride.value = Hub.ride.value.copy(fuelKm = 0f)
        Hub.toast("Full tank · about ${full.roundToInt()} km of range")
    }
    SectionTitle("Your bike")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("Tank size (litres)", s.tankL, Modifier.weight(1f)) { v -> RiderApp.prefs.edit { it.copy(tankL = v) } }
        NumberField("Mileage (km/l)", s.mileage, Modifier.weight(1f)) { v -> RiderApp.prefs.edit { it.copy(mileage = v) } }
    }
    ServiceSection()
}

@Composable
fun Hero(value: String, label: String, modifier: Modifier, color: Color = LocalPal.current.fg) {
    val p = LocalPal.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(p.glass).padding(12.dp)) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
        Text(label.uppercase(), fontSize = 10.sp, letterSpacing = 1.sp, color = p.muted, maxLines = 2, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MoreSheet(act: MainActivity, open: (String) -> Unit, close: () -> Unit) {
    val p = LocalPal.current
    SheetHeader("More")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile(Icons.Rounded.Speed, p.pop, "Rides & stats", "Saved rides with routes, top speed, lean, 0–60") { open("ride") }
        Tile(Icons.Rounded.Description, p.cyan, "Documents", "RC, licence, insurance, PUC with expiry alerts") { open("docs") }
        Tile(Icons.Rounded.LocalGasStation, p.warn, "Fuel & service", "Range, oil change and chain lube reminders") { open("fuel") }
        Tile(Icons.Rounded.Settings, p.muted, "Settings", "Home, emergency contacts, alerts, glove mode") { open("settings") }
        Tile(Icons.Rounded.Lock, p.cyan, "Permissions", "Location, music, calls, SMS, microphone") { open("perms") }
        Tile(Icons.Rounded.TwoWheeler, p.warn, "Set lean to zero", "Do this with the bike upright") {
            act.tracker.calibrateLean()
            close()
            Hub.toast("Lean set to zero")
        }
        Tile(Icons.Rounded.Warning, p.bad, "Test crash alert", "Shows the 15 second countdown. Tap I'm OK to stop it") {
            close()
            act.startCrashCountdown()
        }
        if (com.riderdeck.app.CrashLog.at() > 0L) {
            Tile(Icons.Rounded.BugReport, p.bad, "Crash report", "RiderDeck closed unexpectedly. Send this so it can be fixed") {
                com.riderdeck.app.CrashLog.share(act)
            }
        }
    }
}

@Composable
private fun SettingsSheet(act: MainActivity) {
    val p = LocalPal.current
    val s by RiderApp.prefs.s.collectAsState()
    val prefs = RiderApp.prefs
    SheetHeader("Settings")

    SectionTitle("Places")
    var home by remember { mutableStateOf(s.home) }
    var office by remember { mutableStateOf(s.office) }
    OutlinedTextField(
        home, { home = it; prefs.edit { x -> x.copy(home = it.trim()) } }, label = { Text("Home address") },
        singleLine = true, shape = RoundedCornerShape(16.dp), colors = riderFieldColors(), modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        office, { office = it; prefs.edit { x -> x.copy(office = it.trim()) } }, label = { Text("Office address") },
        singleLine = true, shape = RoundedCornerShape(16.dp), colors = riderFieldColors(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )

    SectionTitle("Emergency contacts")
    Group {
        if (s.contacts.isEmpty()) {
            Text("No contacts yet. They get an SMS with your location after a crash or when you press SOS.", fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(14.dp))
        }
        s.contacts.forEachIndexed { i, c ->
            Row(Modifier.fillMaxWidth().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, fontSize = 15.sp)
                    Text(c.number, fontSize = 12.sp, color = p.muted)
                }
                Box(Modifier.size(48.dp).clickable { prefs.edit { x -> x.copy(contacts = x.contacts.filterIndexed { j, _ -> j != i }) } }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Delete, "Remove ${c.name}", tint = p.muted)
                }
            }
        }
    }
    if (s.contacts.size < 5) {
        var name by remember { mutableStateOf("") }
        var number by remember { mutableStateOf("") }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = riderFieldColors(), modifier = Modifier.weight(1f))
            OutlinedTextField(
                number, { number = it }, label = { Text("Phone") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(16.dp), colors = riderFieldColors(), modifier = Modifier.weight(1f),
            )
        }
        PillButton("Add contact", Modifier.fillMaxWidth().padding(top = 8.dp), primary = false) {
            if (number.isBlank()) {
                Hub.toast("Enter a phone number")
            } else {
                prefs.edit { x -> x.copy(contacts = x.contacts + Contact(name.ifBlank { number }.trim(), number.trim())) }
                name = ""
                number = ""
            }
        }
    }

    SectionTitle("Speed alert")
    Group {
        Column(Modifier.padding(14.dp)) {
            Text("Beep above ${s.alertSpeed} km/h", fontSize = 15.sp)
            Slider(
                value = s.alertSpeed.toFloat(), onValueChange = { v -> prefs.edit { it.copy(alertSpeed = (v / 5).roundToInt() * 5) } },
                valueRange = 30f..120f, steps = 17,
                colors = SliderDefaults.colors(thumbColor = p.blue, activeTrackColor = p.blue),
            )
        }
        ToggleRow("Over-speed beep", null, s.speedBeep) { v -> prefs.edit { it.copy(speedBeep = v) } }
    }

    SectionTitle("Riding")
    Group {
        ToggleRow("Read messages aloud", "WhatsApp and SMS, in your earphones", s.readMessages) { v -> prefs.edit { it.copy(readMessages = v) } }
        ToggleRow("Crash detection", "Hard impact then stop → 15 s countdown → SMS to contacts", s.crashDetection) { v -> prefs.edit { it.copy(crashDetection = v) } }
        ToggleRow("Heat guard", "Voice warning when the phone gets too hot", s.heatGuard) { v -> prefs.edit { it.copy(heatGuard = v) } }
        ToggleRow("Ride lock", "No typing while moving", s.rideLock) { v -> prefs.edit { it.copy(rideLock = v) } }
        ToggleRow("Glove mode", "Bigger buttons", s.gloves) { v -> prefs.edit { it.copy(gloves = v) } }
        ToggleRow("Auto sunlight mode", "Switches to the high-contrast screen in bright sun and back to dark in shade or at night", s.autoTheme) { v -> prefs.edit { it.copy(autoTheme = v) } }
        if (!s.autoTheme) ToggleRow("Sunlight mode", "High-contrast black-on-white screen for glare", s.sunlight) { v -> prefs.edit { it.copy(sunlight = v) } }
    }
    Text("RiderDeck build ${com.riderdeck.app.BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = p.faint, modifier = Modifier.padding(top = 16.dp))
}

@Composable
private fun SosSheet(act: MainActivity, close: () -> Unit) {
    val p = LocalPal.current
    val s by RiderApp.prefs.s.collectAsState()
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    SheetHeader("Emergency", "Hold the button to text your location to your contacts")
    Box(
        Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(16.dp)).background(p.bad.copy(alpha = 0.2f))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        val start = System.currentTimeMillis()
                        while (true) {
                            progress = ((System.currentTimeMillis() - start) / 1500f).coerceAtMost(1f)
                            if (progress >= 1f) {
                                close()
                                act.sendSosNow()
                                break
                            }
                            delay(16)
                        }
                    }
                    tryAwaitRelease()
                    job.cancel()
                    progress = 0f
                })
            },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(p.bad))
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Warning, null)
            Text("Hold 1.5 s to send SOS", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("112" to "All emergencies", "108" to "Ambulance", "100" to "Police").forEach { (n, label) ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(p.glass).clickable { Nav.dial(act, n) }.padding(12.dp)
            ) {
                Text(n, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Call · $label", fontSize = 12.sp, color = p.muted)
            }
        }
    }
    SectionTitle("Gets your location")
    Group {
        if (s.contacts.isEmpty()) {
            Text("No emergency contacts yet. Add them in More → Settings.", fontSize = 13.sp, color = p.warn, modifier = Modifier.padding(14.dp))
        }
        s.contacts.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(14.dp)) {
                Text(c.name, Modifier.weight(1f))
                Text(c.number, color = p.muted)
            }
        }
    }
}

/* ======================= permissions ======================= */

private data class PermItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val why: String,
    val perms: List<String>,
    val special: Boolean = false,
    val check: ((android.content.Context) -> Boolean)? = null,
    val open: ((android.content.Context) -> Unit)? = null,
)

private fun ignoresBatterySaver(ctx: android.content.Context): Boolean =
    ctx.getSystemService(android.os.PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

@android.annotation.SuppressLint("BatteryLife")
private fun askBatterySaver(ctx: android.content.Context) {
    try {
        ctx.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(android.net.Uri.parse("package:" + ctx.packageName))
        )
    } catch (_: Exception) {
        try {
            ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            Hub.toast("Settings → Battery → RiderDeck → allow background use")
        }
    }
}

@Composable
fun PermissionList(act: MainActivity) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick++
        act.startTracking()
    }
    val items = listOf(
        PermItem(Icons.Rounded.Place, "Location", "Speed, trip, map and your SOS location",
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)),
        PermItem(Icons.Rounded.BatteryChargingFull, "Keep running in the background", "So the battery saver doesn't stop ride tracking while you're in Google Maps",
            emptyList(), check = ::ignoresBatterySaver, open = ::askBatterySaver),
        PermItem(Icons.Rounded.NotificationsActive, "Ride notification", "Shows RiderDeck is still tracking, with an End ride button",
            listOf(Manifest.permission.POST_NOTIFICATIONS)).takeIf { android.os.Build.VERSION.SDK_INT >= 33 },
        PermItem(Icons.Rounded.Notifications, "Notification access", "Music controls, Google Maps turns, calls and reading messages", emptyList(), special = true)
            .takeUnless { RiderApp.isLite },
        PermItem(Icons.Rounded.Phone, "Phone", "Answer and decline calls with big buttons",
            listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS)),
        PermItem(Icons.Rounded.Sms, "SMS", "Text your location to emergency contacts", listOf(Manifest.permission.SEND_SMS)),
        PermItem(Icons.Rounded.Mic, "Microphone", "Voice commands", listOf(Manifest.permission.RECORD_AUDIO)),
    )
    Group {
        items.filterNotNull().forEach { item ->
            val granted = remember(tick) {
                item.check?.invoke(ctx) ?: if (item.special) Perms.listener(ctx) else item.perms.all { Perms.has(ctx, it) }
            }
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !granted) {
                    val open = item.open
                    when {
                        open != null -> open(ctx)
                        item.special -> Perms.openListenerSettings(ctx)
                        else -> launcher.launch(item.perms.toTypedArray())
                    }
                }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(item.icon, null, tint = if (granted) p.ok else p.muted)
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(item.why, fontSize = 12.5.sp, color = p.muted)
                }
                Text(
                    if (granted) "On" else "Allow",
                    color = if (granted) p.ok else Color.White,
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp))
                        .background(if (granted) p.ok.copy(alpha = 0.15f) else p.blue)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
fun Onboarding(act: MainActivity, done: () -> Unit) {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize().background(p.bg).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            PopSurface(Modifier.size(62.dp), colors = popColors(PopKind.Accent), shimmer = true, shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Rounded.TwoWheeler, null, modifier = Modifier.size(32.dp))
            }
            Text("Set up RiderDeck", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 18.dp).enter(1))
            Text(
                "Allow what you want to use. You can change these later in More → Permissions.",
                fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            PermissionList(act)
            PillButton("Start riding", Modifier.fillMaxWidth().padding(top = 18.dp), icon = Icons.Rounded.TwoWheeler) {
                if (!Perms.location(act)) Hub.toast("Speed needs Location. You can allow it later")
                done()
            }
        }
    }
}

/* ======================= overlays ======================= */

@Composable
fun Overlays(act: MainActivity) {
    val p = LocalPal.current
    val call by Hub.call.collectAsState()
    val msg by Hub.message.collectAsState()
    val crash by Hub.crashCountdown.collectAsState()
    val voice by Hub.voice.collectAsState()
    val toast by Hub.toast.collectAsState()

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp, start = 12.dp, end = 12.dp).widthIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (call.ringing) CallCard(act, call.caller)
            else if (call.active) CallBar(act, call.caller, call.startedAt)
            msg?.let { m ->
                LaunchedEffect(m.id) {
                    delay(12_000)
                    if (Hub.message.value?.id == m.id) Hub.message.value = null
                }
                Banner(Icons.Rounded.Sms, p.ok, "${m.sender} · ${m.app}", m.text) {
                    Hub.message.value = null
                    Speaker.stop()
                }
            }
        }
        toast?.let { t ->
            LaunchedEffect(t.first) {
                delay(3200)
                if (Hub.toast.value?.first == t.first) Hub.toast.value = null
            }
        }
        var lastToast by remember { mutableStateOf("") }
        toast?.let { lastToast = it.second }
        AnimatedVisibility(
            visible = toast != null,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp, start = 16.dp, end = 16.dp),
            enter = slideInVertically(spring(dampingRatio = 0.6f, stiffness = 420f)) { -it * 2 } + fadeIn(),
            exit = slideOutVertically(tween(220)) { -it * 2 } + fadeOut(tween(220)),
        ) {
            PopSurface(Modifier.widthIn(max = 520.dp), colors = popColors(PopKind.Primary), shape = RoundedCornerShape(50)) {
                Text(
                    lastToast, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }
    }
    voice?.let { v -> VoiceOverlay(v.listening, v.text) { act.stopVoice() } }
    crash?.let { n -> CrashOverlay(n) { act.cancelCrash() } }
}

@Composable
private fun CallCard(act: MainActivity, caller: String) {
    val p = LocalPal.current
    val g = LocalGlove.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.sheet).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(50.dp).clip(CircleShape).background(Color(0xFF7C3AED)), contentAlignment = Alignment.Center) {
                Text(caller.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Column {
                Text(caller, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Incoming call", fontSize = 13.sp, color = p.muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigCallButton(Icons.Rounded.CallEnd, "Decline", p.bad, Modifier.weight(1f).height((66 * g).dp)) { Calls.decline(act) }
            BigCallButton(Icons.Rounded.Call, "Answer", Color(0xFF16A34A), Modifier.weight(1f).height((66 * g).dp)) { Calls.answer(act) }
        }
    }
}

@Composable
private fun BigCallButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val colors = if (color == LocalPal.current.bad) popColors(PopKind.Danger)
    else PopColors(Color(0xFF16A34A), Color.White, gradient = listOf(Color(0xFF22C55E), Color(0xFF15803D)), glow = Color(0xFF22C55E))
    PopSurface(modifier, colors = colors, onClick = onClick, shape = RoundedCornerShape(20.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null)
            Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CallBar(act: MainActivity, caller: String, startedAt: Long) {
    val p = LocalPal.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val secs = ((now - startedAt) / 1000).coerceAtLeast(0)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.sheet).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(16.dp)).background(p.ok.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Call, null, tint = p.ok)
        }
        Column(Modifier.weight(1f)) {
            Text(caller.ifBlank { "On a call" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${secs / 60}:${(secs % 60).toString().padStart(2, '0')}", fontSize = 13.sp, color = p.muted)
        }
        Text(
            "End", color = Color.White, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(p.bad).clickable { Calls.decline(act) }
                .padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun Banner(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String, text: String, onClose: () -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.sheet).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text, fontSize = 13.sp, color = p.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, "Dismiss", tint = p.muted)
        }
    }
}

@Composable
private fun VoiceOverlay(listening: Boolean, text: String, onClose: () -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier.fillMaxSize().background(p.bg.copy(alpha = 0.88f)).clickable(onClick = onClose).safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color.White, p.cyan, p.blue))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
            Text(if (listening) "LISTENING…" else "HEARD", fontSize = 12.sp, color = p.muted, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text.ifBlank { "Say “navigate home”, “next song” or “find a petrol pump”" },
                fontSize = if (text.isBlank()) 15.sp else 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                color = if (text.isBlank()) p.muted else p.fg, modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun CrashOverlay(secondsLeft: Int, onSafe: () -> Unit) {
    val tr = rememberInfiniteTransition(label = "crash")
    val ring by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "ring")
    Box(
        Modifier.fillMaxSize().background(Color(0xFF140404)).safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(320.dp)) {
            val r = size.minDimension / 2
            drawCircle(Color(0xFFFF4F4F).copy(alpha = (1f - ring) * 0.5f), r * (0.45f + 0.55f * ring), style = androidx.compose.ui.graphics.drawscope.Stroke(6f))
            drawCircle(Color(0xFFFF4F4F).copy(alpha = 0.18f), r * 0.45f)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("POSSIBLE CRASH DETECTED", color = Color(0xFFFF8A8A), fontSize = 13.sp, letterSpacing = 2.5.sp, fontWeight = FontWeight.ExtraBold)
            RollingNumber(secondsLeft, fontSize = 96.sp, color = Color.White)
            Text("Sending your location to your emergency contacts", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
            PopSurface(Modifier.padding(top = 8.dp).widthIn(min = 260.dp), colors = popColors(PopKind.Primary), shimmer = true, onClick = onSafe, shape = RoundedCornerShape(50)) {
                Text("I'm OK", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 22.dp, horizontal = 48.dp))
            }
        }
    }
}
