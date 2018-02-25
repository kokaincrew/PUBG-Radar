package pubg.radar.ui

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Buttons.*
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Color.*
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.DEFAULT_CHARS
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import pubg.radar.*
import pubg.radar.deserializer.channel.ActorChannel.Companion.actors
import pubg.radar.deserializer.channel.ActorChannel.Companion.airDropLocation
import pubg.radar.deserializer.channel.ActorChannel.Companion.corpseLocation
import pubg.radar.deserializer.channel.ActorChannel.Companion.droppedItemLocation
import pubg.radar.deserializer.channel.ActorChannel.Companion.visualActors
import pubg.radar.http.PlayerProfile.Companion.completedPlayerInfo
import pubg.radar.http.PlayerProfile.Companion.pendingPlayerInfo
import pubg.radar.http.PlayerProfile.Companion.query
import pubg.radar.sniffer.Sniffer.Companion.preDirection
import pubg.radar.sniffer.Sniffer.Companion.preSelfCoords
import pubg.radar.sniffer.Sniffer.Companion.selfCoords
import pubg.radar.sniffer.Sniffer.Companion.sniffOption
import pubg.radar.sniffer.Sniffer.Companion.targetAddr
import pubg.radar.struct.Actor
import pubg.radar.struct.Archetype
import pubg.radar.struct.Archetype.*
import pubg.radar.struct.NetworkGUID
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
import pubg.radar.util.tuple4
import wumo.pubg.struct.cmd.TeamCMD.team
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.absoluteValue
import kotlin.math.asin
import kotlin.math.pow

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
        config.setTitle("[${targetAddr.hostAddress} ${sniffOption.name}] - PUBG Radar")
        config.useOpenGL3(false, 3, 2)
        config.setWindowedMode(800, 800)
        config.setResizable(true)
        config.setBackBufferConfig(8, 8, 8, 8, 16, 0, 8)
        Lwjgl3Application(this, config)
    }

    private lateinit var spriteBatch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var mapErangelTiles: MutableMap<String, MutableMap<String, MutableMap<String, Texture>>>
    private lateinit var mapMiramarTiles: MutableMap<String, MutableMap<String, MutableMap<String, Texture>>>
    private lateinit var mapTiles: MutableMap<String, MutableMap<String, MutableMap<String, Texture>>>
    private lateinit var iconImages: Icons
    private lateinit var alive: Texture
    private lateinit var corpseboximage: Texture
    private lateinit var airdropimage: Texture
    private lateinit var teamsalive: Texture
    private lateinit var largeFont: BitmapFont
    private lateinit var littleFont: BitmapFont
    private lateinit var nameFont: BitmapFont
    private lateinit var itemFont: BitmapFont
    private lateinit var compaseFont: BitmapFont
    private lateinit var compaseFontShadow: BitmapFont
    private lateinit var fontCamera: OrthographicCamera
    private lateinit var itemCamera: OrthographicCamera
    private lateinit var camera: OrthographicCamera
    private lateinit var alarmSound: Sound



    private val tileZooms = listOf("256", "512", "1024", "2048"/*, "4096"*/)
    private val tileRowCounts = listOf(1, 2, 4, 8, 16/*, 32*/)
    private val tileSizes = listOf(819200f, 409600f, 204800f, 102400f/*, 51200f*/)

    private val layout = GlyphLayout()
    private var windowWidth = initialWindowWidth
    private var windowHeight = initialWindowWidth

    private val aimStartTime = HashMap<NetworkGUID, Long>()
    private val attackLineStartTime = LinkedList<Triple<NetworkGUID, NetworkGUID, Long>>()
    private val pinLocation = Vector2()

    private var dragging = false
    private var prevScreenX = -1f
    private var prevScreenY = -1f
    private var screenOffsetX = 0f
    private var screenOffsetY = 0f

    private fun Vector2.windowToMap() =
            Vector2(selfCoords.x + (x - windowWidth / 2.0f) * camera.zoom * windowToMapUnit + screenOffsetX,
                    selfCoords.y + (y - windowHeight / 2.0f) * camera.zoom * windowToMapUnit + screenOffsetY)

    private fun Vector2.mapToWindow() =
            Vector2((x - selfCoords.x - screenOffsetX) / (camera.zoom * windowToMapUnit) + windowWidth / 2.0f,
                    (y - selfCoords.y - screenOffsetY) / (camera.zoom * windowToMapUnit) + windowHeight / 2.0f)

    override fun scrolled(amount: Int): Boolean {
        camera.zoom *= 1.1f.pow(amount)
        return true
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        when (button) {
            RIGHT -> {
                pinLocation.set(pinLocation.set(screenX.toFloat(), screenY.toFloat()).windowToMap())
                camera.update()
                println(pinLocation)
                return true
            }
            LEFT -> {
                dragging = true
                prevScreenX = screenX.toFloat()
                prevScreenY = screenY.toFloat()
                return true
            }
            MIDDLE -> {
                screenOffsetX = 0f
                screenOffsetY = 0f
            }
        }
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (!dragging) return false
        with(camera) {
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
        alive = Texture(Gdx.files.internal("images/alive.png"))
        teamsalive = Texture(Gdx.files.internal("images/teams.png"))
        itemCamera = OrthographicCamera(initialWindowWidth, initialWindowWidth)
        fontCamera = OrthographicCamera(initialWindowWidth, initialWindowWidth)
        alarmSound = Gdx.audio.newSound(Gdx.files.internal("Alarm.wav"))
        corpseboximage = Texture(Gdx.files.internal("images/box.png"))
        airdropimage = Texture(Gdx.files.internal("images/airdrop.png"))
        iconImages = Icons(Texture(Gdx.files.internal("item-sprites.png")), 64)
        mapErangelTiles = mutableMapOf()
        mapMiramarTiles = mutableMapOf()
        var cur = 0
        tileZooms.forEach {
            mapErangelTiles[it] = mutableMapOf()
            mapMiramarTiles[it] = mutableMapOf()
            for (i in 1..tileRowCounts[cur]) {
                val y = if (i < 10) "0$i" else "$i"
                mapErangelTiles[it]?.set(y, mutableMapOf())
                mapMiramarTiles[it]?.set(y, mutableMapOf())
                for (j in 1..tileRowCounts[cur]) {
                    val x = if (j < 10) "0$j" else "$j"
                    mapErangelTiles[it]!![y]?.set(x, Texture(Gdx.files.internal("tiles/Erangel/$it/${it}_${y}_$x-min.png")))
                    mapMiramarTiles[it]!![y]?.set(x, Texture(Gdx.files.internal("tiles/Miramar/$it/${it}_${y}_$x-min.png")))
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
        param.size = 15
        nameFont = generator.generateFont(param)
        param.color = BLACK
        param.size = 10
        itemFont = generator.generateFont(param)

        param.color = compaseColor
        param.size = 14
        compaseFont = generator.generateFont(param)
        param.color = Color(0f, 0f, 0f, 0.5f)
        compaseFontShadow = generator.generateFont(param)

        generator.dispose()
    }

    private val dirUnitVector = Vector2(1f, 0f)
    override fun render() {
        Gdx.gl.glClearColor(0.417f, 0.417f, 0.417f, 0f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        if (gameStarted)
            mapTiles = if (isErangel) mapErangelTiles else mapMiramarTiles
        else return
        val currentTime = System.currentTimeMillis()
        // selfCoords = Vector2(409600f, 409600f)
        val (selfX, selfY) = selfCoords
        val selfDir = Vector2(selfX, selfY).sub(preSelfCoords)
        if (selfDir.len() < 1e-8)
            selfDir.set(preDirection)

        //move camera
        // camera.position.set(selfX, selfY, 0f)
        camera.position.set(selfX + screenOffsetX, selfY + screenOffsetY, 0f)
        camera.update()
        val cameraTileScale = Math.max(windowWidth, windowHeight) / camera.zoom
        val useScale: Int
        useScale = when {
        //   cameraTileScale > 4096 -> 4
            cameraTileScale > 2048 -> 3
            cameraTileScale > 1024 -> 2
            cameraTileScale > 512 -> 1
            cameraTileScale > 256 -> 0
            else -> 0
        }
        val (tlX, tlY) = Vector2(0f, 0f).windowToMap()
        val (brX, brY) = Vector2(windowWidth, windowHeight).windowToMap()
        val tileZoom = tileZooms[useScale]
        val tileRowCount = tileRowCounts[useScale]
        val tileSize = tileSizes[useScale]

        val xMin = (tlX.toInt() / tileSize.toInt()).coerceIn(1, tileRowCount)
        val xMax = ((brX.toInt() + tileSize.toInt()) / tileSize.toInt()).coerceIn(1, tileRowCount)
        val yMin = (tlY.toInt() / tileSize.toInt()).coerceIn(1, tileRowCount)
        val yMax = ((brY.toInt() + tileSize.toInt()) / tileSize.toInt()).coerceIn(1, tileRowCount)
        paint(camera.combined) {
            for (i in yMin..yMax) {
                val y = if (i < 10) "0$i" else "$i"
                for (j in xMin..xMax) {
                    val x = if (j < 10) "0$j" else "$j"
                    val tileStartX = (j - 1) * tileSize
                    val tileStartY = (i - 1) * tileSize
                    draw(mapTiles[tileZoom]!![y]!![x], tileStartX, tileStartY, tileSize, tileSize,
                            0, 0, 256, 256,
                            false, true)
                }
            }
        }

        shapeRenderer.projectionMatrix = camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)


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
            littleFont.draw(spriteBatch, "Time Elapsed: ${MatchElapsedMinutes}min \n" /* + "${ElapsedWarningDuration.toInt()} Next BlueZone: ${TotalWarningDuration.toInt()}"*/, 10f, windowHeight - 10f)

            spriteBatch.draw(alive, windowWidth - 150f, windowHeight - 100f)
            largeFont.draw(spriteBatch, "$NumAlivePlayers", windowWidth - 143f, windowHeight - 24f)

            if (NumAliveTeams > 1) {

                spriteBatch.draw(teamsalive, windowWidth - 280f, windowHeight - 100f)
                largeFont.draw(spriteBatch, "$NumAliveTeams", windowWidth - 273f, windowHeight - 24f)

            }

            val time = (pinLocation.cpy().sub(selfX, selfY).len() / runSpeed).toInt()
            val (x, y) = pinLocation.mapToWindow()
            littleFont.draw(spriteBatch, "$time", x, windowHeight - y)
            safeZoneHint()
            drawPlayerInfos(typeLocation[Player])
            for(i in -1..1) {
                for(j in -1..1) {
                    compaseFontShadow.draw(spriteBatch, "0"  , windowWidth/2 + i, windowHeight/2 + 150 + j)        // N
                    compaseFontShadow.draw(spriteBatch, "45" , windowWidth/2 + 150 + i, windowHeight/2 + 150 + j)  // NE
                    compaseFontShadow.draw(spriteBatch, "90" , windowWidth/2 + 150 + i, windowHeight/2 + j)        // E
                    compaseFontShadow.draw(spriteBatch, "135", windowWidth/2 + 150 + i, windowHeight/2 - 150 + j)  // SE
                    compaseFontShadow.draw(spriteBatch, "180", windowWidth/2 + i, windowHeight/2 - 150 + j)        // S
                    compaseFontShadow.draw(spriteBatch, "225", windowWidth/2 - 150 + i, windowHeight/2 - 150+ j)   // SW
                    compaseFontShadow.draw(spriteBatch, "270", windowWidth/2 - 150 + i, windowHeight/2 + j)        // W
                    compaseFontShadow.draw(spriteBatch, "315", windowWidth/2 - 150 + i, windowHeight/2 + 150+ j)   // NW
                }
            }
            compaseFont.draw(spriteBatch, "0"  , windowWidth/2, windowHeight/2 + 150)        // N
            compaseFont.draw(spriteBatch, "45" , windowWidth/2 + 150, windowHeight/2 + 150)  // NE
            compaseFont.draw(spriteBatch, "90" , windowWidth/2 + 150, windowHeight/2)        // E
            compaseFont.draw(spriteBatch, "135", windowWidth/2 + 150, windowHeight/2 - 150)  // SE
            compaseFont.draw(spriteBatch, "180", windowWidth/2, windowHeight/2 - 150)        // S
            compaseFont.draw(spriteBatch, "225", windowWidth/2 - 150, windowHeight/2 - 150)  // SW
            compaseFont.draw(spriteBatch, "270", windowWidth/2 - 150, windowHeight/2)        // W
            compaseFont.draw(spriteBatch, "315", windowWidth/2 - 150, windowHeight/2 + 150)  // NW

        }

        val itemNameDrawBlacklist = arrayListOf(
                "AR.Stock",
                "S.Loops",
                "FlashHider",
                "Choke",
                "V.Grip",
                //  "556",
                "762",
                "Ak",
                "Sks",
                "Grenade"
        )
        val iconScale = 1.5f / camera.zoom
        paint(itemCamera.combined) {
            droppedItemLocation.values.asSequence().filter { it.second.isNotEmpty() }
                    .forEach {
                        val (x, y) = it.first
                        val items = it.second
                        val (sx, sy) = Vector2(x+16, y-16).mapToWindow()

                        val syFix = windowHeight - sy


                        // println(items)
                        items.forEach {
                            if (it !in itemNameDrawBlacklist) {
                                if (
                                        iconScale > 8 &&
                                        sx > 0 && sx < windowWidth &&
                                        syFix > 0 && syFix < windowHeight
                                ) {
                                    iconImages.setIcon(it)
                                    draw(
                                            iconImages.icon,
                                            sx - iconScale / 2, syFix + iconScale / 2, iconScale, iconScale
                                    )
                                } else {
                                    // itemFont.draw(spriteBatch, it, sx, windowHeight - sy - yOffset)
                                }
                                // yOffset = yOffset + 2
                            }
                        }
                    }
            //Draw Corpse Icon
            corpseLocation.values.forEach {
                //droppedItemLocation
                val (x,y) = it
                val (sx, sy) = Vector2(x+16, y-16).mapToWindow()
                val syFix = windowHeight - sy
                val iconScale = 2f / camera.zoom

                //println(sx + syFix)
                //spriteBatch.draw(corpseboximage, sx - 16, syFix - 16)

                spriteBatch.draw(corpseboximage, sx - iconScale / 2, syFix + iconScale / 2, iconScale, -iconScale,
                        0, 0, 32, 32,
                        false, true)
            }
            //Draw Airdrop Icon
            airDropLocation.values.forEach {
                //droppedItemLocation
                val (x,y) = it
                val (sx,sy) = Vector2(x,y).mapToWindow()
                val syFix = windowHeight - sy
                val iconScale = 2f / camera.zoom


                spriteBatch.draw(airdropimage, sx - iconScale / 2, syFix + iconScale / 2, iconScale, -iconScale,
                        0, 0, 32, 32,
                        false, true)
            }
        }

        val zoom = camera.zoom

        Gdx.gl.glEnable(GL20.GL_BLEND)
        draw(Filled) {
            color = redZoneColor
            circle(RedZonePosition, RedZoneRadius, 100)

            color = visionColor
            circle(selfX, selfY, visionRadius, 100)

            color = pinColor
            circle(pinLocation, pinRadius * zoom, 10)
            //draw self
            drawPlayer(LIME, tuple4(null, selfX, selfY, selfDir.angle()))
            // drawItem()
            //drawAirDrop(zoom)

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
                ThreeSeatCar -> actorInfos?.forEach {
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
                    return
                }
            }
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

    private fun drawPlayerInfos(players: MutableList<renderInfo>?) {
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
                nameFont.draw(spriteBatch, desc, sx + 2, windowHeight - sy - 2)
            } else nameFont.draw(spriteBatch, "$name($numKills)\n$teamNumber", sx + 2, windowHeight - sy - 2)
        }
        val profileText = "${completedPlayerInfo.size}/${completedPlayerInfo.size + pendingPlayerInfo.size}"
        layout.setText(largeFont, profileText)
    }

    private var lastPlayTime = System.currentTimeMillis()
    private fun safeZoneHint() {
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

    private inline fun draw(type: ShapeType, draw: ShapeRenderer.() -> Unit) {
        shapeRenderer.apply {
            begin(type)
            draw()
            end()
        }
    }

    private inline fun paint(matrix: Matrix4, paint: SpriteBatch.() -> Unit) {
        spriteBatch.apply {
            projectionMatrix = matrix
            begin()
            paint()
            end()
        }
    }

    private fun ShapeRenderer.circle(loc: Vector2, radius: Float, segments: Int) {
        circle(loc.x, loc.y, radius, segments)
    }

    private fun ShapeRenderer.aimAtMe(it: renderInfo, selfX: Float, selfY: Float, currentTime: Long, zoom: Float) {
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

    private fun ShapeRenderer.drawPlayer(pColor: Color?, actorInfo: renderInfo, drawSight: Boolean = true) {
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
            arc(x, y, directionRadius, dir - fov / 2, fov, 10)
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

    private fun ShapeRenderer.drawVehicle(_color: Color, actorInfo: renderInfo,
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
        itemCamera.setToOrtho(false, windowWidth, windowHeight)
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
        compaseFont.dispose()
        compaseFontShadow.dispose()
        alive.dispose()
        corpseboximage.dispose()
        airdropimage.dispose()
        iconImages.iconSheet.dispose()

        var cur = 0
        tileZooms.forEach {
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
