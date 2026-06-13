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
    var paused by remember { mutableStateOf(false) }
    var cpuPct by remember { mutableFloatStateOf(0f) }
    var highScore by remember { mutableIntStateOf(AppPrefs.snakeHighScore(context)) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { CpuReader.read(context, "snake") }
        while (true) {
            cpuPct = withContext(Dispatchers.IO) {
                CpuReader.read(context, "snake").totalPercent
            }
            delay(800)
        }
    }

    LaunchedEffect(gameOver, paused) {
        if (gameOver || paused) return@LaunchedEffect
        while (!gameOver && !paused) {
            // Tick speed scales inversely with CPU load: higher CPU = faster snake.
            // 0% CPU -> 380 ms (calm), 100% CPU -> 90 ms (frantic)
            val tickMs = (380 - cpuPct * 2.9f).coerceIn(90f, 380f).toLong()
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
        paused = false
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
                Text("CPU-Boost", color = OnSurfaceMuted, fontSize = 11.sp)
                val cpuColor = when {
                    cpuPct < 25f -> GaugeGreen
                    cpuPct < 50f -> AccentSoft
                    cpuPct < 75f -> GaugeOrange
                    else -> GaugeRed
                }
                Text(
                    "${cpuPct.toInt()} %",
                    color = cpuColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    when {
                        cpuPct < 25f -> "lahm"
                        cpuPct < 50f -> "normal"
                        cpuPct < 75f -> "schnell"
                        else -> "WILD"
                    },
                    color = cpuColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Wisch zum Steuern · CPU-Last steuert die Geschwindigkeit",
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
                    detectTapGestures(onTap = { paused = !paused && !gameOver })
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
                        "GAME OVER",
                        color = GaugeRed,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
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
                Text(
                    "PAUSE · tap zum Fortsetzen",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
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
