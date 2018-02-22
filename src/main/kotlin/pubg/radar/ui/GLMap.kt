package pubg.radar.ui

import com.badlogic.gdx.*
import com.badlogic.gdx.Input.Buttons.LEFT
import com.badlogic.gdx.Input.Buttons.RIGHT
import com.badlogic.gdx.Input.Buttons.MIDDLE
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.backends.lwjgl3.*
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.Color.*
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.*
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.*
import com.badlogic.gdx.math.*
import pubg.radar.*
import pubg.radar.deserializer.channel.ActorChannel.Companion.actors
import pubg.radar.deserializer.channel.ActorChannel.Companion.airDropLocation
import pubg.radar.deserializer.channel.ActorChannel.Companion.corpseLocation
import pubg.radar.deserializer.channel.ActorChannel.Companion.droppedItemLocation
import pubg.radar.deserializer.channel.ActorChannel.Companion.visualActors
import pubg.radar.http.PlayerProfile.Companion.completedPlayerInfo
import pubg.radar.http.PlayerProfile.Companion.pendingPlayerInfo
import pubg.radar.http.PlayerProfile.Companion.query
import pubg.radar.sniffer.Sniffer.Companion.targetAddr
import pubg.radar.sniffer.Sniffer.Companion.preDirection
import pubg.radar.sniffer.Sniffer.Companion.preSelfCoords
import pubg.radar.sniffer.Sniffer.Companion.selfCoords
import pubg.radar.sniffer.Sniffer.Companion.sniffOption
import pubg.radar.struct.*
import pubg.radar.struct.Archetype.*
import pubg.radar.struct.Archetype.Plane
import pubg.radar.struct.cmd.ActorCMD.actorWithPlayerState
import pubg.radar.struct.cmd.ActorCMD.playerStateToActor
import pubg.radar.struct.cmd.GameStateCMD.ElapsedWarningDuration
import pubg.radar.struct.cmd.GameStateCMD.MatchElapsedMinutes
import pubg.radar.struct.cmd.GameStateCMD.NumAlivePlayers
import pubg.radar.struct.cmd.GameStateCMD.NumAliveTeams
import pubg.radar.struct.cmd.GameStateCMD.PoisonGasWarningPosition
import pubg.radar.struct.cmd.GameStateCMD.PoisonGasWarningRadius
import pubg.radar.struct.cmd.GameStateCMD.RedZonePosition
import pubg.radar.struct.cmd.GameStateCMD.RedZoneRadius
import pubg.radar.struct.cmd.GameStateCMD.SafetyZonePosition
import pubg.radar.struct.cmd.GameStateCMD.SafetyZoneRadius
import pubg.radar.struct.cmd.GameStateCMD.TotalWarningDuration
import pubg.radar.struct.cmd.PlayerStateCMD.attacks
import pubg.radar.struct.cmd.PlayerStateCMD.playerNames
import pubg.radar.struct.cmd.PlayerStateCMD.playerNumKills
import pubg.radar.struct.cmd.PlayerStateCMD.selfID
import pubg.radar.struct.cmd.PlayerStateCMD.teamNumbers
import pubg.radar.ui.GLMap.Companion.component1
import pubg.radar.util.tuple4
import wumo.pubg.struct.cmd.TeamCMD.team
import java.util.*
import java.util.Vector
import kotlin.collections.ArrayList
import kotlin.math.*

typealias renderInfo = tuple4<Actor, Float, Float, Float>

fun Float.d(n: Int) = String.format("%.${n}f", this)
class GLMap : InputAdapter(), ApplicationListener, GameListener {
  companion object {
    operator fun Vector3.component1(): Float = x
    operator fun Vector3.component2(): Float = y
    operator fun Vector3.component3(): Float = z
    operator fun Vector2.component1(): Float = x
    operator fun Vector2.component2(): Float = y

    val spawnErangel = Vector2(795548.3f, 17385.875f)
    val spawnDesert = Vector2(78282f, 731746f)
  }

  init {
    register(this)
  }

  override fun onGameStart() {
    preSelfCoords.set(if (isErangel) spawnErangel else spawnDesert)
    selfCoords.set(preSelfCoords)
    preDirection.setZero()
  }

  override fun onGameOver() {
    camera.zoom = 1 / 4f

    aimStartTime.clear()
    attackLineStartTime.clear()
    pinLocation.setZero()
  }

  fun show() {
    val config = Lwjgl3ApplicationConfiguration()
    config.setTitle("[${targetAddr.hostAddress} ${sniffOption.name}] - Radar")
    config.useOpenGL3(true, 3, 3)
    config.setWindowedMode(1000, 1000)
    config.setResizable(true)
    config.setBackBufferConfig(8, 8, 8, 8, 32, 0, 8)
    Lwjgl3Application(this, config)
  }

  lateinit var spriteBatch: SpriteBatch
  lateinit var shapeRenderer: ShapeRenderer
  // lateinit var mapErangel: Texture
  // lateinit var mapMiramar: Texture
  lateinit var mapErangelTiles: MutableMap<String, MutableMap<String, MutableMap<String, Texture>>>
  lateinit var mapMiramarTiles: MutableMap<String, MutableMap<String, MutableMap<String, Texture>>>
  lateinit var mapTiles: MutableMap<String, MutableMap<String, MutableMap<String, Texture>>>
  lateinit var iconImages: Map<String, Texture>
  lateinit var airdropimage: Texture
  lateinit var corpseboximage: Texture
  lateinit var alive: Texture
  lateinit var selficon: Texture
  lateinit var teamsalive: Texture

  // lateinit var map: Texture
  lateinit var largeFont: BitmapFont
  lateinit var littleFont: BitmapFont
  lateinit var nameFont: BitmapFont
  lateinit var itemFont: BitmapFont
  lateinit var fontCamera: OrthographicCamera
  lateinit var camera: OrthographicCamera
  lateinit var alarmSound: Sound

  val tileZooms = listOf("256", "512", "1024", "2048", "4096", "8192")
  val tileRowCounts = listOf(1, 2, 4, 8, 16, 32)
  val tileSizes = listOf(819200f, 409600f, 204800f, 102400f, 51200f, 25600f)

  val layout = GlyphLayout()
  var windowWidth = initialWindowWidth
  var windowHeight = initialWindowWidth

  val aimStartTime = HashMap<NetworkGUID, Long>()
  val attackLineStartTime = LinkedList<Triple<NetworkGUID, NetworkGUID, Long>>()
  val pinLocation = Vector2()

  var dragging = false
  var prevScreenX = -1f
  var prevScreenY = -1f
  var screenOffsetX = 0f
  var screenOffsetY = 0f

  fun Vector2.windowToMap() =
      Vector2(selfCoords.x + (x - windowWidth / 2.0f) * camera.zoom * windowToMapUnit + screenOffsetX,
              selfCoords.y + (y - windowHeight / 2.0f) * camera.zoom * windowToMapUnit + screenOffsetY)

  fun Vector2.mapToWindow() =
      Vector2((x - selfCoords.x - screenOffsetX) / (camera.zoom * windowToMapUnit) + windowWidth / 2.0f,
              (y - selfCoords.y - screenOffsetY) / (camera.zoom * windowToMapUnit) + windowHeight / 2.0f)

  override fun scrolled(amount: Int): Boolean {
    camera.zoom *= 1.1f.pow(amount)
    return true
  }


  override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
    if (button == RIGHT) {
    pinLocation.set(pinLocation.set(screenX.toFloat(), screenY.toFloat()).windowToMap())
      camera.update()
      println(pinLocation)
      return true
    } else if (button == LEFT) {
      dragging = true
      prevScreenX = screenX.toFloat()
      prevScreenY = screenY.toFloat()
      return true
    } else if (button == MIDDLE) {
      screenOffsetX = 0f
      screenOffsetY = 0f
    }
    return false
  }

  override fun touchDragged (screenX: Int, screenY: Int, pointer: Int): Boolean {
		if (!dragging) return false
    with (camera) {
      screenOffsetX += (prevScreenX - screenX.toFloat()) * camera.zoom * 500
      screenOffsetY += (prevScreenY - screenY.toFloat()) * camera.zoom * 500
      prevScreenX = screenX.toFloat()
      prevScreenY = screenY.toFloat()
    }
		return true
	}

  override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
    if (button == LEFT) {
      dragging = false
      return true
    }
    return false
  }

  override fun create() {
    spriteBatch = SpriteBatch()
    shapeRenderer = ShapeRenderer()
    Gdx.input.inputProcessor = this
    camera = OrthographicCamera(windowWidth, windowHeight)
    with(camera) {
      setToOrtho(true, windowWidth * windowToMapUnit, windowHeight * windowToMapUnit)
      zoom = 1 / 4f
      update()
      position.set(mapWidth / 2, mapWidth / 2, 0f)
      update()
    }

    fontCamera = OrthographicCamera(initialWindowWidth, initialWindowWidth)
    alarmSound = Gdx.audio.newSound(Gdx.files.internal("Alarm.wav"))
    corpseboximage = Texture(Gdx.files.internal("icons/box.png"))
    alive = Texture(Gdx.files.internal("images/alive.png"))
    teamsalive = Texture(Gdx.files.internal("images/teams.png"))
    selficon = Texture(Gdx.files.internal("images/self.png"))
    airdropimage = Texture(Gdx.files.internal("icons/airdrop.png"))
    // mapErangel = Texture(Gdx.files.internal("Erangel.bmp"))
    // mapMiramar = Texture(Gdx.files.internal("Miramar.bmp"))
    iconImages = mapOf(
      "AR.Supp" to Texture(Gdx.files.internal("icons/AR.Supp.png")),
      "S.Supp" to Texture(Gdx.files.internal("icons/S.Supp.png")),
      "AR.Comp" to Texture(Gdx.files.internal("icons/AR.Comp.png")),
      "S.Comp" to Texture(Gdx.files.internal("icons/S.Comp.png")),
      "AR.ExtQ" to Texture(Gdx.files.internal("icons/AR.ExtQ.png")),
      "S.ExtQ" to Texture(Gdx.files.internal("icons/S.ExtQ.png")),
      "AR.Ext" to Texture(Gdx.files.internal("icons/AR.Ext.png")),
      "S.Ext" to Texture(Gdx.files.internal("icons/S.Ext.png")),
      "AR.Stock" to Texture(Gdx.files.internal("icons/AR.Stock.png")),
      "CheekPad" to Texture(Gdx.files.internal("icons/CheekPad.png")),
      "A.Grip" to Texture(Gdx.files.internal("icons/A.Grip.png")),
      "V.Grip" to Texture(Gdx.files.internal("icons/V.Grip.png")),
      "556" to Texture(Gdx.files.internal("icons/556.png")),
      "98k" to Texture(Gdx.files.internal("icons/98k.png")),
      "Mini" to Texture(Gdx.files.internal("icons/Mini.png")),
      "m416" to Texture(Gdx.files.internal("icons/M4.png")),
      "m16" to Texture(Gdx.files.internal("icons/M16.png")),
      "Scar" to Texture(Gdx.files.internal("icons/Scar.png")),
      "Ak" to Texture(Gdx.files.internal("icons/Ak.png")),
      "DP" to Texture(Gdx.files.internal("icons/Dp.png")),
      "8x" to Texture(Gdx.files.internal("icons/8x.png")),
      "4x" to Texture(Gdx.files.internal("icons/4x.png")),
      "2x" to Texture(Gdx.files.internal("icons/2x.png")),
      "R.Dot" to Texture(Gdx.files.internal("icons/R.Dot.png")),
      "Holo" to Texture(Gdx.files.internal("icons/Holo.png")),
      "armor3" to Texture(Gdx.files.internal("icons/Arm3.png")),
      "armor2" to Texture(Gdx.files.internal("icons/Arm2.png")),
      "helmet3" to Texture(Gdx.files.internal("icons/Helm3.png")),
      "helmet2" to Texture(Gdx.files.internal("icons/Helm2.png")),
      "Bag3" to Texture(Gdx.files.internal("icons/Bag3.png")),
      "Bag2" to Texture(Gdx.files.internal("icons/Bag2.png")),
      "Pan" to Texture(Gdx.files.internal("icons/Pan.png")),
      "MedKit" to Texture(Gdx.files.internal("icons/MedKit.png")),
      "FirstAid" to Texture(Gdx.files.internal("icons/FirstAid.png")),
      "Pain" to Texture(Gdx.files.internal("icons/Pain.png")),
      "Drink" to Texture(Gdx.files.internal("icons/Drink.png")),
      "Grenade" to Texture(Gdx.files.internal("icons/Grenade.png"))
    )
    mapErangelTiles = mutableMapOf()
    mapMiramarTiles = mutableMapOf()
    var cur = 0
    tileZooms.forEach{
        mapErangelTiles.set(it, mutableMapOf())
        mapMiramarTiles.set(it, mutableMapOf())
        for (i in 1..tileRowCounts[cur]) {
            val y = if (i < 10) "0$i" else "$i"
            mapErangelTiles[it]?.set(y, mutableMapOf())
            mapMiramarTiles[it]?.set(y, mutableMapOf())
            for (j in 1..tileRowCounts[cur]) {
                val x = if (j < 10) "0$j" else "$j"
                mapErangelTiles[it]!![y]?.set(x, Texture(Gdx.files.internal("tiles/Erangel/${it}/${it}_${y}_${x}.png")))
                mapMiramarTiles[it]!![y]?.set(x, Texture(Gdx.files.internal("tiles/Miramar/${it}/${it}_${y}_${x}.png")))
            }
        }
        cur++
    }
    mapTiles = mapErangelTiles
    // map = mapErangel

    val generator = FreeTypeFontGenerator(Gdx.files.internal("GOTHICB.TTF"))
    val param = FreeTypeFontParameter()
    param.size = 38
    param.characters = DEFAULT_CHARS
    param.color = WHITE
    largeFont = generator.generateFont(param)
    param.size = 20
    param.color = WHITE
    littleFont = generator.generateFont(param)
    param.color = BLACK
    param.size = 10
    nameFont = generator.generateFont(param)
    param.color = ORANGE
    param.size = 10
    itemFont = generator.generateFont(param)
    generator.dispose()
  }

  val dirUnitVector = Vector2(1f, 0f)
  override fun render() {
    Gdx.gl.glClearColor(0.417f, 0.417f, 0.417f, 0f)
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    if (gameStarted)
      mapTiles = if (isErangel) mapErangelTiles else mapMiramarTiles
    else return
    val currentTime = System.currentTimeMillis()
    val (selfX, selfY) = selfCoords
    val selfDir = Vector2(selfX, selfY).sub(preSelfCoords)
    if (selfDir.len() < 1e-8)
      selfDir.set(preDirection)

    //move camera
    camera.position.set(selfX + screenOffsetX, selfY + screenOffsetY, 0f)
    camera.update()


    val cameraTileScale = Math.max(windowWidth, windowHeight) / camera.zoom
    var useScale = 0
    when {
        cameraTileScale > 4096 -> useScale = 5
        cameraTileScale > 2048 -> useScale = 4
        cameraTileScale > 1024 -> useScale = 3
        cameraTileScale > 512 -> useScale = 2
        cameraTileScale > 256 -> useScale = 1
        else -> useScale = 0
    }
    val (tlX, tlY) = Vector2(0f, 0f).windowToMap()
    val (brX, brY) = Vector2(windowWidth, windowHeight).windowToMap()
    var tileZoom = tileZooms[useScale]
    var tileRowCount = tileRowCounts[useScale]
    var tileSize = tileSizes[useScale]
    paint(camera.combined) {
      val xMin = (tlX.toInt() / tileSize.toInt()).coerceIn(1, tileRowCount)
      val xMax = ((brX.toInt() + tileSize.toInt()) / tileSize.toInt()).coerceIn(1, tileRowCount)
      val yMin = (tlY.toInt() / tileSize.toInt()).coerceIn(1, tileRowCount)
      val yMax = ((brY.toInt() + tileSize.toInt()) / tileSize.toInt()).coerceIn(1, tileRowCount)
      for (i in yMin..yMax) {
        val y = if (i < 10) "0$i" else "$i"
        for (j in xMin..xMax) {
          val x = if (j < 10) "0$j" else "$j"
          val tileStartX = (j-1)*tileSize
          val tileStartY = (i-1)*tileSize
          draw(mapTiles[tileZoom]!![y]!![x], tileStartX, tileStartY, tileSize, tileSize,
           0, 0, 256, 256,
           false, true)
        }
      }
    }

    shapeRenderer.projectionMatrix = camera.combined
    Gdx.gl.glEnable(GL20.GL_BLEND)

    drawGrid()
    drawCircles()

    val typeLocation = EnumMap<Archetype, MutableList<renderInfo>>(Archetype::class.java)
    for ((_, actor) in visualActors)
      typeLocation.compute(actor.Type) { _, v ->
        val list = v ?: ArrayList()
        val (centerX, centerY) = actor.location
        val direction = actor.rotation.y
        list.add(tuple4(actor, centerX, centerY, direction))
        list
      }

    paint(fontCamera.combined) {
      largeFont.draw(spriteBatch, "Time Elapsed: ${MatchElapsedMinutes}min \n" /* + "${ElapsedWarningDuration.toInt()} Next BlueZone: ${TotalWarningDuration.toInt()}"*/ ,10f, windowHeight - 10f)

      spriteBatch.draw(alive, windowWidth - 150f , windowHeight - 100f)
      largeFont.draw(spriteBatch, "$NumAlivePlayers" , windowWidth - 143f, windowHeight - 24f)

      if (NumAliveTeams > 1) {

        spriteBatch.draw(teamsalive, windowWidth - 280f , windowHeight - 100f)
        largeFont.draw(spriteBatch, "$NumAliveTeams" , windowWidth - 273f, windowHeight - 24f)

      }


      val time = (pinLocation.cpy().sub(selfX, selfY).len() / runSpeed).toInt()
      val (x, y) = pinLocation.mapToWindow()
      littleFont.draw(spriteBatch, "$time", x, windowHeight - y)
      safeZoneHint()
      drawPlayerInfos(typeLocation[Player]) //PlayerName etc

      var itemNameDrawBlacklist = arrayListOf(
        "AR.Stock",
        "S.Loops",
        "S.Comp",
        "U.Supp",
        "Choke",
      //"V.Grip",
        "A.Grip",
        "762",
      //"Ak",
        "U.Ext",
        "AR.Ext",
        "2x",
        "Vector",
        "Win94",
        "Bag2",
        "Grenade"
      )

      //Draw Airdrop Icon
      airDropLocation.values.forEach {
        val (x, y) = it
        val (sx, sy) = Vector2(x, y).mapToWindow()
        val syFix = windowHeight - sy
        spriteBatch.draw(airdropimage, sx - 16, syFix - 16)
      }

      //Draw Corpse Icon
      corpseLocation.values.forEach {
        //droppedItemLocation
        val (x,y) = it
        val (sx,sy) = Vector2(x,y).mapToWindow()
        val syFix = windowHeight - sy

        //println(sx + syFix)
        spriteBatch.draw(corpseboximage, sx - 16, syFix - 16)

      }

      //Draw Items
      droppedItemLocation.values.asSequence().filter { it.second.isNotEmpty() }
        .forEach {
          val (x, y) = it.first
          val items = it.second
          val finalColor = it.third

          val (sx, sy) = Vector2(x+16, y-16).mapToWindow()
          val syFix = windowHeight - sy

          //var yOffset = 2
          //println(items) //print items in console
          items.forEach {
          if (it !in itemNameDrawBlacklist) {
          if ( it in iconImages && sx > 0 &&
              sx < windowWidth && sy > 0 && sy < windowHeight ) {
              draw(iconImages[it], sx, syFix)
      //    draw(iconImages[it], sx, syFix)
      //    itemFont.draw(spriteBatch,"$items" , sx, syFix)

          } else {
            itemFont.draw(spriteBatch, it, sx, syFix)
              }
            }
          }
        }
    }

    val zoom = camera.zoom

    Gdx.gl.glEnable(GL20.GL_BLEND)
    //Redzone Color
    draw(Filled) {
      color = redZoneColor
      circle(RedZonePosition, RedZoneRadius, 100)

      color = visionColor
      circle(selfX, selfY, visionRadius, 100)

      color = pinColor
      circle(pinLocation, pinRadius * zoom, 10)

      //selfDir.angle)

      //draw self
      drawPlayer(LIME, tuple4(null, selfX, selfY, selfDir.angle()))

      drawItem()
      //drawItemNames()
      //drawCorpse()
      drawAirDrop(zoom)


      drawAPawn(typeLocation, selfX, selfY, zoom, currentTime)
    }

    drawAttackLine(currentTime)

    preSelfCoords.set(selfX, selfY)
    preDirection = selfDir

    Gdx.gl.glDisable(GL20.GL_BLEND)
  }

  private fun drawAttackLine(currentTime: Long) {
    while (attacks.isNotEmpty()) {
      val (A, B) = attacks.poll()
      attackLineStartTime.add(Triple(A, B, currentTime))
    }
    if (attackLineStartTime.isEmpty()) return
    draw(Line) {
      val iter = attackLineStartTime.iterator()
      while (iter.hasNext()) {
        val (A, B, st) = iter.next()
        if (A == selfID || B == selfID) {
          val enemyID = if (A == selfID) B else A
          val actorEnemyID = playerStateToActor[enemyID]
          if (actorEnemyID == null) {
            iter.remove()
            continue
          }
          val actorEnemy = actors[actorEnemyID]
          if (actorEnemy == null || currentTime - st > attackMeLineDuration) {
            iter.remove()
            continue
          }
          color = attackLineColor
          val (xA, yA) = selfCoords
          val (xB, yB) = actorEnemy.location
          line(xA, yA, xB, yB)
        } else {
          val actorAID = playerStateToActor[A]
          val actorBID = playerStateToActor[B]
          if (actorAID == null || actorBID == null) {
            iter.remove()
            continue
          }
          val actorA = actors[actorAID]
          val actorB = actors[actorBID]
          if (actorA == null || actorB == null || currentTime - st > attackLineDuration) {
            iter.remove()
            continue
          }
          color = attackLineColor
          val (xA, yA) = actorA.location
          val (xB, yB) = actorB.location
          line(xA, yA, xB, yB)
        }
      }
    }
  }

  private fun drawCircles() {
    Gdx.gl.glLineWidth(2f)
    draw(Line) {
      //vision circle

      color = safeZoneColor
      circle(PoisonGasWarningPosition, PoisonGasWarningRadius, 100)

      color = BLUE
      circle(SafetyZonePosition, SafetyZoneRadius, 100)

      if (PoisonGasWarningPosition.len() > 0) {
        color = safeDirectionColor
        line(selfCoords, PoisonGasWarningPosition)
      }
    }
    Gdx.gl.glLineWidth(1f)
  }

  private fun drawGrid() {
    draw(Filled) {
      color = BLACK
      //thin grid
      for (i in 0..7)
        for (j in 0..9) {
          rectLine(0f, i * unit + j * unit2, gridWidth, i * unit + j * unit2, 100f)
          rectLine(i * unit + j * unit2, 0f, i * unit + j * unit2, gridWidth, 100f)
        }
      color = GRAY
      //thick grid
      for (i in 0..7) {
        rectLine(0f, i * unit, gridWidth, i * unit, 500f)
        rectLine(i * unit, 0f, i * unit, gridWidth, 500f)
      }
    }
  }

  private fun ShapeRenderer.drawAPawn(typeLocation: EnumMap<Archetype, MutableList<renderInfo>>,
                                      selfX: Float, selfY: Float,
                                      zoom: Float,
                                      currentTime: Long) {
    for ((type, actorInfos) in typeLocation) {
      when (type) {
        TwoSeatBoat -> actorInfos?.forEach {
          drawVehicle(boatColor, it, vehicle2Width, vehicle6Width)
        }
        SixSeatBoat -> actorInfos?.forEach {
          drawVehicle(boatColor, it, vehicle4Width, vehicle6Width)
        }
        TwoSeatCar -> actorInfos?.forEach {
          drawVehicle(carColor, it, vehicle2Width, vehicle6Width)
        }
        TwoSeatBike -> actorInfos?.forEach {
          drawVehicle(boatColor, it, vehicle4Width, vehicle6Width)
        }
        ThreeSeatBike -> actorInfos?.forEach {
          drawVehicle(carColor, it, vehicle2Width, vehicle6Width)
        }
        FourSeatCar -> actorInfos?.forEach {
          drawVehicle(carColor, it, vehicle4Width, vehicle6Width)
        }
        SixSeatCar -> actorInfos?.forEach {
          drawVehicle(carColor, it, vehicle2Width, vehicle6Width)
        }
        Plane -> actorInfos?.forEach {
          drawPlayer(planeColor, it)
        }
        Player -> actorInfos?.forEach {
          drawPlayer(playerColor, it)

          aimAtMe(it, selfX, selfY, currentTime, zoom)
        }
        Parachute -> actorInfos?.forEach {
          drawPlayer(parachuteColor, it)
        }
        Grenade -> actorInfos?.forEach {
          drawPlayer(WHITE, it, false)
        }
        else -> {
          //            actorInfos?.forEach {
          //            bugln { "${it._1!!.archetype.pathName} ${it._1.location}" }
          //            drawPlayer(BLACK, it)
          //            }
        }
      }
    }
  }

  private fun ShapeRenderer.drawCorpse() {
    corpseLocation.values.forEach {

      val (x, y) = it
      val backgroundRadius = (corpseRadius + 50f)
      val radius = corpseRadius
      color = BLACK
      rect(x - backgroundRadius, y - backgroundRadius, backgroundRadius * 2, backgroundRadius * 2)
      color = corpseColor
      rect(x - radius, y - radius, radius * 2, radius * 2)
    }
  }

  private fun ShapeRenderer.drawAirDrop(zoom: Float) {
    airDropLocation.values.forEach {
      val (x, y) = it
      val backgroundRadius = (airDropRadius + 2000) * zoom
      val airDropRadius = airDropRadius * zoom
      color = BLACK
      rect(x - backgroundRadius, y - backgroundRadius, backgroundRadius * 2, backgroundRadius * 2)
      color = BLUE
      rect(x, y - airDropRadius, airDropRadius, airDropRadius * 2)
      color = RED
      rect(x - airDropRadius, y - airDropRadius, airDropRadius, airDropRadius * 2)
    }
  }

  private fun ShapeRenderer.drawItem() {
    droppedItemLocation.values.asSequence().filter { it.second.isNotEmpty() }
        .forEach {
          val (x, y) = it.first
          val items = it.second
          val finalColor = it.third

          if (finalColor.a == 0f)
            finalColor.set(
                when {
                  "m416" in items || "Ak" in items || "Scar" in items || "m16" in items || "DP" in items -> ARiflesColor
                  "Mini" in items || "Sks" in items -> sniperColor
                  "AR.Supp" in items || "S.Supp" in items -> suppressorColor
                  "armor3" in items || "helmet3" in items -> rareArmorColor
                  "4x" in items || "8x" in items -> rareScopeColor
                  "AR.ExtQ" in items || "S.ExtQ" in items -> rareAttachColor
                  "AR.Ext" in items || "S.Ext" in items || "AR.Comp" in items || "S.Comp" in items -> rareAttachColor
                  "A.Grip" in items || "V.Grip" in items -> rareAttachColor
                  "FirstAid" in items || "MedKit" in items -> healItemColor
                  "98k" in  items -> k98Color
                  "Ump" in items || "Pan" in items -> UmpandPanColor
                  else -> normalItemColor
                }
              )
/*
Adding a bunch of different shapes/colors for certain backgroundRadius
//legend

largeFont.draw(spriteBatch,   "Light Green Circle = m4/ak/scar/m16\n" +
                              "Light Blue Circle = Level 3 Helm/Chest\n " +
                              "Orange Square = Mini/SKS\n"+
                              "Brown Square = Kar98k\n"+
                              "Pink Triange = Pan/UMP\n"+
                              "White Triangle = AR/SR Suppressor\n"+
                              "Gold Triangle = 4x/8x Scope",  10f, windowHeight - 50f)

*/
          val ARifles = when (finalColor) {
          ARiflesColor -> false //m4/ak/scar/m16 LIME GREEN
          else -> false
          }

          val lvl3Armor = when (finalColor) {
          rareArmorColor -> false //lvl3armor LIGHT BLUE
          else -> false
          }

          val SniperGuns = when (finalColor) {
          sniperColor -> false // mini/sks ORANGERED
          else -> false
          }

          val k98Gun = when (finalColor) {
          k98Color -> true //kar98k
          else -> false
          }

          val UmpandPan = when (finalColor) {
            UmpandPanColor ->  false //ump and Pan
            else -> false
          }

          val asSUP = when (finalColor) {
            suppressorColor ->  false //AR and Sniper Suppressor
            else -> false
          }

          val Scopes = when (finalColor) {
            rareScopeColor ->  false //4x / 8x Scopes
            else -> false
          }

          val backgroundRadius = (itemRadius + 50f)
          val radius = itemRadius

          val triBackRadius = backgroundRadius * 0.866f //1.5f
          val triRadius = radius * 0.866f //1,5f

          when {
            ARifles -> {
              // Lime Green Circle
              color = finalColor
              circle(x, y, backgroundRadius * 0.9f, 10)
              color = finalColor
              circle(x, y, radius * 0.9f, 10)
            }

            lvl3Armor -> {
            //lvl3armor LIGHT BLUE Circle
              color = finalColor
              circle(x, y, backgroundRadius * 0.9f, 10)
              color = finalColor
              circle(x, y, radius * 0.9f, 10)
            }

            SniperGuns -> {
              // mini/sks ORANGERED Square
              color = finalColor
              rect(x - backgroundRadius, y - backgroundRadius,
                      backgroundRadius * 2, backgroundRadius * 2)
              color = finalColor
              rect(x - radius, y - radius, radius * 2, radius * 2)
            }

            k98Gun -> {
              //kar98k Brown Square
              color = RED
              //finalColor

              rect(x - backgroundRadius, y - backgroundRadius,
                      backgroundRadius * 2, backgroundRadius * 2)
              color = RED
              //finalColor
              rect(x - radius, y - radius, radius * 2, radius * 2)
            }

            UmpandPan -> {
              //ump and Pan Pink Triangle
              color = finalColor
              triangle(x, y - triBackRadius,
                      x - triBackRadius * 0.866f, y + triBackRadius * 0.5f,
                      x + triBackRadius * 0.866f, y + triBackRadius * 0.5f)
              color = finalColor
              triangle(x, y - triRadius,
                      x - triRadius * 0.866f, y + triRadius * 0.5f,
                      x + triRadius * 0.866f, y + triRadius * 0.5f)
            }

            asSUP -> {
              //AR and Sniper Suppressor White Triangle
              color = finalColor
              triangle(x, y - triBackRadius,
                      x - triBackRadius * 0.866f, y + triBackRadius * 0.5f,
                      x + triBackRadius * 0.866f, y + triBackRadius * 0.5f)

              color = finalColor
              triangle(x, y - triRadius,
                      x - triRadius * 0.866f, y + triRadius * 0.5f,
                      x + triRadius * 0.866f, y + triRadius * 0.5f)
                    }

                    Scopes -> {
                    //  4x / 8x Scopes Gold Triangle
                      color = finalColor
                      triangle(x, y - triBackRadius,
                              x - triBackRadius * 0.866f, y + triBackRadius * 0.5f,
                              x + triBackRadius * 0.866f, y + triBackRadius * 0.5f)

                      color = finalColor
                      triangle(x, y - triRadius,
                              x - triRadius * 0.866f, y + triRadius * 0.5f,
                              x + triRadius * 0.866f, y + triRadius * 0.5f)
                            }
    /*          normal -> {
              // Circle
              color = BLACK
              circle(x, y, backgroundRadius * 0.9f, 10)
              color = BLACK
              circle(x, y, radius * 0.9f, 10)
            }
*/
            else -> {
              // Do nothing
            }
          }
        }
  }

  fun drawPlayerInfos(players: MutableList<renderInfo>?) {
    players?.forEach {
      val (actor, x, y, _) = it
      actor!!
      val playerStateGUID = actorWithPlayerState[actor.netGUID] ?: return@forEach
      val name = playerNames[playerStateGUID] ?: return@forEach
      val teamNumber = teamNumbers[playerStateGUID] ?: 0
      val numKills = playerNumKills[playerStateGUID] ?: 0
      val (sx, sy) = Vector2(x, y).mapToWindow()
      query(name)
      if (completedPlayerInfo.containsKey(name)) {
        val info = completedPlayerInfo[name]!!
       val desc = "$name($numKills)\n${info.win}/${info.totalPlayed}\n${info.roundMostKill}-${info.killDeathRatio.d(2)}/${info.headshotKillRatio.d(2)}\n$teamNumber"
       // nameFont.draw(spriteBatch, desc, sx + 2, windowHeight - sy - 2)
      //} else nameFont.draw(spriteBatch, "$name($numKills)\n$teamNumber", sx + 2, windowHeight - sy - 2)
      } else nameFont.draw(spriteBatch, "$name \n Kills: $numKills \n ID:($teamNumber)", sx + 2, windowHeight - sy - 2)
    }
    val profileText = "${completedPlayerInfo.size}/${completedPlayerInfo.size + pendingPlayerInfo.size}"
    layout.setText(largeFont, profileText)
   // largeFont.draw(spriteBatch, profileText, windowWidth - layout.width, windowHeight - 10f) // the right side text
  }

  var lastPlayTime = System.currentTimeMillis()

  fun safeZoneHint() {
    if (PoisonGasWarningPosition.len() > 0) {
      val dir = PoisonGasWarningPosition.cpy().sub(selfCoords)
      val road = dir.len() - PoisonGasWarningRadius
      if (road > 0) {
        val runningTime = (road / runSpeed).toInt()
        val (x, y) = dir.nor().scl(road).add(selfCoords).mapToWindow()
        littleFont.draw(spriteBatch, "$runningTime", x, windowHeight - y)
        val remainingTime = (TotalWarningDuration - ElapsedWarningDuration).toInt()
        if (remainingTime == 60 && runningTime > remainingTime) {
          val currentTime = System.currentTimeMillis()
          if (currentTime - lastPlayTime > 10000) {
            lastPlayTime = currentTime
            alarmSound.play()
          }
        }
      }
    }
  }

  inline fun draw(type: ShapeType, draw: ShapeRenderer.() -> Unit) {
    shapeRenderer.apply {
      begin(type)
      draw()
      end()
    }
  }

  inline fun paint(matrix: Matrix4, paint: SpriteBatch.() -> Unit) {
    spriteBatch.apply {
      projectionMatrix = matrix
      begin()
      paint()
      end()
    }
  }

  fun ShapeRenderer.circle(loc: Vector2, radius: Float, segments: Int) {
    circle(loc.x, loc.y, radius, segments)
  }


  fun ShapeRenderer.aimAtMe(it: renderInfo, selfX: Float, selfY: Float, currentTime: Long, zoom: Float) {
    //draw aim line
    val (actor, x, y, dir) = it
    if (isTeamMate(actor)) return
    val actorID = actor!!.netGUID
    val dirVec = dirUnitVector.cpy().rotate(dir)
    val focus = Vector2(selfX - x, selfY - y)
    val distance = focus.len()
    var aim = false
    if (distance < aimLineRange && distance > aimCircleRadius) {
      val aimAngle = focus.angle(dirVec)
      if (aimAngle.absoluteValue < asin(aimCircleRadius / distance) * MathUtils.radiansToDegrees) { //aim
        aim = true
        aimStartTime.compute(actorID) { _, startTime ->
          if (startTime == null) currentTime
          else {
            if (currentTime - startTime > aimTimeThreshold) {
              color = aimLineColor
              rectLine(x, y, selfX, selfY, aimLineWidth * zoom)
            }
            startTime
          }
        }
      }
    }
    if (!aim)
      aimStartTime.remove(actorID)
  }

  fun ShapeRenderer.drawPlayer(pColor: Color?, actorInfo: renderInfo, drawSight: Boolean = true) {
    val zoom = camera.zoom
    val backgroundRadius = (playerRadius + 2000f) * zoom
    val playerRadius = playerRadius * zoom
    val directionRadius = directionRadius * zoom

    color = BLACK
    val (actor, x, y, dir) = actorInfo
    circle(x, y, backgroundRadius, 10)

    color = if (isTeamMate(actor))
      teamColor
    else pColor

    circle(x, y, playerRadius, 10)

    if (drawSight) {
      color = sightColor
      arc(x, y, directionRadius, dir - fov / 3, /*fov*/60f, 3)
    }
  }

  private fun isTeamMate(actor: Actor?): Boolean {
    if (actor != null) {
      val playerStateGUID = actorWithPlayerState[actor.netGUID]
      if (playerStateGUID != null) {
        val name = playerNames[playerStateGUID] ?: return false
        if (name in team)
          return true
      }
    }
    return false
  }

  fun ShapeRenderer.drawVehicle(_color: Color, actorInfo: renderInfo,
                                width: Float, height: Float) {

    val (actor, x, y, dir) = actorInfo
    val v_x = actor!!.velocity.x
    val v_y = actor.velocity.y

    val dirVector = dirUnitVector.cpy().rotate(dir).scl(height / 2)
    color = BLACK
    val backVector = dirVector.cpy().nor().scl(height / 2 + 200f)
    rectLine(x - backVector.x, y - backVector.y,
             x + backVector.x, y + backVector.y, width + 400f)
    color = _color
    rectLine(x - dirVector.x, y - dirVector.y,
             x + dirVector.x, y + dirVector.y, width)

    if (actor.beAttached || v_x * v_x + v_y * v_y > 40) {
      color = playerColor
      circle(x, y, playerRadius * camera.zoom, 10)
    }
  }

  override fun resize(width: Int, height: Int) {
    windowWidth = width.toFloat()
    windowHeight = height.toFloat()
    camera.setToOrtho(true, windowWidth * windowToMapUnit, windowHeight * windowToMapUnit)
    fontCamera.setToOrtho(false, windowWidth, windowHeight)
  }


  override fun pause() {
  }

  override fun resume() {
  }

  override fun dispose() {
    deregister(this)
    alarmSound.dispose()
    nameFont.dispose()
    largeFont.dispose()
    littleFont.dispose()
    airdropimage.dispose()
    corpseboximage.dispose()
    alive.dispose()
    teamsalive.dispose()
    selficon.dispose()
    // mapErangel.dispose()
    // mapMiramar.dispose()
    for ((key, image) in iconImages) {
      image.dispose()
    }

    var cur = 0
    tileZooms.forEach{
        for (i in 1..tileRowCounts[cur]) {
            val y = if (i < 10) "0$i" else "$i"
            for (j in 1..tileRowCounts[cur]) {
                val x = if (j < 10) "0$j" else "$j"
                mapErangelTiles[it]!![y]!![x]!!.dispose()
                mapMiramarTiles[it]!![y]!![x]!!.dispose()
                mapTiles[it]!![y]!![x]!!.dispose()
            }
        }
        cur++
    }
    spriteBatch.dispose()
    shapeRenderer.dispose()
  }
}
