package pubg.radar.ui

import com.badlogic.gdx.graphics.Color
import pubg.radar.gridWidth
import pubg.radar.mapWidth

const val initialWindowWidth = 1000f
const val windowToMapUnit = mapWidth / initialWindowWidth

const val runSpeed = 6.3 * 100//6.3m/s
const val unit = gridWidth / 8
const val unit2 = unit / 10
//1m=100
const val playerRadius = 4000f
const val vehicle2Width = 400f
const val vehicle4Width = 800f
const val vehicle6Width = 1600f
//const val directionRadius = 16000f
const val directionRadius = 10000f
const val airDropRadius = 4000f
const val corpseRadius = 300f
//const val itemRadius = 300f
const val itemRadius = 50f
const val visionRadius = mapWidth / 8
const val fov = 90f

const val aimLineWidth = 1000f
const val aimLineRange = 50000f
const val aimCircleRadius = 200f
const val aimTimeThreshold = 1000
const val attackLineDuration = 1000
const val attackMeLineDuration = 10000
const val pinRadius = 4000f

val teamColor = Color(1f, 1f, 0f, 1f)
val safeDirectionColor = Color(0.12f, 0.56f, 1f, 0.5f)
val visionColor = Color(1f, 1f, 1f, 0.1f)
val corpseColor = Color(1f, 1f, 0f, 1f)
val carColor = Color(0.12f, 0.56f, 1f, 0.9f)
val boatColor = Color(1f, 0.49f, 0f, 0.9f)
val planeColor = Color(0.93f, 0.90f, 0f, 1.0f)
val parachuteColor = Color(0.94f, 1.0f, 1.0f, 1f)
val playerColor = Color.RED!!
val compaseColor = Color(0f, 0.95f, 1f, 1f)  //Turquoise1
val normalItemColor = Color(0.87f, 0.0f, 1.0f, 1f)
val rareScopeColor = Color(1.000f, 0.843f, 0.000f, 1.0f)         // Gold Triangle
val rareArmorColor = Color(0.000f, 1.000f, 1.000f, 1.0f)            // LIGHT BLUE
val ARiflesColor = Color(0.486f, 0.988f, 0.000f, 1.0f)      // Lime Green
val sniperColor = Color(1.000f, 0.271f, 0.000f, 1.0f)           // OrangeRed
val rareAttachColor = Color(0.31f, 0.51f, 0.71f, 1.0f)      // Light Blue Circle
val suppressorColor = Color(0.89f, 0.85f, 0.79f, 1.0f)      // White triangle
val healItemColor = Color(0.56f, 0.93f, 0.56f, 1.0f)        // Green Circle
val k98Color = Color(0.545f, 0.271f, 0.075f, 1.0f)               // Brown
val UmpandPanColor = Color(0.73f, 0.56f, 1.0f, 1.0f)                 //PINK
val sightColor = Color(0.980f, 0.922f, 0.843f, 1.0f)            //purple
val aimLineColor = Color(0f, 0f, 1f, 1f)
val attackLineColor = Color(1.0f, 0f, 0f, 1f)
val pinColor = Color(1f, 1f, 0f, 1f)
val redZoneColor = Color(1f, 0f, 0f, 0.2f)
val safeZoneColor = Color(1f, 1f, 1f, 0.5f)
