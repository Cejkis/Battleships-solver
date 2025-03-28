import java.lang.Exception
import kotlin.math.pow
import kotlin.random.Random
import kotlin.system.exitProcess


const val UNKNOWN = '*' // can be any of other types
const val SUNKEN_SHIP = 'x'
const val SHIP = 'X' // ship being sunk
const val WATER = '.'
const val MAP_SIZE = 12

// Ships are positioned sideways here, but it doesn't really matter, and they are symmetrical and also applied perpendicularly.
// Except for NINE, they are long lines
enum class ShipShapes(
    // Coordinates of squares that make up the ship shape.
    val structure: List<Pair<Int, Int>>,
    // Dimensions of the whole ship
    val shapeSize: Pair<Int, Int>
) {
    // "Helicarrier"
    NINE(
        listOf(
            Pair(1, 0), Pair(1, 1), Pair(1, 2), Pair(1, 3), Pair(1, 4),
            Pair(0, 1), Pair(2, 1), Pair(0, 3), Pair(2, 3),
        ),
        Pair(3, 5),
    ),
    FIVE(
        listOf(Pair(0, 0), Pair(0, 1), Pair(0, 2), Pair(0, 3), Pair(0, 4)),
        Pair(1, 5),
    ),
    FOUR(
        listOf(Pair(0, 0), Pair(0, 1), Pair(0, 2), Pair(0, 3)),
        Pair(1, 4),
    ),
    THREE(
        listOf(Pair(0, 0), Pair(0, 1), Pair(0, 2)),
        Pair(1, 3),
    ),
    TWO(
        listOf(Pair(0, 0), Pair(0, 1)),
        Pair(1, 2),
    ),
}

class ShapeDirected(val shape: ShipShapes, horizontal: Boolean) {
    val height = if (horizontal) shape.shapeSize.first else shape.shapeSize.second
    val width = if (horizontal) shape.shapeSize.second else shape.shapeSize.first
    val points = if (horizontal) shape.structure
        else shape.structure.map { Pair(it.second, it.first) }
}

fun printMap(map: Array<CharArray>) {
    map.forEach { row ->
        row.forEach {
            print("$it ")
        }
        println()
    }
    println()
}

fun printHeat(map: Array<DoubleArray>) {
    map.forEach { row ->
        row.forEach {
            print(String.format("%.3f ", it))
        }
        println()
    }
    println()
}

fun canShipBePut(legend: Array<CharArray>, x: Int, y: Int, structure: List<Pair<Int, Int>>): Boolean {
    for (it in structure) {
        (-1..1).forEach { row ->
            (-1..1).forEach { column ->
                val currentValue = try {
                    legend[x + it.first + row][y + it.second + column]
                } catch (_: Exception) {
                    // ugly but easy way to ignore getting out of bounds
                    null
                }
                if (currentValue == SHIP) {
                    return false
                }
            }
        }
    }
    return true
}

fun createRandomMap(ships: MutableList<ShipShapes>, seed: Int = Random.nextInt()): Array<CharArray> {
    val legend = Array(MAP_SIZE) { row ->
        CharArray(MAP_SIZE) { WATER }
    }
    val rng = Random(seed)

    ships.forEach { ship ->
        // try putting the ship on random location
        while (true) {
            val shape = ShapeDirected(shape = ship, horizontal = rng.nextBoolean())
            val x = rng.nextInt(MAP_SIZE - shape.height + 1)
            val y = rng.nextInt(MAP_SIZE - shape.width + 1)

            // can I put it there?
            if (canShipBePut(legend, x, y, shape.points)) {
                shape.points.forEach {
                    legend[x + it.first][y + it.second] = SHIP
                }
                break
            }
        }
    }

    printMap(legend)
    return legend
}

class Game {
    var round = 1

    val missingShips = mutableListOf(ShipShapes.NINE, ShipShapes.FIVE, ShipShapes.FOUR, ShipShapes.THREE, ShipShapes.THREE, ShipShapes.TWO)

    val gameLegend = createRandomMap(missingShips)

    // current game progress - what player sees
    val myMap = Array(MAP_SIZE) { CharArray(MAP_SIZE) { UNKNOWN } }

    // coordinates of a ship we are currently sinking. Empty if we are looking for another ship.
    val hitShipPlaces = mutableSetOf<Pair<Int, Int>>()

    // Heat Map is a heuristic map for all coordinates - how good is to shoot there?
    var heatMap = resetHeatMap()

    fun resetHeatMap(): Array<DoubleArray> {
        return Array(MAP_SIZE) { DoubleArray(MAP_SIZE) { 0.0 } }
    }

    fun computeHeatMap(missingShapes: List<ShapeDirected>) {
        heatMap = resetHeatMap()

        // Try to apply all remaining ships to all coords
        missingShapes.forEach { shape ->
            (0..MAP_SIZE - shape.height).forEach { row ->
                (0..MAP_SIZE - shape.width).forEach { column ->
                    var possible = true
                    // we are counting how many unsunk ship pieces are we laying over current shape to radically increase heat there.
                    // The idea is to continue with already sinking ship instead of searching for new places (algorithm is designed to go one by one)
                    var ships = 0

                    shape.points.forEach { // can all points of a ship be applied there?
                        when (myMap[row + it.first][column + it.second]) {
                            WATER, SUNKEN_SHIP -> possible = false
                            SHIP -> ships++
                        }
                    }
                    if (possible) {
                        // increase how good these coords are
                        shape.points.forEach {
                            heatMap[row + it.first][column + it.second] += 0.001 * 10.0.pow(ships.toDouble())
                        }
                    }
                }
            }
        }

        printHeat(heatMap)
    }

    fun sunkShipDetection(shapes: List<ShapeDirected>) {
        val potentialShips = mutableSetOf<Triple<Int, Int, ShapeDirected>>()

        shapes.forEach { shape ->
            (0..MAP_SIZE - shape.height).forEach { row ->
                (0..MAP_SIZE - shape.width).forEach { column ->
                    var match = true
                    val shapeOnMap = shape.points.map { Pair(row + it.first, column + it.second) }

                    if (shapeOnMap.containsAll(hitShipPlaces)) {
                        shapeOnMap.forEach { // how good is this place for this ship?
                            val place = myMap[it.first][it.second]
                            if (place == WATER || place == SUNKEN_SHIP) {
                                match = false
                            }
                        }
                        if (match) { // ship can be put in these coords
                            potentialShips.add(Triple(row, column, shape))
                        }
                    }
                }
            }
        }

        // we successfully sunk ship last round - there is only one shape that fits
        if (potentialShips.size == 1 && hitShipPlaces.size == potentialShips.first().third.points.size) { // only one possible ship - sink it
            val match = potentialShips.first()
            val matchingShape = match.third
            println("Sinking ship " + matchingShape.shape.name)

            val shipCoords = matchingShape.points.map { Pair(match.first + it.first, match.second + it.second) }
            shipCoords.forEach {
                myMap[it.first][it.second] = SUNKEN_SHIP
                // Put water in all surrounding areas that are now UNKNOWN
                (-1..1).forEach { row ->
                    (-1..1).forEach { column ->
                        val currentValue = try {
                            myMap[it.first + row][it.second + column]
                        } catch (_: Exception) {
                            // ugly but easy way to ignore getting out of bounds
                            null
                        }
                        if (currentValue == UNKNOWN) {
                            myMap[it.first + row][it.second + column] = WATER
                        }
                    }
                }
            }
            hitShipPlaces.clear()
            missingShips.remove(matchingShape.shape)
        }
        if (missingShips.isEmpty()) {
            println("Game won in round $round.")
            exitProcess(0)
        }
    }

    fun shoot() {
        // find the biggest heat
        var maxValue = Double.MIN_VALUE
        var maxRow = -1
        var maxCol = -1
        heatMap.indices.forEach { i ->
            heatMap[i].indices.forEach { j ->
                if (heatMap[i][j] > maxValue && myMap[i][j] == UNKNOWN) {
                    maxValue = heatMap[i][j]
                    maxRow = i
                    maxCol = j
                }
            }
        }
        if (maxValue == Double.MIN_VALUE) {
            println("No more moves.")
        }
        println("The biggest heat is $maxValue, row:$maxRow, col:$maxCol")
        println()

        // "shoot"
        myMap[maxRow][maxCol] = gameLegend[maxRow][maxCol]

        if (gameLegend[maxRow][maxCol] == SHIP) {
            hitShipPlaces.add(Pair(maxRow, maxCol))
        }
    }

    fun solve() {
        while (true) {
            println("round ${round++}")
            printMap(myMap)

            // shapes of all unsunk ships in both directions
            val missingShapes = missingShips.toSortedSet().map { ShapeDirected(it, false) }
                .plus(missingShips.toSortedSet().map { ShapeDirected(it, true) })

            computeHeatMap(missingShapes)

            shoot()

            // If we are in a process of sinking a ship, figure out whether its sunk
            if (hitShipPlaces.isNotEmpty()) {
                sunkShipDetection(missingShapes)
            }
        }
    }
}

fun main() {
    Game().solve()
}
