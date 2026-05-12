package com.patrollink.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object PatrolTextSize {
    val Hero = 34.sp
    val PageTitle = 24.sp
    val SectionTitle = 18.sp
    val CardTitle = 16.sp
    val Body = 14.sp
    val BodySmall = 12.sp
    val Label = 11.sp
    val Caption = 10.sp
}

object PatrolTextStyle {
    val Hero = TextStyle(fontSize = PatrolTextSize.Hero, fontWeight = FontWeight.Black, lineHeight = 40.sp)
    val PageTitle = TextStyle(fontSize = PatrolTextSize.PageTitle, fontWeight = FontWeight.Black, lineHeight = 30.sp)
    val SectionTitle = TextStyle(fontSize = PatrolTextSize.SectionTitle, fontWeight = FontWeight.Black, lineHeight = 24.sp)
    val CardTitle = TextStyle(fontSize = PatrolTextSize.CardTitle, fontWeight = FontWeight.Black, lineHeight = 22.sp)
    val Body = TextStyle(fontSize = PatrolTextSize.Body, fontWeight = FontWeight.Medium, lineHeight = 21.sp)
    val BodyStrong = TextStyle(fontSize = PatrolTextSize.Body, fontWeight = FontWeight.Black, lineHeight = 21.sp)
    val BodySmall = TextStyle(fontSize = PatrolTextSize.BodySmall, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
    val Label = TextStyle(fontSize = PatrolTextSize.Label, fontWeight = FontWeight.Black, lineHeight = 16.sp)
    val Caption = TextStyle(fontSize = PatrolTextSize.Caption, fontWeight = FontWeight.Black, lineHeight = 14.sp)
}

val PatrolTypography = Typography(
    displayLarge = PatrolTextStyle.Hero.copy(fontFamily = FontFamily.Default),
    headlineLarge = PatrolTextStyle.PageTitle,
    headlineMedium = PatrolTextStyle.SectionTitle,
    titleLarge = PatrolTextStyle.CardTitle,
    bodyLarge = PatrolTextStyle.Body,
    bodyMedium = PatrolTextStyle.BodySmall,
    labelLarge = PatrolTextStyle.Label,
    labelSmall = PatrolTextStyle.Caption
)
