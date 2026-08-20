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

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameView = GameView()
        gameView.isFocusable = true
        gameView.isFocusableInTouchMode = true
        setContentView(gameView)
        gameView.requestFocus()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (::gameView.isInitialized && gameView.handleControllerKey(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

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

    inner class GameView : View(this@MainActivity) {
        private val rng = Random(System.currentTimeMillis())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val prefs = getSharedPreferences("save", Context.MODE_PRIVATE)
        private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)

        private var mode = 0
        private var menuIndex = 0
        private var menuPage = 0
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

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.rgb(5, 6, 9))
            try {

            if (mode == 0) {
                drawTitle(canvas)
                return
            }

            if (mode == 2) {
                drawShop(canvas)
                return
            }

            if (mode == 3) {
                drawMenu(canvas)
                return
            }

            val cell = minOf(width / 17f, height * 0.60f / 25f)
            val left = (width - 17 * cell) / 2f
            val top = 48f

            paint.textAlign = Paint.Align.CENTER

            // Dungeon frame
            paint.color = Color.rgb(20, 22, 28)
            canvas.drawRoundRect(
                left - 7f, top - 7f,
                left + 17 * cell + 7f,
                top + 25 * cell + 7f,
                10f, 10f, paint
            )

            // Dungeon tiles
            for (y in 0..24) {
                for (x in 0..16) {
                    val floor = map[y][x] == '.'
                    paint.color = if (floor) {
                        val shade = 62 + ((x * 13 + y * 7) % 16)
                        Color.rgb(shade, shade - 4, shade - 10)
                    } else {
                        Color.rgb(28, 30, 36)
                    }

                    canvas.drawRect(
                        left + x * cell,
                        top + y * cell,
                        left + (x + 1) * cell - 1.5f,
                        top + (y + 1) * cell - 1.5f,
                        paint
                    )

                    if (floor && (x + y) % 4 == 0) {
                        paint.color = Color.rgb(82, 77, 68)
                        canvas.drawCircle(
                            left + x * cell + cell * .28f,
                            top + y * cell + cell * .32f,
                            maxOf(1.2f, cell * .035f),
                            paint
                        )
                    }

                    if (!floor) {
                        paint.color = Color.rgb(42, 44, 51)
                        canvas.drawLine(
                            left + x * cell + 2,
                            top + y * cell + 2,
                            left + (x + 1) * cell - 3,
                            top + y * cell + 2,
                            paint
                        )
                    }
                }
            }

            // Stairs
            val sx = left + stairX * cell
            val sy = top + stairY * cell

            paint.color = Color.rgb(120, 95, 35)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 7f
            canvas.drawCircle(
                sx + cell / 2f,
                sy + cell / 2f,
                cell * .38f,
                paint
            )

            paint.color = Color.rgb(255, 215, 70)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawRect(
                sx + cell * .22f, sy + cell * .18f,
                sx + cell * .78f, sy + cell * .82f, paint
            )
            paint.style = Paint.Style.FILL
            paint.textSize = cell * .48f
            canvas.drawText(
                "▼", sx + cell / 2,
                sy + cell * .66f, paint
            )

            // Traps
            paint.color = Color.rgb(135, 80, 80)
            paint.textSize = cell * .42f
            for (trap in traps) {
                canvas.drawText(
                    "^",
                    left + trap.first * cell + cell / 2,
                    top + trap.second * cell + cell * .68f,
                    paint
                )
            }

            // Items — small pixel-art-like icons
            for (item in items) {
                drawItemSprite(
                    canvas,
                    left + item.x * cell,
                    top + item.y * cell,
                    cell,
                    item.type
                )
            }

            // Enemies — each type has its own silhouette/face
            for (enemy in enemies) {
                drawEnemySprite(
                    canvas,
                    left + enemy.x * cell,
                    top + enemy.y * cell,
                    cell,
                    enemy.type,
                    enemy.sleep > 0
                )
            }

            // Player — readable little adventurer sprite
            drawPlayerSprite(
                canvas,
                left + px * cell,
                top + py * cell,
                cell
            )

            // Top HUD
            paint.color = Color.rgb(18, 20, 27)
            canvas.drawRoundRect(
                8f, 5f, width - 8f, 41f,
                8f, 8f, paint
            )

            paint.color = Color.WHITE
            paint.textSize = 14f
            canvas.drawText(
                "第${floorNumber}階",
                width * .13f, 28f, paint
            )
            canvas.drawText(
                "Lv $level",
                width * .31f, 28f, paint
            )
            canvas.drawText(
                "HP $hp/$maxHp",
                width * .53f, 28f, paint
            )
            canvas.drawText(
                "G $gold",
                width * .78f, 28f, paint
            )

            // HP / hunger bars
            val barLeft = 12f
            val barRight = width - 12f
            val barWidth = barRight - barLeft

            paint.color = Color.rgb(35, 37, 43)
            canvas.drawRoundRect(
                barLeft, top + 25 * cell + 12,
                barRight, top + 25 * cell + 24,
                5f, 5f, paint
            )

            paint.color = Color.rgb(210, 55, 65)
            val hpRatio = (hp.toFloat() / maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)
            canvas.drawRoundRect(
                barLeft, top + 25 * cell + 12,
                barLeft + barWidth * hpRatio,
                top + 25 * cell + 24,
                5f, 5f, paint
            )

            paint.color = Color.rgb(35, 37, 43)
            canvas.drawRoundRect(
                barLeft, top + 25 * cell + 29,
                barRight, top + 25 * cell + 41,
                5f, 5f, paint
            )

            paint.color = Color.rgb(205, 150, 55)
            val hungerRatio = (hunger / 100f).coerceIn(0f, 1f)
            canvas.drawRoundRect(
                barLeft, top + 25 * cell + 29,
                barLeft + barWidth * hungerRatio,
                top + 25 * cell + 41,
                5f, 5f, paint
            )

            paint.color = Color.WHITE
            paint.textSize = 12f
            canvas.drawText(
                "HP",
                barLeft + 20f,
                top + 25 * cell + 22f,
                paint
            )
            canvas.drawText(
                "満腹 $hunger",
                barLeft + 48f,
                top + 25 * cell + 39f,
                paint
            )

            // EXP bar
            paint.color = Color.rgb(35, 37, 43)
            canvas.drawRoundRect(
                barLeft, top + 25 * cell + 46,
                barRight, top + 25 * cell + 52,
                3f, 3f, paint
            )
            paint.color = Color.rgb(90, 150, 220)
            val expNeed = (level * 5).coerceAtLeast(1)
            val expRatio = (exp.toFloat() / expNeed).coerceIn(0f, 1f)
            canvas.drawRoundRect(
                barLeft, top + 25 * cell + 46,
                barLeft + barWidth * expRatio,
                top + 25 * cell + 52,
                3f, 3f, paint
            )

            // Equipment strip
            paint.color = Color.rgb(18, 20, 27)
            canvas.drawRoundRect(
                8f, top + 25 * cell + 49,
                width - 8f, top + 25 * cell + 82,
                8f, 8f, paint
            )

            paint.color = Color.WHITE
            paint.textSize = 12f
            canvas.drawText(
                "剣 ${sword + swordPlus}",
                width * .12f, top + 25 * cell + 70, paint
            )
            canvas.drawText(
                "盾 +$shieldPlus",
                width * .30f, top + 25 * cell + 70, paint
            )
            canvas.drawText(
                "矢 $arrows",
                width * .48f, top + 25 * cell + 70, paint
            )
            canvas.drawText(
                "食 $food",
                width * .66f, top + 25 * cell + 70, paint
            )
            canvas.drawText(
                "薬 $potions",
                width * .84f, top + 25 * cell + 70, paint
            )

            // Message window
            paint.color = Color.rgb(17, 18, 24)
            canvas.drawRoundRect(
                10f,
                top + 25 * cell + 90,
                width - 10f,
                top + 25 * cell + 123,
                7f, 7f, paint
            )
            paint.color = Color.rgb(75, 78, 90)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            canvas.drawRoundRect(
                10f,
                top + 25 * cell + 90,
                width - 10f,
                top + 25 * cell + 123,
                7f, 7f, paint
            )
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.textSize = 12f
            canvas.drawText(
                if (message.isBlank()) "迷宮を探索しよう……" else message,
                width / 2f,
                top + 25 * cell + 111,
                paint
            )

            // Touch controls remain available.
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
                canvas.drawText(
                    "力尽きた",
                    width / 2f,
                    height * .42f,
                    paint
                )
                paint.textSize = 17f
                canvas.drawText(
                    "A / タップでタイトルへ",
                    width / 2f,
                    height * .50f,
                    paint
                )
            }
            } catch (e: Exception) {
                paint.color = Color.rgb(8, 8, 10)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.color = Color.WHITE
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = 22f
                canvas.drawText("RogueDungeon", width / 2f, height * .40f, paint)
                paint.textSize = 14f
                canvas.drawText("A / タップで開始", width / 2f, height * .50f, paint)
            }
        }

        private fun drawPlayerSprite(canvas: Canvas, x: Float, y: Float, s: Float) {
            val cx = x + s / 2f
            val cy = y + s / 2f

            paint.style = Paint.Style.FILL

            // shadow
            paint.color = Color.argb(110, 0, 0, 0)
            canvas.drawOval(
                cx - s * .31f, y + s * .72f,
                cx + s * .31f, y + s * .88f, paint
            )

            // cloak/body
            paint.color = Color.rgb(42, 92, 125)
            canvas.drawRoundRect(
                x + s * .25f, y + s * .42f,
                x + s * .75f, y + s * .82f,
                s * .12f, s * .12f, paint
            )

            // head
            paint.color = Color.rgb(218, 171, 125)
            canvas.drawCircle(cx, y + s * .32f, s * .19f, paint)

            // hair/hood
            paint.color = Color.rgb(55, 42, 38)
            canvas.drawArc(
                x + s * .29f, y + s * .13f,
                x + s * .71f, y + s * .50f,
                180f, 180f, true, paint
            )

            // sword
            paint.color = Color.LTGRAY
            paint.strokeWidth = maxOf(2f, s * .06f)
            canvas.drawLine(
                x + s * .70f, y + s * .47f,
                x + s * .91f, y + s * .25f,
                paint
            )
            paint.color = Color.rgb(190, 145, 60)
            canvas.drawLine(
                x + s * .64f, y + s * .55f,
                x + s * .78f, y + s * .42f,
                paint
            )
        }

        private fun drawEnemySprite(
            canvas: Canvas,
            x: Float,
            y: Float,
            s: Float,
            type: Int,
            asleep: Boolean
        ) {
            val cx = x + s / 2f
            val cy = y + s / 2f

            paint.style = Paint.Style.FILL
            paint.color = Color.argb(100, 0, 0, 0)
            canvas.drawOval(
                cx - s * .32f, y + s * .73f,
                cx + s * .32f, y + s * .88f, paint
            )

            when (type) {
                0 -> {
                    // Slime
                    paint.color = Color.rgb(205, 65, 70)
                    canvas.drawRoundRect(
                        x + s * .20f, y + s * .28f,
                        x + s * .80f, y + s * .78f,
                        s * .20f, s * .20f, paint
                    )
                    paint.color = Color.WHITE
                    canvas.drawCircle(x + s * .40f, y + s * .47f, s * .07f, paint)
                    canvas.drawCircle(x + s * .60f, y + s * .47f, s * .07f, paint)
                    paint.color = Color.BLACK
                    canvas.drawCircle(x + s * .40f, y + s * .47f, s * .025f, paint)
                    canvas.drawCircle(x + s * .60f, y + s * .47f, s * .025f, paint)
                }
                1 -> {
                    // Orc
                    paint.color = Color.rgb(165, 105, 55)
                    canvas.drawCircle(cx, y + s * .45f, s * .31f, paint)
                    paint.color = Color.rgb(235, 205, 150)
                    canvas.drawCircle(x + s * .39f, y + s * .44f, s * .045f, paint)
                    canvas.drawCircle(x + s * .61f, y + s * .44f, s * .045f, paint)
                    paint.color = Color.LTGRAY
                    canvas.drawRect(
                        x + s * .34f, y + s * .62f,
                        x + s * .43f, y + s * .75f, paint
                    )
                    canvas.drawRect(
                        x + s * .57f, y + s * .62f,
                        x + s * .66f, y + s * .75f, paint
                    )
                }
                2 -> {
                    // Mage
                    paint.color = Color.rgb(72, 92, 175)
                    val path = android.graphics.Path()
                    path.moveTo(cx, y + s * .14f)
                    path.lineTo(x + s * .18f, y + s * .78f)
                    path.lineTo(x + s * .82f, y + s * .78f)
                    path.close()
                    canvas.drawPath(path, paint)
                    paint.color = Color.rgb(210, 180, 145)
                    canvas.drawCircle(cx, y + s * .42f, s * .18f, paint)
                    paint.color = Color.CYAN
                    canvas.drawCircle(cx, y + s * .43f, s * .05f, paint)
                }
                else -> {
                    // Beast
                    paint.color = Color.rgb(80, 170, 90)
                    canvas.drawOval(
                        x + s * .17f, y + s * .25f,
                        x + s * .83f, y + s * .78f, paint
                    )
                    paint.color = Color.rgb(45, 85, 45)
                    canvas.drawCircle(x + s * .39f, y + s * .44f, s * .06f, paint)
                    canvas.drawCircle(x + s * .61f, y + s * .44f, s * .06f, paint)
                    paint.color = Color.WHITE
                    canvas.drawCircle(x + s * .39f, y + s * .42f, s * .025f, paint)
                    canvas.drawCircle(x + s * .61f, y + s * .42f, s * .025f, paint)
                }
            }

            if (asleep) {
                paint.color = Color.WHITE
                paint.textSize = s * .22f
                canvas.drawText("Z", x + s * .72f, y + s * .24f, paint)
            }
        }

        private fun drawItemSprite(
            canvas: Canvas,
            x: Float,
            y: Float,
            s: Float,
            type: Int
        ) {
            val cx = x + s / 2f

            paint.style = Paint.Style.FILL

            when (type) {
                0 -> {
                    // Coin
                    paint.color = Color.rgb(245, 205, 55)
                    canvas.drawCircle(cx, y + s * .52f, s * .25f, paint)
                    paint.color = Color.rgb(110, 80, 25)
                    paint.textSize = s * .25f
                    canvas.drawText("G", cx, y + s * .60f, paint)
                }
                1 -> {
                    // Potion
                    paint.color = Color.LTGRAY
                    canvas.drawRect(
                        x + s * .40f, y + s * .18f,
                        x + s * .60f, y + s * .32f, paint
                    )
                    paint.color = Color.CYAN
                    canvas.drawRoundRect(
                        x + s * .29f, y + s * .30f,
                        x + s * .71f, y + s * .75f,
                        s * .10f, s * .10f, paint
                    )
                }
                2 -> {
                    // Food
                    paint.color = Color.rgb(190, 110, 55)
                    canvas.drawOval(
                        x + s * .25f, y + s * .35f,
                        x + s * .75f, y + s * .72f, paint
                    )
                    paint.color = Color.rgb(235, 175, 100)
                    canvas.drawOval(
                        x + s * .34f, y + s * .43f,
                        x + s * .66f, y + s * .58f, paint
                    )
                }
                3 -> {
                    // Scroll / staff
                    paint.color = Color.rgb(230, 210, 150)
                    canvas.drawRect(
                        x + s * .31f, y + s * .25f,
                        x + s * .69f, y + s * .72f, paint
                    )
                    paint.color = Color.rgb(90, 55, 35)
                    canvas.drawCircle(cx, y + s * .25f, s * .08f, paint)
                    canvas.drawCircle(cx, y + s * .72f, s * .08f, paint)
                }
                else -> {
                    // Gem
                    paint.color = Color.rgb(125, 215, 235)
                    val path = android.graphics.Path()
                    path.moveTo(cx, y + s * .16f)
                    path.lineTo(x + s * .78f, y + s * .40f)
                    path.lineTo(cx, y + s * .80f)
                    path.lineTo(x + s * .22f, y + s * .40f)
                    path.close()
                    canvas.drawPath(path, paint)
                    paint.color = Color.WHITE
                    canvas.drawCircle(
                        x + s * .40f, y + s * .36f,
                        s * .045f, paint
                    )
                }
            }
        }

        private fun drawMenu(canvas: Canvas) {
            canvas.drawColor(Color.rgb(7, 8, 12))

            paint.color = Color.rgb(24, 26, 34)
            canvas.drawRoundRect(
                width * .08f, height * .06f,
                width * .92f, height * .94f,
                18f, 18f, paint
            )

            paint.color = Color.rgb(65, 68, 80)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawRoundRect(
                width * .08f, height * .06f,
                width * .92f, height * .94f,
                18f, 18f, paint
            )
            paint.style = Paint.Style.FILL

            if (menuPage == 0) {
                drawMainMenu(canvas)
            } else {
                when (menuPage) {
                    1 -> drawStatusPage(canvas)
                    2 -> drawItemsPage(canvas)
                    3 -> drawHelpPage(canvas)
                    4 -> drawSettingsPage(canvas)
                }
            }
        }

        private fun drawMainMenu(canvas: Canvas) {
            paint.color = Color.YELLOW
            paint.textSize = 30f
            canvas.drawText("MENU", width / 2f, height * .15f, paint)

            val entries = arrayOf(
                "ステータス",
                "アイテム",
                "操作説明",
                "設定",
                "ゲームに戻る",
                "タイトルへ戻る"
            )

            paint.textSize = 18f
            entries.forEachIndexed { index, text ->
                val y = height * .24f + index * height * .095f

                if (index == menuIndex) {
                    paint.color = Color.rgb(65, 72, 92)
                    canvas.drawRoundRect(
                        width * .16f, y - 27f,
                        width * .84f, y + 10f,
                        8f, 8f, paint
                    )
                    paint.color = Color.YELLOW
                    canvas.drawText("▶ $text", width / 2f, y, paint)
                } else {
                    paint.color = Color.LTGRAY
                    canvas.drawText(text, width / 2f, y, paint)
                }
            }

            paint.color = Color.GRAY
            paint.textSize = 12f
            canvas.drawText(
                "↑↓ 選択    A 決定    B 戻る    START 閉じる",
                width / 2f,
                height * .89f,
                paint
            )
        }

        private fun panelText(canvas: Canvas, title: String, lines: List<String>) {
            paint.color = Color.YELLOW
            paint.textSize = 27f
            canvas.drawText(title, width / 2f, height * .15f, paint)

            paint.textAlign = Paint.Align.LEFT
            paint.color = Color.WHITE
            paint.textSize = 16f

            var y = height * .25f
            for (line in lines) {
                canvas.drawText(line, width * .18f, y, paint)
                y += height * .065f
            }

            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.GRAY
            paint.textSize = 12f
            canvas.drawText(
                "B / START でメニューへ戻る",
                width / 2f,
                height * .89f,
                paint
            )
        }

        private fun drawStatusPage(canvas: Canvas) {
            panelText(
                canvas,
                "ステータス",
                listOf(
                    "階層              第${floorNumber}階",
                    "レベル            Lv $level",
                    "経験値            $exp / ${level * 5}",
                    "HP                $hp / $maxHp",
                    "満腹度            $hunger / 100",
                    "攻撃力            ${sword + swordPlus}",
                    "剣強化            +$swordPlus",
                    "盾強化            +$shieldPlus",
                    "所持金            $gold G",
                    "スコア            $score",
                    "杖                ${if (hasStaff) "所持" else "なし"}"
                )
            )
        }

        private fun drawItemsPage(canvas: Canvas) {
            panelText(
                canvas,
                "アイテム",
                listOf(
                    "剣                 攻撃 ${sword + swordPlus}",
                    "盾                 強化 +$shieldPlus",
                    "矢                 $arrows 本",
                    "食料               $food 個",
                    "回復薬             $potions 個",
                    "魔法の杖           ${if (hasStaff) "所持" else "なし"}",
                    "",
                    "拾ったアイテムは自動的に装備・所持します。"
                )
            )
        }

        private fun drawHelpPage(canvas: Canvas) {
            panelText(
                canvas,
                "操作説明",
                listOf(
                    "十字キー / 左スティック   移動",
                    "A                         決定 / 開始",
                    "B                         戻る",
                    "X                         食料を食べる",
                    "Y                         回復薬を使う",
                    "L1 / LB                   杖を使う",
                    "R1 / RB                   弓を撃つ",
                    "START                     メニュー",
                    "",
                    "敵に隣接して移動すると攻撃します。",
                    "階段を探して次の階へ進みましょう。"
                )
            )
        }

        private fun drawSettingsPage(canvas: Canvas) {
            panelText(
                canvas,
                "設定",
                listOf(
                    "効果音              ON",
                    "タッチ操作          ON",
                    "コントローラー      ON",
                    "",
                    "現在のバージョン",
                    "迷宮の旅人 DX",
                    "",
                    "※ 設定項目は今後追加できます。"
                )
            )
        }

        private fun drawStatus(canvas: Canvas) {
            // Kept as a separate helper for future menu expansion.
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

            if (mode == 3) {
                if (menuPage != 0) {
                    menuPage = 0
                    invalidate()
                    return true
                }

                val y = event.y
                val first = height * .24f
                val step = height * .095f
                val selected = ((y - first + step / 2) / step).toInt()
                    .coerceIn(0, 5)

                menuIndex = selected
                if (event.x in width * .15f..width * .85f) {
                    when (menuIndex) {
                        0 -> menuPage = 1
                        1 -> menuPage = 2
                        2 -> menuPage = 3
                        3 -> menuPage = 4
                        4 -> mode = 1
                        5 -> mode = 0
                    }
                }
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
                x in w * .35f..w * .65f &&
                        y > h * .72f &&
                        y < h * .86f -> move(0, -1)
                x in w * .35f..w * .65f &&
                        y > h * .86f -> move(0, 1)
                x < w * .28f && y > h * .89f -> eat()
                x in w * .28f..w * .49f && y > h * .89f -> drink()
                x in w * .49f..w * .74f && y > h * .89f -> cast()
                x > w * .74f && y > h * .89f -> shoot()
            }

            return true
        }

        fun handleControllerKey(event: android.view.KeyEvent): Boolean {
            if (event.action != android.view.KeyEvent.ACTION_DOWN) return false

            val key = event.keyCode

            // A = confirm / start
            if (key == android.view.KeyEvent.KEYCODE_BUTTON_A ||
                key == android.view.KeyEvent.KEYCODE_ENTER) {
                if (mode == 0) {
                    newGame()
                } else if (gameOver) {
                    mode = 0
                    invalidate()
                } else if (mode == 3) {
                    if (menuPage == 0) {
                        when (menuIndex) {
                            0 -> menuPage = 1
                            1 -> menuPage = 2
                            2 -> menuPage = 3
                            3 -> menuPage = 4
                            4 -> mode = 1
                            5 -> mode = 0
                        }
                    }
                    invalidate()
                }
                return true
            }

            // B = back / cancel
            if (key == android.view.KeyEvent.KEYCODE_BUTTON_B ||
                key == android.view.KeyEvent.KEYCODE_ESCAPE) {
                if (mode == 3) {
                    if (menuPage != 0) {
                        menuPage = 0
                    } else {
                        mode = 1
                    }
                    invalidate()
                }
                return true
            }

            // START = in-game menu
            if (key == android.view.KeyEvent.KEYCODE_BUTTON_START) {
                if (!gameOver && mode == 1) {
                    mode = 3
                    menuPage = 0
                    menuIndex = 0
                    invalidate()
                } else if (mode == 3) {
                    mode = 1
                    menuPage = 0
                    invalidate()
                }
                return true
            }

            // D-pad / left stick digital directions
            when (key) {
                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                    if (mode == 3) {
                        menuIndex = (menuIndex - 1 + 6) % 6
                        invalidate()
                    } else if (mode == 1) {
                        move(0, -1)
                    }
                    return true
                }

                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (mode == 3) {
                        menuIndex = (menuIndex + 1) % 6
                        invalidate()
                    } else if (mode == 1) {
                        move(0, 1)
                    }
                    return true
                }

                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (mode == 1) move(-1, 0)
                    return true
                }

                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (mode == 1) move(1, 0)
                    return true
                }

                android.view.KeyEvent.KEYCODE_BUTTON_L1 -> {
                    if (mode == 1) cast()
                    return true
                }

                android.view.KeyEvent.KEYCODE_BUTTON_R1 -> {
                    if (mode == 1) shoot()
                    return true
                }

                android.view.KeyEvent.KEYCODE_BUTTON_X -> {
                    if (mode == 1) eat()
                    return true
                }

                android.view.KeyEvent.KEYCODE_BUTTON_Y -> {
                    if (mode == 1) drink()
                    return true
                }
            }

            return false
        }

        private fun placeReachableStairs() {
            val reachable = mutableSetOf<Pair<Int, Int>>()
            val queue = java.util.ArrayDeque<Pair<Int, Int>>()
            val start = Pair(px, py)

            map[py][px] = '.'
            queue.add(start)
            reachable.add(start)

            val dirs = arrayOf(
                Pair(1, 0), Pair(-1, 0),
                Pair(0, 1), Pair(0, -1)
            )

            while (queue.isNotEmpty()) {
                val p = queue.removeFirst()
                for (d in dirs) {
                    val nx = p.first + d.first
                    val ny = p.second + d.second
                    if (nx !in 0..16 || ny !in 0..24) continue
                    if (map[ny][nx] == '#') continue
                    val next = Pair(nx, ny)
                    if (reachable.add(next)) queue.add(next)
                }
            }

            val candidates = reachable
                .filter { it != start }
                .shuffled(rng)

            val chosen = candidates.firstOrNull() ?: start
            stairX = chosen.first
            stairY = chosen.second
            map[stairY][stairX] = '.'
        }

        private fun newGame() {
            requestFocus()
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

            // 部屋＋通路方式。
            // まず複数の部屋を作り、各部屋を必ず前の部屋へ通路で接続する。
            // これにより「孤立した部屋」が発生せず、全体が一本の連結した迷宮になる。
            data class Room(
                val x: Int,
                val y: Int,
                val w: Int,
                val h: Int
            ) {
                val centerX: Int get() = x + w / 2
                val centerY: Int get() = y + h / 2
            }

            val rooms = mutableListOf<Room>()
            val maxRooms = 10
            var attempts = 0

            while (rooms.size < maxRooms && attempts < 120) {
                attempts++

                val w = rng.nextInt(3, 7)
                val h = rng.nextInt(3, 6)
                val x = rng.nextInt(1, 17 - w)
                val y = rng.nextInt(1, 25 - h)

                val candidate = Room(x, y, w, h)

                // 部屋同士を少し離して配置。
                val overlaps = rooms.any { room ->
                    candidate.x - 1 < room.x + room.w &&
                    candidate.x + candidate.w + 1 > room.x &&
                    candidate.y - 1 < room.y + room.h &&
                    candidate.y + candidate.h + 1 > room.y
                }

                if (!overlaps) {
                    rooms.add(candidate)
                }
            }

            // 部屋が少なすぎる場合でも最低限の迷宮を確保。
            if (rooms.isEmpty()) {
                rooms.add(Room(6, 10, 5, 5))
            }

            // 部屋を左上から順に並べ、隣接する部屋同士を必ず接続する。
            rooms.sortWith(compareBy<Room> { it.y }.thenBy { it.x })

            for (room in rooms) {
                for (yy in room.y until room.y + room.h) {
                    for (xx in room.x until room.x + room.w) {
                        map[yy][xx] = '.'
                    }
                }
            }

            // 各部屋を「前の部屋」に接続。
            // 部屋の順番に沿って通路を掘るので、全室が一つの連結成分になる。
            for (i in 1 until rooms.size) {
                val a = rooms[i - 1]
                val b = rooms[i]
                carveCorridor(a.centerX, a.centerY, b.centerX, b.centerY)
            }

            // 迷宮の見た目を少し複雑にするため、隣接する部屋を追加接続。
            // ただし主経路は必ず残る。
            for (i in 0 until rooms.size - 1) {
                if (rng.nextBoolean()) {
                    val a = rooms[i]
                    val b = rooms[i + 1]
                    carveCorridor(a.centerX, a.centerY, b.centerX, b.centerY)
                }
            }

            // 最初の部屋の中心から開始。
            val startRoom = rooms.first()
            px = startRoom.centerX.coerceIn(1, 15)
            py = startRoom.centerY.coerceIn(1, 23)
            map[py][px] = '.'

            // 到達可能領域を調査。
            val reachable = mutableSetOf<Pair<Int, Int>>()
            val queue = java.util.ArrayDeque<Pair<Int, Int>>()
            val startPos = px to py

            reachable.add(startPos)
            queue.add(startPos)

            val dirs = arrayOf(
                1 to 0,
                -1 to 0,
                0 to 1,
                0 to -1
            )

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()

                for (dir in dirs) {
                    val nx = current.first + dir.first
                    val ny = current.second + dir.second

                    if (nx !in 1..15 || ny !in 1..23) continue
                    if (map[ny][nx] == '#') continue

                    val next = nx to ny
                    if (reachable.add(next)) {
                        queue.add(next)
                    }
                }
            }

            // 連結確認。
            // 通路生成に問題があった場合は、最後の部屋まで強制接続する。
            for (i in 1 until rooms.size) {
                val room = rooms[i]
                if (!reachable.contains(room.centerX to room.centerY)) {
                    val previous = rooms[i - 1]
                    carveCorridor(
                        previous.centerX,
                        previous.centerY,
                        room.centerX,
                        room.centerY
                    )
                }
            }

            // もう一度到達可能領域を計算。
            reachable.clear()
            queue.clear()
            reachable.add(startPos)
            queue.add(startPos)

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()

                for (dir in dirs) {
                    val nx = current.first + dir.first
                    val ny = current.second + dir.second

                    if (nx !in 1..15 || ny !in 1..23) continue
                    if (map[ny][nx] == '#') continue

                    val next = nx to ny
                    if (reachable.add(next)) {
                        queue.add(next)
                    }
                }
            }

            // 階段は必ず到達可能な場所。
            val farCandidates = reachable
                .filter { it != startPos }
                .sortedByDescending {
                    abs(it.first - px) + abs(it.second - py)
                }

            val stair = when {
                farCandidates.size >= 10 ->
                    farCandidates.take(minOf(20, farCandidates.size)).random(rng)
                farCandidates.isNotEmpty() ->
                    farCandidates.first()
                else ->
                    startPos
            }

            stairX = stair.first
            stairY = stair.second
            map[stairY][stairX] = '.'

            traps.clear()
            items.clear()
            enemies.clear()

            val safeTiles = reachable.filter {
                it != startPos && it != stair
            }

            // 敵
            repeat(5 + floorNumber.coerceAtMost(8)) {
                val candidates = safeTiles.filter {
                    abs(it.first - px) + abs(it.second - py) > 5 &&
                    enemies.none { e -> e.x == it.first && e.y == it.second }
                }

                val q = candidates.randomOrNull()
                if (q != null) {
                    enemies.add(
                        Enemy(
                            q.first,
                            q.second,
                            5 + floorNumber,
                            rng.nextInt(4)
                        )
                    )
                }
            }

            // アイテム
            repeat(5) {
                val candidates = safeTiles.filter {
                    items.none { item ->
                        item.x == it.first && item.y == it.second
                    }
                }

                val q = candidates.randomOrNull()
                if (q != null) {
                    items.add(Item(q.first, q.second, rng.nextInt(5)))
                }
            }

            // 罠
            repeat(4) {
                val candidates = safeTiles.filter {
                    !traps.contains(it)
                }

                val q = candidates.randomOrNull()
                if (q != null) {
                    traps.add(q)
                }
            }

            // 最終保証
            map[stairY][stairX] = '.'
            invalidate()
        }

        private fun carveRoom(room: Any) {
            // Room is local to newFloor; this helper is intentionally unused.
        }

        private fun carveCorridor(x1: Int, y1: Int, x2: Int, y2: Int) {
            var x = x1
            var y = y1

            // 横→縦、または縦→横をランダムにして単調になりすぎないようにする。
            if (rng.nextBoolean()) {
                while (x != x2) {
                    map[y][x] = '.'
                    x += if (x2 > x) 1 else -1
                }
                while (y != y2) {
                    map[y][x] = '.'
                    y += if (y2 > y) 1 else -1
                }
            } else {
                while (y != y2) {
                    map[y][x] = '.'
                    y += if (y2 > y) 1 else -1
                }
                while (x != x2) {
                    map[y][x] = '.'
                    x += if (x2 > x) 1 else -1
                }
            }

            map[y][x] = '.'
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
