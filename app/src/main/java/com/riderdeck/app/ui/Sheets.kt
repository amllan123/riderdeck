package com.riderdeck.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
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
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(bottom = 28.dp).navigationBarsPadding()
        ) {
            when (name) {
                "goto" -> GoToSheet(act, open, close)
                "near" -> NearbySheet(act, close)
                "music" -> MusicSheet(act)
                "fuel" -> FuelSheet()
                "more" -> MoreSheet(act, open, close)
                "ride" -> RideSheet(act)
                "settings" -> SettingsSheet(act)
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

@Composable
private fun GoToSheet(act: MainActivity, open: (String) -> Unit, close: () -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val ride by Hub.ride.collectAsState()
    val s by RiderApp.prefs.s.collectAsState()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf("") }
    val locked = s.rideLock && ride.speedKmh > 10f
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (!locked) try { focus.requestFocus() } catch (_: Exception) { }
    }
    LaunchedEffect(q) {
        val text = q.trim()
        if (text.length < 3) {
            results = emptyList(); searched = ""; return@LaunchedEffect
        }
        delay(450)
        busy = true
        results = PlaceSearch.find(ctx, text)
        searched = text
        busy = false
    }
    fun go(place: String) {
        if (place.isBlank()) return
        close()
        Navigator.previewQuery(ctx, place, autoStart = false)
    }
    fun show(pl: Place) {
        Hub.pin.value = Pin(pl.lat, pl.lon, pl.title)
        close()
    }

    SheetHeader("Search", if (locked) "Typing is locked while you're moving. Say it or pick a saved place" else "Find any place, show it on the map or start a route")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = q, onValueChange = { q = it }, enabled = !locked, singleLine = true,
            placeholder = { Text(if (locked) "Locked while moving" else "Place, shop, address, landmark") },
            leadingIcon = { Icon(if (locked) Icons.Rounded.Lock else Icons.Rounded.Search, null) },
            trailingIcon = {
                if (q.isNotEmpty()) {
                    Box(Modifier.size(40.dp).clip(CircleShape).clickable { q = "" }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, "Clear")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                val first = results.firstOrNull()
                if (first != null) show(first) else if (q.isNotBlank()) Nav.searchNearby(act, q.trim())
            }),
            shape = RoundedCornerShape(18.dp), colors = riderFieldColors(),
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        RoundFab(Icons.Rounded.Mic, "Say a place", true) { close(); act.startVoice() }
    }

    when {
        busy -> Text("Searching…", fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(top = 14.dp, start = 4.dp))
        results.isNotEmpty() -> {
            SectionTitle("Results")
            Group {
                results.forEach { pl -> ResultRow(pl, onShow = { show(pl) }, onGo = { close(); Navigator.preview(Pin(pl.lat, pl.lon, pl.title)) }) }
            }
        }
        searched.isNotEmpty() -> {
            Text("No matches for “$searched”.", fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(top = 14.dp, start = 4.dp))
            PillButton("Search “$searched” in Google Maps", Modifier.fillMaxWidth().padding(top = 8.dp), primary = false, icon = Icons.Rounded.Search) {
                close(); Nav.searchNearby(act, searched)
            }
        }
    }

    if (q.isBlank()) {
        SectionTitle("Saved")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(Icons.Rounded.Home, p.blue, "Home", s.home.ifBlank { "Tap to set your home address" }) {
                if (s.home.isBlank()) open("settings") else go(s.home)
            }
            Tile(Icons.Rounded.Work, p.cyan, "Office", s.office.ifBlank { "Tap to set your office address" }) {
                if (s.office.isBlank()) open("settings") else go(s.office)
            }
            Tile(Icons.Rounded.LocalGasStation, p.warn, "Nearby", "Petrol, puncture shop, air, mechanic, hospital") { open("near") }
        }
        Text(
            "“Go” plans a two-wheeler route on the RiderDeck map with voice turn-by-turn. You can still hand it to Google Maps from the route card.",
            fontSize = 12.5.sp, color = p.muted, modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun ResultRow(pl: Place, onShow: () -> Unit, onGo: () -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onShow).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Place, null, tint = p.bad)
        Column(Modifier.weight(1f)) {
            Text(pl.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val dist = pl.distanceKm?.let { formatKm(it) + " · " } ?: ""
            Text(dist + pl.sub, fontSize = 12.sp, color = p.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "Map", color = p.fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(p.glass).clickable(onClick = onShow)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        )
        Text(
            "Go", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(p.blue, p.cyan)))
                .clickable(onClick = onGo).padding(horizontal = 14.dp, vertical = 9.dp),
        )
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
    SheetHeader("Fuel", "Range is worked out from your tank size, mileage and the km you've ridden since filling up")
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
}

@Composable
private fun Hero(value: String, label: String, modifier: Modifier, color: Color = LocalPal.current.fg) {
    val p = LocalPal.current
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(p.glass).padding(12.dp)) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = color, maxLines = 1)
        Text(label, fontSize = 12.sp, color = p.muted, maxLines = 2)
    }
}

@Composable
private fun RideSheet(act: MainActivity) {
    val ride by Hub.ride.collectAsState()
    SheetHeader("This ride", "Since you opened RiderDeck")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Hero("%.1f".format(ride.tripKm), "km", Modifier.weight(1f))
        Hero(duration(ride.movingSecs), "moving time", Modifier.weight(1f))
        Hero("${ride.avgKmh.roundToInt()}", "avg km/h", Modifier.weight(1f))
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Hero("${ride.topKmh.roundToInt()}", "top km/h", Modifier.weight(1f))
        Hero("${ride.maxLeanDeg.roundToInt()}°", "max lean", Modifier.weight(1f))
        Hero("${ride.altitudeM.roundToInt()}", "altitude m", Modifier.weight(1f))
    }
    PillButton("Reset trip", Modifier.fillMaxWidth().padding(top = 14.dp), primary = false) {
        act.tracker.resetTrip()
        Hub.toast("Trip reset")
    }
}

@Composable
private fun MoreSheet(act: MainActivity, open: (String) -> Unit, close: () -> Unit) {
    val p = LocalPal.current
    SheetHeader("More")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile(Icons.Rounded.Speed, p.blue, "This ride", "Distance, time, top speed, max lean") { open("ride") }
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
        ToggleRow("Heat guard", "Dims the screen when the phone passes 43°C", s.heatGuard) { v -> prefs.edit { it.copy(heatGuard = v) } }
        ToggleRow("Ride lock", "No typing while moving", s.rideLock) { v -> prefs.edit { it.copy(rideLock = v) } }
        ToggleRow("Glove mode", "Bigger buttons", s.gloves) { v -> prefs.edit { it.copy(gloves = v) } }
        ToggleRow("Sunlight mode", "Bright, high-contrast screen for daytime", s.sunlight) { v -> prefs.edit { it.copy(sunlight = v) } }
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
        Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(24.dp)).background(p.bad.copy(alpha = 0.2f))
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
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(p.glass).clickable { Nav.dial(act, n) }.padding(12.dp)
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
)

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
                if (item.special) Perms.listener(ctx) else item.perms.all { Perms.has(ctx, it) }
            }
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !granted) {
                    if (item.special) Perms.openListenerSettings(ctx) else launcher.launch(item.perms.toTypedArray())
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
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
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
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(p.blue, p.cyan))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.TwoWheeler, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
            Text("Set up RiderDeck", fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 16.dp))
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
                delay(3000)
                if (Hub.toast.value?.first == t.first) Hub.toast.value = null
            }
            Text(
                t.second, color = p.fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp, start = 16.dp, end = 16.dp)
                    .widthIn(max = 520.dp).clip(RoundedCornerShape(99.dp)).background(p.sheet)
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            )
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(p.sheet).padding(14.dp),
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
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(color).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color.White)
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(p.ok.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Call, null, tint = p.ok)
        }
        Column(Modifier.weight(1f)) {
            Text(caller.ifBlank { "On a call" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${secs / 60}:${(secs % 60).toString().padStart(2, '0')}", fontSize = 13.sp, color = p.muted)
        }
        Text(
            "End", color = Color.White, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(p.bad).clickable { Calls.decline(act) }
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
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
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
    Box(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFFFF4D5A), Color(0xFF7A0D16)))).safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("POSSIBLE CRASH DETECTED", color = Color.White, fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
            Text("$secondsLeft", color = Color.White, fontSize = 88.sp, fontWeight = FontWeight.Black)
            Text("Sending your location to your emergency contacts", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                "I'm OK", color = Color(0xFF7A0D16), fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp).widthIn(min = 260.dp).clip(RoundedCornerShape(26.dp)).background(Color.White)
                    .clickable(onClick = onSafe).padding(vertical = 24.dp),
            )
        }
    }
}
