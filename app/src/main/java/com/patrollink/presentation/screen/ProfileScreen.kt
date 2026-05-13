package com.patrollink.presentation.screen

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.CircleOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolygonOptions
import com.amap.api.maps.model.PolylineOptions
import com.patrollink.domain.GpsLocation
import com.patrollink.domain.DisplayThemeMode
import com.patrollink.domain.FontSizeMode
import com.patrollink.domain.AppUiState
import com.patrollink.domain.PatrolArea
import com.patrollink.domain.PatrolGeoPoint
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.component.PatrolCard
import com.patrollink.presentation.component.PrimaryAction
import com.patrollink.presentation.component.StatusTag
import com.patrollink.presentation.component.SystemBars
import com.patrollink.presentation.theme.PatrolDisplay
import com.patrollink.presentation.theme.PatrolTextStyle
import com.patrollink.presentation.theme.TechBlue
import com.patrollink.presentation.theme.Warning
import java.util.Locale
import android.graphics.Color as AndroidColor

@Composable
fun ProfileScreen(uiState: AppUiState, viewModel: PatrolViewModel, onOpenVersionInfo: () -> Unit) {
    val colors = PatrolDisplay.colors
    val titleColor = profileTitleColor()
    LaunchedEffect(Unit) {
        viewModel.refreshCurrentLocation()
    }
    SystemBars(statusBarColor = colors.topBar, navigationBarColor = colors.bottomBar, lightStatusBar = !colors.dark, lightNavigationBar = !colors.dark)
    val user = uiState.user
    Box(Modifier.fillMaxSize().background(colors.page)) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            item {
                PatrolCard(radius = 12, dark = true) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(
                            Modifier
                                .weight(0.38f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF0B63F6), Color(0xFF0B1326)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(user.name, color = titleColor, style = PatrolTextStyle.PageTitle.copy(fontSize = 22.sp, lineHeight = 27.sp))
                                StatusTag("已认证", TechBlue)
                            }
                            Text(user.badgeNo, color = TechBlue, style = PatrolTextStyle.BodyStrong.copy(fontSize = 14.sp, lineHeight = 19.sp))
                            Text(user.department, color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
            item {
                PatrolCard(radius = 12, dark = true) {
                    Column {
                        SectionHeading("执勤辖区", Icons.Filled.LocationOn, TechBlue)
                        Spacer(Modifier.height(10.dp))
                        Text(uiState.patrolArea.name, color = titleColor, style = PatrolTextStyle.BodyStrong.copy(fontSize = 15.sp, lineHeight = 21.sp))
                        Spacer(Modifier.height(3.dp))
                        Text("${uiState.patrolArea.teamName} | ${user.patrolGroup.substringAfter("| ", user.patrolGroup)}", color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Bold))
                        PatrolAreaMap(location = uiState.sosLocation, dutyArea = uiState.patrolArea.name, patrolArea = uiState.patrolArea)
                    }
                }
            }
            item {
                PatrolCard(radius = 12, dark = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeading("联络方式", Icons.Filled.ContactPhone, Color(0xFF22C55E))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ContactInfo(Icons.Filled.Phone, "手机号码", user.phone, Color(0xFF22C55E))
                            ContactInfo(Icons.Filled.Email, "警务邮箱", user.email, TechBlue)
                        }
                    }
                }
            }
            item {
                PatrolCard(radius = 12, dark = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeading("显示设置", Icons.Filled.Settings, Color(0xFF8B5CF6))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DisplaySettingGroup(
                                title = "字体大小",
                                options = listOf(
                                    DisplayOption("紧凑", FontSizeMode.Compact),
                                    DisplayOption("标准", FontSizeMode.Standard),
                                    DisplayOption("大号", FontSizeMode.Large)
                                ),
                                selected = uiState.fontSizeMode,
                                onSelect = viewModel::setFontSizeMode
                            )
                            DisplaySettingGroup(
                                title = "主题模式",
                                options = listOf(
                                    DisplayOption("跟随系统", DisplayThemeMode.System),
                                    DisplayOption("浅色", DisplayThemeMode.Light),
                                    DisplayOption("深色", DisplayThemeMode.Dark)
                                ),
                                selected = uiState.displayThemeMode,
                                onSelect = viewModel::setDisplayThemeMode
                            )
                        }
                    }
                }
            }
            item {
                PatrolCard(
                    modifier = Modifier.clickable(onClick = onOpenVersionInfo),
                    radius = 12,
                    dark = true
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeading("版本信息", Icons.Filled.Info, Warning)
                        Text("执法链路 v${uiState.versionUpdate.currentVersionName} · 加密通道已启用 · 核心服务已同步", color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Bold))
                        StatusTag(uiState.versionUpdate.latestVersionName?.let { "发现新版本 v$it" } ?: "点击检查更新", Warning)
                    }
                }
            }
            item {
                PrimaryAction("退出登录", onClick = viewModel::logout, modifier = Modifier.fillMaxWidth(), danger = true)
            }
            }
        }
    }
}

private data class DisplayOption<T>(val label: String, val value: T)

@Composable
private fun profileTitleColor(): Color {
    val colors = PatrolDisplay.colors
    return if (colors.dark) colors.text else Color(0xFF1E293B)
}

@Composable
private fun SectionHeading(title: String, icon: ImageVector, accent: Color) {
    val colors = PatrolDisplay.colors
    val titleColor = profileTitleColor()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(accent.copy(alpha = if (colors.dark) 0.18f else 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
        }
        Text(title, color = titleColor, style = PatrolTextStyle.CardTitle.copy(fontSize = 16.sp, lineHeight = 21.sp))
    }
}

@Composable
private fun PatrolAreaMap(location: GpsLocation, dutyArea: String, patrolArea: PatrolArea) {
    var expanded by remember { mutableStateOf(false) }

    DutyMapView(
        location = location,
        dutyArea = dutyArea,
        patrolArea = patrolArea,
        expanded = false,
        onMapClick = { expanded = true },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .aspectRatio(2.2f)
    )

    if (expanded) {
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                DutyMapView(
                    location = location,
                    dutyArea = dutyArea,
                    patrolArea = patrolArea,
                    expanded = true,
                    onMapClick = {},
                    modifier = Modifier.fillMaxSize()
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(18.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.58f))
                        .clickable { expanded = false }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("关闭", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun DutyMapView(
    location: GpsLocation,
    dutyArea: String,
    patrolArea: PatrolArea,
    expanded: Boolean,
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLabel = "${"%.4f".format(Locale.CHINA, location.latitude)}, ${"%.4f".format(Locale.CHINA, location.longitude)}"
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val dark = PatrolDisplay.colors.dark
    val shape = if (expanded) RoundedCornerShape(0.dp) else RoundedCornerShape(10.dp)
    val mapView = remember {
        MapsInitializer.updatePrivacyShow(context, true, true)
        MapsInitializer.updatePrivacyAgree(context, true)
        MapView(context).apply {
            onCreate(Bundle())
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF07111F), Color(0xFF123C8A), Color(0xFF020617))))
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view -> view.configureDutyMap(location, patrolArea, expanded, dark, onMapClick) }
        )

        val topOverlayModifier = if (expanded) {
            Modifier.statusBarsPadding()
        } else {
            Modifier
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .then(topOverlayModifier)
                .padding(10.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("当前辖区", color = Color.White.copy(alpha = 0.72f), fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text(dutyArea, color = Color.White, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black)
            if (expanded) {
                Text(
                    "精度 ${"%.1f".format(Locale.CHINA, location.accuracyMeters)}m",
                    color = Color(0xFFBFDBFE),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        if (!expanded) {
            Text(
                "精度 ${"%.1f".format(Locale.CHINA, location.accuracyMeters)}m",
                color = Color(0xFFBFDBFE),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = 0.34f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (!expanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(10.dp)
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(onClick = onMapClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.OpenInFull, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = if (expanded) 76.dp else 18.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.52f))
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { mapView.map?.animateCamera(CameraUpdateFactory.zoomIn()) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { mapView.map?.animateCamera(CameraUpdateFactory.zoomOut()) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .then(if (expanded) Modifier.navigationBarsPadding() else Modifier)
                .padding(10.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color.Black.copy(alpha = 0.48f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = TechBlue, modifier = Modifier.size(14.dp))
            Text("当前位置 $currentLabel", color = Color.White, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

private fun MapView.configureDutyMap(location: GpsLocation, patrolArea: PatrolArea, expanded: Boolean, dark: Boolean, onMapClick: () -> Unit) {
    val aMap = map ?: return
    val point = location.toAmapLatLng(this)
    val route = patrolArea.route.map { it.toAmapLatLng(this) }
    val boundary = patrolArea.boundary.map { it.toAmapLatLng(this) }

    aMap.clear()
    aMap.mapType = if (dark) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL
    aMap.uiSettings.apply {
        isZoomControlsEnabled = false
        isScaleControlsEnabled = expanded
        isCompassEnabled = expanded
        isMyLocationButtonEnabled = false
        setAllGesturesEnabled(expanded)
    }
    aMap.setOnMapClickListener { onMapClick() }
    aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(point, if (expanded) 16.8f else 15.8f))
    if (boundary.size >= 3) {
        aMap.addPolygon(
            PolygonOptions()
                .addAll(boundary)
                .strokeColor(if (dark) AndroidColor.argb(225, 96, 165, 250) else AndroidColor.argb(210, 37, 99, 235))
                .fillColor(if (dark) AndroidColor.argb(42, 96, 165, 250) else AndroidColor.argb(34, 37, 99, 235))
                .strokeWidth(4f)
        )
    }
    if (route.size >= 2) {
        aMap.addPolyline(
            PolylineOptions()
                .addAll(route)
                .width(9f)
                .color(if (dark) AndroidColor.argb(235, 34, 211, 238) else AndroidColor.argb(230, 8, 145, 178))
        )
    }
    aMap.addCircle(
        CircleOptions()
            .center(point)
            .radius(location.accuracyMeters.coerceAtLeast(30f).toDouble())
            .strokeColor(if (dark) AndroidColor.argb(210, 96, 165, 250) else AndroidColor.argb(190, 11, 99, 246))
            .fillColor(if (dark) AndroidColor.argb(48, 96, 165, 250) else AndroidColor.argb(42, 11, 99, 246))
            .strokeWidth(3f)
    )
    aMap.addMarker(
        MarkerOptions()
            .position(point)
            .title("当前位置")
            .snippet(location.address)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
    )
}

private fun GpsLocation.toAmapLatLng(mapView: MapView): LatLng {
    return toConvertedAmapLatLng(latitude, longitude, mapView)
}

private fun PatrolGeoPoint.toAmapLatLng(mapView: MapView): LatLng {
    return toConvertedAmapLatLng(latitude, longitude, mapView)
}

private fun toConvertedAmapLatLng(latitude: Double, longitude: Double, mapView: MapView): LatLng {
    val gpsPoint = LatLng(latitude, longitude)
    return runCatching {
        CoordinateConverter(mapView.context.applicationContext)
            .from(CoordinateConverter.CoordType.GPS)
            .coord(gpsPoint)
            .convert()
    }.getOrDefault(gpsPoint)
}

@Composable
private fun ContactInfo(icon: ImageVector, label: String, value: String, accent: Color) {
    val colors = PatrolDisplay.colors
    val titleColor = profileTitleColor()
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Black))
            Text(value, color = titleColor, style = PatrolTextStyle.BodyStrong.copy(fontSize = 15.sp, lineHeight = 21.sp))
        }
    }
}

@Composable
private fun <T> DisplaySettingGroup(
    title: String,
    options: List<DisplayOption<T>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    val colors = PatrolDisplay.colors
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Black))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.control)
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { option ->
                val active = option.value == selected
                Text(
                    text = option.label,
                    color = if (active) Color.White else colors.textMuted,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) TechBlue else Color.Transparent)
                        .clickable { onSelect(option.value) }
                        .padding(top = 8.dp)
                )
            }
        }
    }
}
