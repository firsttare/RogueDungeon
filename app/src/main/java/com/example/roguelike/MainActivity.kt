package com.example.roguelike

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.graphics.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.random.Random

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(GameView())
    }

    inner class GameView : View(this@MainActivity) {
        private val rng = Random(System.currentTimeMillis())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val prefs = getSharedPreferences("save", Context.MODE_PRIVATE)
        private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)

        private var mode = 0
        private var map = Array(25) { CharArray(17) { '#' } }
        private var px = 8
        private var py = 12
        private var stairX = 1
        private var stairY = 1

        private var hp = 30
        private var maxHp = 30
        private var hunger = 100
        private var level = 1
        private var exp = 0
        private var gold = 0
        private var floorNumber = 1
        private var score = 0

        private var sword = 4
        private var swordPlus = 0
        private var shieldPlus = 0
        private var arrows = 8
        private var food = 3
        private var potions = 2
        private var hasStaff = false

        private var gameOver = false
        private var message = ""

        private val traps = mutableSetOf<Pair<Int, Int>>()
        private val items = mutableListOf<Item>()
        private val enemies = mutableListOf<Enemy>()

        data class Enemy(
            var x: Int,
            var y: Int,
            var hp: Int,
            var type: Int,
            var sleep: Int = 0
        )

        data class Item(
            var x: Int,
            var y: Int,
            var type: Int
        )

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.rgb(7, 7, 7))
            if (mode == 0) {
                drawTitle(canvas)
                return
            }
            if (mode == 2) {
                drawShop(canvas)
                return
            }

            val cell = minOf(width / 17f, height * 0.67f / 25f)
            val left = (width - 17 * cell) / 2f
            val top = 38f

            paint.textAlign = Paint.Align.CENTER

            for (y in 0..24) {
                for (x in 0..16) {
                    paint.color =
                        if (map[y][x] == '#') Color.rgb(35, 35, 40)
                        else Color.rgb(73, 68, 58)
                    canvas.drawRect(
                        left + x * cell,
                        top + y * cell,
                        left + (x + 1) * cell - 1,
                        top + (y + 1) * cell - 1,
                        paint
                    )
                }
            }

            paint.textSize = cell * 0.62f

            paint.color = Color.YELLOW
            canvas.drawText(">", left + stairX * cell + cell / 2, top + stairY * cell + cell * .72f, paint)

            for (trap in traps) {
                paint.color = Color.DKGRAY
                canvas.drawText("^", left + trap.first * cell + cell / 2, top + trap.second * cell + cell * .72f, paint)
            }

            for (item in items) {
                paint.color = when (item.type) {
                    0 -> Color.WHITE
                    1 -> Color.CYAN
                    2 -> Color.GREEN
                    3 -> Color.YELLOW
                    else -> Color.LTGRAY
                }
                canvas.drawText(itemName(item.type), left + item.x * cell + cell / 2, top + item.y * cell + cell * .72f, paint)
            }

            for (enemy in enemies) {
                paint.color = when (enemy.type) {
                    0 -> Color.RED
                    1 -> Color.rgb(220, 120, 40)
                    2 -> Color.BLUE
                    else -> Color.GREEN
                }
                canvas.drawText(enemyName(enemy.type), left + enemy.x * cell + cell / 2, top + enemy.y * cell + cell * .72f, paint)
            }

            paint.color = Color.CYAN
            canvas.drawText("@", left + px * cell + cell / 2, top + py * cell + cell * .72f, paint)

            paint.textSize = 15f
            paint.color = Color.WHITE
            canvas.drawText(
                "HP $hp/$maxHp  満腹 $hunger  Lv$level  経験 $exp  金$gold",
                width / 2f, 25f, paint
            )

            paint.textSize = 13f
            canvas.drawText(
                "剣 ${sword + swordPlus} 盾 $shieldPlus  矢$arrows  食料$food  薬$potions",
                width / 2f, top + 25 * cell + 17, paint
            )

            paint.color = Color.LTGRAY
            canvas.drawText(message, width / 2f, top + 25 * cell + 36, paint)

            button(canvas, width * .16f, height * .82f, "←")
            button(canvas, width * .50f, height * .77f, "↑")
            button(canvas, width * .84f, height * .82f, "→")
            button(canvas, width * .50f, height * .90f, "↓")
            button(canvas, width * .16f, height * .93f, "食")
            button(canvas, width * .37f, height * .93f, "薬")
            button(canvas, width * .63f, height * .93f, "杖")
            button(canvas, width * .84f, height * .93f, "弓")

            if (gameOver) {
                paint.color = Color.argb(225, 0, 0, 0)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.color = Color.WHITE
                paint.textSize = 34f
                canvas.drawText("力尽きた", width / 2f, height * .42f, paint)
                paint.textSize = 17f
                canvas.drawText("タップでタイトルへ", width / 2f, height * .50f, paint)
            }
        }

        private fun drawTitle(canvas: Canvas) {
            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.WHITE
            paint.textSize = 38f
            canvas.drawText("迷宮の旅人", width / 2f, height * .28f, paint)

            paint.textSize = 18f
            paint.color = Color.LTGRAY
            canvas.drawText("ターン制ローグライク DX", width / 2f, height * .34f, paint)

            paint.textSize = 20f
            paint.color = Color.YELLOW
            canvas.drawText("画面をタップして開始", width / 2f, height * .52f, paint)

            paint.textSize = 14f
            paint.color = Color.GRAY
            canvas.drawText(
                "ランダム迷宮 / 装備 / 店 / 魔法 / 罠 / セーブ",
                width / 2f, height * .60f, paint
            )

            val best = prefs.getInt("best", 0)
            canvas.drawText("最高スコア $best", width / 2f, height * .66f, paint)
        }

        private fun drawShop(canvas: Canvas) {
            canvas.drawColor(Color.rgb(18, 12, 8))
            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.YELLOW
            paint.textSize = 32f
            canvas.drawText("商店", width / 2f, 100f, paint)

            paint.color = Color.WHITE
            paint.textSize = 19f
            canvas.drawText("所持金: $gold G", width / 2f, 145f, paint)

            val lines = listOf(
                "回復薬 10G",
                "食料 8G",
                "矢10本 12G",
                "剣強化 40G",
                "盾強化 40G",
                "画面タップで戻る"
            )
            lines.forEachIndexed { index, text ->
                canvas.drawText(text, width / 2f, 210f + index * 55, paint)
            }
        }

        private fun button(canvas: Canvas, x: Float, y: Float, text: String) {
            paint.color = Color.rgb(45, 45, 50)
            canvas.drawCircle(x, y, 32f, paint)
            paint.color = Color.WHITE
            paint.textSize = 22f
            canvas.drawText(text, x, y + 8, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_UP) return true

            if (mode == 0) {
                newGame()
                return true
            }

            if (gameOver) {
                mode = 0
                invalidate()
                return true
            }

            if (mode == 2) {
                mode = 1
                invalidate()
                return true
            }

            val x = event.x
            val y = event.y
            val w = width.toFloat()
            val h = height.toFloat()

            when {
                x < w * .30f && y > h * .76f -> move(-1, 0)
                x > w * .70f && y > h * .76f -> move(1, 0)
                x in w * .35f..w * .65f && y > h * .72f && y < h * .86f -> move(0, -1)
                x in w * .35f..w * .65f && y > h * .86f -> move(0, 1)
                x < w * .28f && y > h * .89f -> eat()
                x in w * .28f..w * .49f && y > h * .89f -> drink()
                x in w * .49f..w * .74f && y > h * .89f -> cast()
                x > w * .74f && y > h * .89f -> shoot()
            }

            return true
        }

        private fun newGame() {
            mode = 1
            floorNumber = 1
            level = 1
            exp = 0
            gold = 0
            hp = 30
            maxHp = 30
            hunger = 100
            sword = 4
            swordPlus = 0
            shieldPlus = 0
            arrows = 8
            food = 3
            potions = 2
            hasStaff = false
            score = 0
            gameOver = false
            newFloor()
            message = "第1階。階段を探そう"
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        }

        private fun newFloor() {
            map = Array(25) { CharArray(17) { '#' } }

            repeat(13) {
                val roomWidth = rng.nextInt(3, 8)
                val roomHeight = rng.nextInt(3, 7)
                val roomX = rng.nextInt(1, 17 - roomWidth)
                val roomY = rng.nextInt(1, 25 - roomHeight)

                for (y in roomY until roomY + roomHeight) {
                    for (x in roomX until roomX + roomWidth) {
                        map[y][x] = '.'
                    }
                }
            }

            var cx = 8
            var cy = 12
            repeat(120) {
                map[cy][cx] = '.'
                when (rng.nextInt(4)) {
                    0 -> cx = (cx + 1).coerceAtMost(15)
                    1 -> cx = (cx - 1).coerceAtLeast(1)
                    2 -> cy = (cy + 1).coerceAtMost(23)
                    else -> cy = (cy - 1).coerceAtLeast(1)
                }
            }

            val floorTiles = mutableListOf<Pair<Int, Int>>()
            for (y in 1..23) {
                for (x in 1..15) {
                    if (map[y][x] == '.') floorTiles.add(x to y)
                }
            }

            val start = floorTiles.random(rng)
            px = start.first
            py = start.second

            val far = floorTiles.maxBy { abs(it.first - px) + abs(it.second - py) }
            stairX = far.first
            stairY = far.second

            traps.clear()
            items.clear()
            enemies.clear()

            repeat(5 + floorNumber) {
                val candidates = floorTiles.filter {
                    abs(it.first - px) + abs(it.second - py) > 5
                }
                val q = candidates.randomOrNull()
                if (q != null) {
                    enemies.add(
                        Enemy(q.first, q.second, 5 + floorNumber, rng.nextInt(4))
                    )
                }
            }

            repeat(5) {
                val q = floorTiles.random(rng)
                if (q != (px to py) && q != (stairX to stairY)) {
                    items.add(Item(q.first, q.second, rng.nextInt(5)))
                }
            }

            repeat(4) {
                traps.add(floorTiles.random(rng))
            }

            invalidate()
        }

        private fun enemyName(type: Int): String =
            when (type) {
                0 -> "鬼"
                1 -> "蛇"
                2 -> "魔"
                else -> "虫"
            }

        private fun itemName(type: Int): String =
            when (type) {
                0 -> "剣"
                1 -> "盾"
                2 -> "薬"
                3 -> "杖"
                else -> "食"
            }

        private fun move(dx: Int, dy: Int) {
            val nx = px + dx
            val ny = py + dy

            if (nx !in 1..15 || ny !in 1..23 || map[ny][nx] == '#') {
                message = "壁だ"
                invalidate()
                return
            }

            val enemy = enemies.firstOrNull { it.x == nx && it.y == ny }
            if (enemy != null) {
                attack(enemy)
                turn()
                return
            }

            px = nx
            py = ny
            hunger--

            if (hunger <= 0) {
                hp--
                message = "空腹でHP減少"
            }

            checkTile()

            if (px == stairX && py == stairY) {
                floorNumber++
                level++
                maxHp += 3
                hp = maxHp
                score += 100
                newFloor()
                message = "階段を降りた！ 第${floorNumber}階"
            } else {
                turn()
            }
        }

        private fun attack(enemy: Enemy) {
            val damage = sword + swordPlus
            enemy.hp -= damage
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 70)
            message = "攻撃！ $damage ダメージ"

            if (enemy.hp <= 0) {
                enemies.remove(enemy)
                gold += rng.nextInt(3, 10)
                exp += 2
                score += 10

                if (exp >= level * 5) {
                    exp = 0
                    level++
                    maxHp += 3
                    hp = maxHp
                    message = "レベルアップ！"
                }
            }
        }

        private fun turn() {
            for (enemy in enemies.toList()) {
                if (enemy.sleep > 0) {
                    enemy.sleep--
                    continue
                }

                if (abs(enemy.x - px) + abs(enemy.y - py) == 1) {
                    hp -= maxOf(1, 1 + floorNumber / 4 - shieldPlus)
                    message = "敵の攻撃！"
                } else if (abs(enemy.x - px) + abs(enemy.y - py) < 8) {
                    val dx = if (px > enemy.x) 1 else if (px < enemy.x) -1 else 0
                    val dy = if (py > enemy.y) 1 else if (py < enemy.y) -1 else 0
                    val nx = enemy.x + dx
                    val ny = enemy.y + dy

                    if (
                        nx in 1..15 &&
                        ny in 1..23 &&
                        map[ny][nx] == '.' &&
                        enemies.none { it !== enemy && it.x == nx && it.y == ny } &&
                        !(nx == px && ny == py)
                    ) {
                        enemy.x = nx
                        enemy.y = ny
                    }
                }
            }

            if (hp <= 0) {
                hp = 0
                gameOver = true
                val best = prefs.getInt("best", 0)
                prefs.edit().putInt("best", maxOf(score, best)).apply()
            }

            invalidate()
        }

        private fun checkTile() {
            if (traps.remove(px to py)) {
                hp -= rng.nextInt(2, 6)
                message = "罠を踏んだ！"
            }

            val item = items.firstOrNull { it.x == px && it.y == py }
            if (item != null) {
                items.remove(item)

                when (item.type) {
                    0 -> swordPlus++
                    1 -> shieldPlus++
                    2 -> potions++
                    3 -> hasStaff = true
                    4 -> food++
                }

                message = "アイテムを拾った: ${itemName(item.type)}"
            }
        }

        private fun eat() {
            if (food > 0) {
                food--
                hunger = (hunger + 35).coerceAtMost(100)
                message = "食料を食べた"
                turn()
            } else {
                message = "食料がない"
                invalidate()
            }
        }

        private fun drink() {
            if (potions > 0 && hp < maxHp) {
                potions--
                hp = (hp + 12).coerceAtMost(maxHp)
                message = "薬で回復"
                turn()
            } else {
                message = "薬が使えない"
                invalidate()
            }
        }

        private fun cast() {
            if (!hasStaff) {
                message = "杖を持っていない"
                invalidate()
                return
            }

            val target = enemies.minByOrNull {
                abs(it.x - px) + abs(it.y - py)
            }

            if (target != null && abs(target.x - px) + abs(target.y - py) <= 5) {
                target.hp -= 10
                message = "魔法！ 10ダメージ"
                if (target.hp <= 0) enemies.remove(target)
                turn()
            } else {
                message = "魔法の射程に敵がいない"
                invalidate()
            }
        }

        private fun shoot() {
            if (arrows <= 0) {
                message = "矢がない"
                invalidate()
                return
            }

            val target = enemies.firstOrNull {
                (it.x == px && abs(it.y - py) <= 4) ||
                    (it.y == py && abs(it.x - px) <= 4)
            }

            if (target != null) {
                arrows--
                target.hp -= 6
                message = "矢を放った！"
                if (target.hp <= 0) {
                    enemies.remove(target)
                    gold += 3
                }
            } else {
                message = "一直線上に敵がいない"
            }

            turn()
        }
    }
}
