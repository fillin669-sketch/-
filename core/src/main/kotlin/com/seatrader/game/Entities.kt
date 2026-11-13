package com.seatrader.game

import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.math.Vector3
import kotlin.math.PI
import kotlin.random.Random

internal fun rand(a: Float, b: Float) = a + Random.nextFloat() * (b - a)
internal fun clamp(v: Float, a: Float, b: Float) = if (v < a) a else if (v > b) b else v
internal fun wrapAngle(x: Float): Float {
    var a = x
    while (a > PI) a -= (2 * PI).toFloat()
    while (a < -PI) a += (2 * PI).toFloat()
    return a
}

enum class Faction { PLAYER, PIRATE, NEUTRAL }
enum class AiState { WANDER, ATTACK, FOLLOW }
enum class Ammo { NORMAL, FIRE, CHAIN, MORTAR, ROCKET }

class Ship(
    val id: Int, val name: String, var stats: Stats, val faction: Faction, val look: Look,
    var x: Float, var z: Float, var h: Float, val inst: ModelInstance, val boss: Boolean,
) {
    var type = ""
    var coinsMin = 0; var coinsMax = 0; var gems = 0f
    var desired = h
    var throttle = 0f
    // Физика
    var vx = 0f; var vz = 0f
    var angVel = 0f
    var roll = 0f; var rollVel = 0f
    var pitch = 0f
    var y = 0f
    var hp = stats.hp.toFloat()
    var maxHp = stats.hp.toFloat()
    val len = look.len
    val width = look.width
    val radius = look.len * 0.36f
    val deckTop = Models.deckTop(look)
    val mastH = look.len * 0.62f + 3
    val reload = floatArrayOf(rand(0.3f, 1.2f), rand(0.3f, 1.2f)) // [левый, правый]
    var sinkT = 0f
    var boostT = 0f
    var boostCd = 0f
    var lastHit = -99f
    var lastFire = -99f
    var wakeT = 0f
    var smokeT = 0f
    var sprayT = 0f
    var burnT = 0f
    var burnDps = 0f
    var slowT = 0f
    var repairT = 0f
    val ramT = HashMap<Int, Float>()
    var ai = AiState.WANDER
    var wx = x; var wz = z; var aiT = 0f
    var side = if (Random.nextBoolean()) -1 else 1
    var safe = false
    var inPort = false
    var ally = false
    var allyIdx = 0
    var mercId = ""
    var target: Ship? = null
    /** Номер капитана с наградой за голову (-1 — обычный). */
    var bounty = -1
    /** Навигация ботов: сколько стоим на месте и куда выбираемся. */
    var stuckT = 0f
    /** Союзник догоняет строй: немного быстрее обычного. */
    var catchUp = false
    /** Граница погони: враг не уходит от точки появления дальше [leash] м. */
    var homeX = x; var homeZ = z
    var leash = 300f
    var giveUpT = 0f
    /** Спустил флаг (сдался) и уже проверял, сдаваться ли. */
    var surrendered = false
    /** Мирное судно, на которое напал игрок-пират: защищается или удирает. */
    var provoked = false
    var surrenderChecked = false
    var escapeT = 0f
    var escA = 0f
    var hunter = false
    var ghost = false
    var trait = ""
    var leader: Ship? = null
    /** Пробоины: x — вдоль корпуса, y — высота, z — поперёк. */
    val holes = ArrayList<Vector3>()
    val mastHp = FloatArray(look.masts) { stats.hp * 0.18f }
    var brokenMasts = 0
    val sails: List<Node> = (0 until Models.sailCount(look)).mapNotNull { inst.getNode("sail$it") }
    val sinking get() = sinkT > 0
    /** Скорость вдоль курса. */
    val speed get() = vx * kotlin.math.sin(h) - vz * kotlin.math.cos(h)
}

class Ball(
    val owner: Ship?, val faction: Faction, var x: Float, var y: Float, var z: Float,
    var vx: Float, var vy: Float, var vz: Float, val dmg: Float, val ammo: Ammo, var delay: Float,
) {
    var age = 0f
    var bounced = false
    var trailT = 0f
    var fired = false
}

class Island(val x: Float, val z: Float, val r: Float, val port: Int, val inst: ModelInstance, val foam: ModelInstance, val lights: List<Vector3>, val glows: List<Vector3> = emptyList())
class Port(val def: PortDef, val i: Int, val rx: Float, val rz: Float, val ring: ModelInstance)
class Loot(val gem: Boolean, val value: Int, var x: Float, var z: Float, val inst: ModelInstance, var t: Float) { var age = 0f }
class FloatText(val text: String, val x: Float, var y: Float, val color: com.badlogic.gdx.graphics.Color, val font: com.badlogic.gdx.graphics.g2d.BitmapFont) { var age = 0f }
class Treasure(val island: Island, val x: Float, val z: Float, val inst: ModelInstance)
class Gull(var cx: Float, var cz: Float, val r: Float, var a: Float, val h: Float, val speed: Float, val phase: Float)

/** Морской змей: голова и цепочка сегментов, ныряет и кусает. */
class Serpent(var x: Float, var z: Float, val head: ModelInstance, val body: List<ModelInstance>) {
    var h = 0f
    var hp = 900f
    val maxHp = 900f
    var diving = false
    var stateT = 12f
    var biteCd = 0f
    var depth = 0f
    var deadT = 0f
    var lastHit = -99f
    var homeX = x; var homeZ = z
    var returning = false
    val trail = ArrayList<Vector3>()
    val segPos = Array(body.size) { Vector3() }
    val dead get() = deadT > 0
}
