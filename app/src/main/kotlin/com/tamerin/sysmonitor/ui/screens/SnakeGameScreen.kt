package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.BatteryReader
import com.tamerin.sysmonitor.data.CpuReader
import com.tamerin.sysmonitor.settings.AppPrefs
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.random.Random

private const val GRID = 18

private enum class Dir { UP, DOWN, LEFT, RIGHT }

private data class Cell(val x: Int, val y: Int)

@Composable
fun SnakeGameScreen() {
    val context = LocalContext.current

    var snake by remember {
        mutableStateOf(listOf(Cell(GRID / 2, GRID / 2), Cell(GRID / 2 - 1, GRID / 2)))
    }
    var direction by remember { mutableStateOf(Dir.RIGHT) }
    var pendingDir by remember { mutableStateOf(Dir.RIGHT) }
    var food by remember { mutableStateOf(Cell(GRID / 2 + 4, GRID / 2)) }
    var score by remember { mutableIntStateOf(0) }
    var gameOver by remember { mutableStateOf(false) }
    var pauseGameOver by remember { mutableStateOf(false) }  // 50% russian roulette
    var paused by remember { mutableStateOf(false) }
    var cpuPct by remember { mutableFloatStateOf(0f) }
    var wattsNow by remember { mutableFloatStateOf(0f) }
    var deviceFactor by remember { mutableFloatStateOf(1f) }
    var highScore by remember { mutableIntStateOf(AppPrefs.snakeHighScore(context)) }

    // Once-per-game device factor based on cores × peak GHz.
    // Snapdragon 888 (8c × ~2.85 GHz) ≈ baseline 1.0; flagships go up to ~1.5.
    LaunchedEffect(Unit) {
        val cpu = withContext(Dispatchers.IO) { CpuReader.read(context, "snake-init") }
        val cores = cpu.coreCount.coerceAtLeast(1)
        val maxGhzAvg = cpu.coreMaxFreqKHz
            .filter { it > 0 }
            .map { it / 1_000_000.0 }
            .takeIf { it.isNotEmpty() }
            ?.average() ?: 2.0
        deviceFactor = ((cores * maxGhzAvg) / 22.0).toFloat().coerceIn(0.5f, 2.0f)
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { CpuReader.read(context, "snake") }
        while (true) {
            val cpu = withContext(Dispatchers.IO) { CpuReader.read(context, "snake") }
            val batt = withContext(Dispatchers.IO) { BatteryReader.read(context) }
            cpuPct = cpu.totalPercent
            wattsNow = batt.wattsNow
            delay(800)
        }
    }

    LaunchedEffect(gameOver, paused) {
        if (gameOver || paused) return@LaunchedEffect
        while (!gameOver && !paused) {
            // Base speed from CPU load (0% → 380 ms, 100% → 90 ms)
            val baseTick = (380 - cpuPct * 2.9f).coerceIn(90f, 380f)
            // Watt boost: live power consumption — 5 W = +1× multiplier
            val wattBoost = 1f + (wattsNow.coerceIn(0f, 30f) / 5f) * 0.4f
            // Final = base / (deviceFactor × wattBoost) — clamped 50..400 ms
            val tickMs = (baseTick / (deviceFactor * wattBoost)).coerceIn(50f, 400f).toLong()
            delay(tickMs)
            if (gameOver || paused) break

            direction = pendingDir
            val head = snake.first()
            val newHead = when (direction) {
                Dir.UP -> Cell(head.x, head.y - 1)
                Dir.DOWN -> Cell(head.x, head.y + 1)
                Dir.LEFT -> Cell(head.x - 1, head.y)
                Dir.RIGHT -> Cell(head.x + 1, head.y)
            }

            val hitWall = newHead.x !in 0 until GRID || newHead.y !in 0 until GRID
            val hitSelf = newHead in snake
            if (hitWall || hitSelf) {
                gameOver = true
                if (score > highScore) {
                    highScore = score
                    AppPrefs.setSnakeHighScore(context, score)
                }
                break
            }

            val ate = newHead == food
            snake = listOf(newHead) + if (ate) snake else snake.dropLast(1)
            if (ate) {
                score++
                food = spawnFood(snake)
            }
        }
    }

    fun tryChangeDirection(newDir: Dir) {
        // Disallow 180° reversal (would instantly kill snake).
        val opposite = when (direction) {
            Dir.UP -> Dir.DOWN
            Dir.DOWN -> Dir.UP
            Dir.LEFT -> Dir.RIGHT
            Dir.RIGHT -> Dir.LEFT
        }
        if (newDir != opposite) pendingDir = newDir
    }

    fun restart() {
        snake = listOf(Cell(GRID / 2, GRID / 2), Cell(GRID / 2 - 1, GRID / 2))
        direction = Dir.RIGHT
        pendingDir = Dir.RIGHT
        food = spawnFood(snake)
        score = 0
        gameOver = false
        pauseGameOver = false
        paused = false
    }

    /** Pause tap = 50% Chance dass die Runde sofort vorbei ist. Russian roulette. */
    fun tryPause() {
        if (gameOver) return
        if (paused) { paused = false; return }
        if (Random.nextFloat() < 0.5f) {
            pauseGameOver = true
            gameOver = true
            if (score > highScore) {
                highScore = score
                AppPrefs.setSnakeHighScore(context, score)
            }
        } else {
            paused = true
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Score", color = OnSurfaceMuted, fontSize = 11.sp)
                Text(
                    "$score",
                    color = Accent,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text("Best: $highScore", color = OnSurfaceMuted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                val wattBoost = 1f + (wattsNow.coerceIn(0f, 30f) / 5f) * 0.4f
                val totalMult = deviceFactor * wattBoost
                val multColor = when {
                    totalMult < 1.0f -> AccentSoft
                    totalMult < 1.4f -> GaugeGreen
                    totalMult < 2.0f -> GaugeOrange
                    else -> GaugeRed
                }
                Text("Multiplier", color = OnSurfaceMuted, fontSize = 11.sp)
                Text(
                    "×${"%.2f".format(totalMult)}",
                    color = multColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "CPU ${cpuPct.toInt()}% · ${"%.1f".format(wattsNow)}W",
                    color = OnSurfaceMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "Device ×${"%.2f".format(deviceFactor)}",
                    color = OnSurfaceMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Wisch zum Steuern · Speed = CPU-Last × Device-Klasse × Watt-Boost · ⚠ Pause = 50 % Tod",
            color = OnSurfaceMuted, fontSize = 11.sp
        )
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0A0A0A))
                .pointerInput(direction) {
                    var startX = 0f
                    var startY = 0f
                    detectDragGestures(
                        onDragStart = { offset ->
                            startX = offset.x
                            startY = offset.y
                        },
                        onDragEnd = { },
                        onDrag = { change, _ ->
                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            val threshold = 40f
                            if (abs(dx) > abs(dy)) {
                                if (dx > threshold) {
                                    tryChangeDirection(Dir.RIGHT)
                                    startX = change.position.x; startY = change.position.y
                                } else if (dx < -threshold) {
                                    tryChangeDirection(Dir.LEFT)
                                    startX = change.position.x; startY = change.position.y
                                }
                            } else {
                                if (dy > threshold) {
                                    tryChangeDirection(Dir.DOWN)
                                    startX = change.position.x; startY = change.position.y
                                } else if (dy < -threshold) {
                                    tryChangeDirection(Dir.UP)
                                    startX = change.position.x; startY = change.position.y
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { tryPause() })
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                val cellSize = size.minDimension / GRID

                // Grid background dots
                for (gx in 0 until GRID) {
                    for (gy in 0 until GRID) {
                        drawRect(
                            color = Color(0x10FFFFFF),
                            topLeft = Offset(
                                gx * cellSize + cellSize * 0.45f,
                                gy * cellSize + cellSize * 0.45f
                            ),
                            size = Size(cellSize * 0.1f, cellSize * 0.1f)
                        )
                    }
                }

                // Food (pulsing accent)
                drawRect(
                    color = GaugeGreen,
                    topLeft = Offset(food.x * cellSize + 2f, food.y * cellSize + 2f),
                    size = Size(cellSize - 4f, cellSize - 4f)
                )

                // Snake — head in solid Accent, body gradient to AccentSoft
                snake.forEachIndexed { idx, c ->
                    val color = if (idx == 0) Accent else AccentSoft
                    drawRect(
                        color = color,
                        topLeft = Offset(c.x * cellSize + 1f, c.y * cellSize + 1f),
                        size = Size(cellSize - 2f, cellSize - 2f)
                    )
                }
            }

            if (gameOver) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (pauseGameOver) "PAUSE = TOD" else "GAME OVER",
                        color = GaugeRed,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (pauseGameOver) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Pech (50 % Chance)",
                            color = GaugeOrange, fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Score: $score",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { restart() }) { Text("Nochmal") }
                }
            } else if (paused) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "PAUSE",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Glück gehabt · tap zum Fortsetzen",
                        color = OnSurfaceMuted, fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "🐍 Easter Egg: dieses Spiel passt seine Geschwindigkeit an die tatsächliche CPU-Last deines Geräts an. Bei hoher Last (z. B. läuft im Hintergrund ein Benchmark) wird die Schlange schneller.",
            color = OnSurfaceMuted,
            fontSize = 11.sp
        )
    }
}

private fun spawnFood(snake: List<Cell>): Cell {
    val occupied = snake.toSet()
    var attempts = 0
    while (attempts < 200) {
        val candidate = Cell(Random.nextInt(GRID), Random.nextInt(GRID))
        if (candidate !in occupied) return candidate
        attempts++
    }
    // Fallback: any free cell
    for (y in 0 until GRID) {
        for (x in 0 until GRID) {
            val c = Cell(x, y)
            if (c !in occupied) return c
        }
    }
    return Cell(0, 0)
}
