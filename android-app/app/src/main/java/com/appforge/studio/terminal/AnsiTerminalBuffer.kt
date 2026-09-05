package com.appforge.studio.terminal

import kotlin.math.max
import kotlin.math.min

internal data class AnsiTerminalStyle(
    val foregroundRgb: Int? = null,
    val backgroundRgb: Int? = null,
    val bold: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false
)

internal data class AnsiTerminalCell(
    val character: Char,
    val style: AnsiTerminalStyle
)

internal data class AnsiTerminalSnapshot(
    val lines: List<List<AnsiTerminalCell>>,
    val cursorLine: Int,
    val cursorColumn: Int,
    val cursorVisible: Boolean,
    val rows: Int,
    val columns: Int
) {
    fun plainText(): String =
        lines.joinToString("\n") { line ->
            line.joinToString("") {
                it.character.toString()
            }.trimEnd()
        }.trimEnd()
}

internal class AnsiTerminalBuffer(
    initialRows: Int = 24,
    initialColumns: Int = 80,
    private val maxScrollbackLines: Int = 1_000
) {
    private enum class ParserState {
        TEXT,
        ESCAPE,
        CSI,
        OSC,
        OSC_ESCAPE
    }

    private data class MutableCell(
        var character: Char = ' ',
        var style: AnsiTerminalStyle =
            AnsiTerminalStyle()
    )

    private var rows =
        initialRows.coerceIn(
            2,
            1_000
        )

    private var columns =
        initialColumns.coerceIn(
            10,
            2_000
        )

    private var screen =
        newScreen(
            rows,
            columns
        )

    private val scrollback =
        ArrayDeque<List<AnsiTerminalCell>>()

    private var cursorRow = 0
    private var cursorColumn = 0
    private var savedCursorRow = 0
    private var savedCursorColumn = 0
    private var cursorVisible = true
    private var currentStyle =
        AnsiTerminalStyle()

    private var parserState =
        ParserState.TEXT

    private val csiBuffer =
        StringBuilder()

    private var alternateScreen = false
    private var savedMainScreen:
        MutableList<MutableList<MutableCell>>? = null
    private var savedMainCursorRow = 0
    private var savedMainCursorColumn = 0
    private var savedMainCursorVisible = true

    fun reset() {
        screen =
            newScreen(
                rows,
                columns
            )
        scrollback.clear()
        cursorRow = 0
        cursorColumn = 0
        savedCursorRow = 0
        savedCursorColumn = 0
        cursorVisible = true
        currentStyle =
            AnsiTerminalStyle()
        parserState =
            ParserState.TEXT
        csiBuffer.clear()
        alternateScreen = false
        savedMainScreen = null
    }

    fun clear() {
        scrollback.clear()
        for (row in 0 until rows) {
            clearRange(row, 0, columns)
        }
        cursorRow = 0
        cursorColumn = 0
    }

    fun resize(
        newRows: Int,
        newColumns: Int
    ) {
        val targetRows =
            newRows.coerceIn(
                2,
                1_000
            )
        val targetColumns =
            newColumns.coerceIn(
                10,
                2_000
            )

        if (
            targetRows == rows &&
            targetColumns == columns
        ) {
            return
        }

        val replacement =
            newScreen(
                targetRows,
                targetColumns
            )

        val rowsToCopy =
            min(
                rows,
                targetRows
            )
        val columnsToCopy =
            min(
                columns,
                targetColumns
            )

        for (row in 0 until rowsToCopy) {
            for (
                column in
                0 until columnsToCopy
            ) {
                replacement[row][column] =
                    screen[row][column].copy()
            }
        }

        rows = targetRows
        columns = targetColumns
        screen = replacement
        cursorRow =
            cursorRow.coerceIn(
                0,
                rows - 1
            )
        cursorColumn =
            cursorColumn.coerceIn(
                0,
                columns - 1
            )
    }

    fun feed(
        text: String
    ) {
        text.forEach(::feedChar)
    }

    fun snapshot(
        includeScrollback: Boolean = true,
        maxHistoryLines: Int = Int.MAX_VALUE
    ): AnsiTerminalSnapshot {
        val history =
            if (includeScrollback && !alternateScreen) {
                scrollback
                    .toList()
                    .takeLast(
                        maxHistoryLines
                            .coerceAtLeast(0)
                    )
            } else {
                emptyList()
            }

        val current =
            screen.map { row ->
                row.map { cell ->
                    AnsiTerminalCell(
                        character =
                            cell.character,
                        style =
                            cell.style
                    )
                }
            }

        return AnsiTerminalSnapshot(
            lines = history + current,
            cursorLine =
                history.size +
                    cursorRow,
            cursorColumn =
                cursorColumn,
            cursorVisible =
                cursorVisible,
            rows = rows,
            columns = columns
        )
    }

    private fun feedChar(
        char: Char
    ) {
        when (parserState) {
            ParserState.TEXT ->
                when (char) {
                    '\u001b' ->
                        parserState =
                            ParserState.ESCAPE

                    '\r' ->
                        cursorColumn = 0

                    '\n' ->
                        lineFeed()

                    '\b' ->
                        cursorColumn =
                            max(
                                0,
                                cursorColumn - 1
                            )

                    '\t' -> {
                        val nextTab =
                            ((cursorColumn / 8) + 1) * 8

                        cursorColumn =
                            min(
                                columns - 1,
                                nextTab
                            )
                    }

                    '\u0007' -> Unit

                    '\u000c' ->
                        clear()

                    else ->
                        if (
                            char.code >= 0x20 &&
                            char != '\u007f'
                        ) {
                            putChar(char)
                        }
                }

            ParserState.ESCAPE ->
                when (char) {
                    '[' -> {
                        csiBuffer.clear()
                        parserState =
                            ParserState.CSI
                    }

                    ']' ->
                        parserState =
                            ParserState.OSC

                    '7' -> {
                        saveCursor()
                        parserState =
                            ParserState.TEXT
                    }

                    '8' -> {
                        restoreCursor()
                        parserState =
                            ParserState.TEXT
                    }

                    'D' -> {
                        lineFeed()
                        parserState =
                            ParserState.TEXT
                    }

                    'M' -> {
                        reverseIndex()
                        parserState =
                            ParserState.TEXT
                    }

                    'c' -> {
                        reset()
                    }

                    else ->
                        parserState =
                            ParserState.TEXT
                }

            ParserState.CSI -> {
                if (char.code in 0x40..0x7e) {
                    handleCsi(
                        csiBuffer.toString(),
                        char
                    )
                    csiBuffer.clear()
                    parserState =
                        ParserState.TEXT
                } else if (
                    csiBuffer.length <
                    MAX_ESCAPE_SEQUENCE
                ) {
                    csiBuffer.append(char)
                } else {
                    csiBuffer.clear()
                    parserState =
                        ParserState.TEXT
                }
            }

            ParserState.OSC ->
                when (char) {
                    '\u0007' ->
                        parserState =
                            ParserState.TEXT

                    '\u001b' ->
                        parserState =
                            ParserState.OSC_ESCAPE
                }

            ParserState.OSC_ESCAPE ->
                parserState =
                    if (char == '\\') {
                        ParserState.TEXT
                    } else {
                        ParserState.OSC
                    }
        }
    }

    private fun putChar(
        char: Char
    ) {
        if (cursorColumn >= columns) {
            cursorColumn = 0
            lineFeed()
        }

        screen[cursorRow][cursorColumn]
            .apply {
                character = char
                style = currentStyle
            }

        cursorColumn += 1

        if (cursorColumn >= columns) {
            cursorColumn = columns
        }
    }

    private fun lineFeed() {
        if (cursorRow >= rows - 1) {
            val removed =
                screen.removeAt(0)
                    .map { cell ->
                        AnsiTerminalCell(
                            character =
                                cell.character,
                            style =
                                cell.style
                        )
                    }

            if (!alternateScreen) {
                scrollback.add(
                    removed
                )

                while (
                    scrollback.size >
                    maxScrollbackLines
                ) {
                    scrollback.removeFirst()
                }
            }

            screen.add(
                MutableList(columns) {
                    MutableCell()
                }
            )
        } else {
            cursorRow += 1
        }
    }

    private fun reverseIndex() {
        if (cursorRow > 0) {
            cursorRow -= 1
            return
        }

        screen.add(
            0,
            MutableList(columns) {
                MutableCell()
            }
        )
        screen.removeAt(
            screen.lastIndex
        )
    }

    private fun handleCsi(
        rawParameters: String,
        finalChar: Char
    ) {
        val privateMode =
            rawParameters.startsWith('?')

        val parameterText =
            rawParameters
                .removePrefix("?")
                .removePrefix(">")

        val parameters =
            if (parameterText.isBlank()) {
                emptyList()
            } else {
                parameterText
                    .split(';')
                    .map {
                        it.toIntOrNull()
                    }
            }

        fun parameter(
            index: Int,
            defaultValue: Int
        ): Int =
            parameters
                .getOrNull(index)
                ?: defaultValue

        when (finalChar) {
            'A' ->
                cursorRow =
                    max(
                        0,
                        cursorRow -
                            max(
                                1,
                                parameter(0, 1)
                            )
                    )

            'B' ->
                cursorRow =
                    min(
                        rows - 1,
                        cursorRow +
                            max(
                                1,
                                parameter(0, 1)
                            )
                    )

            'C' ->
                cursorColumn =
                    min(
                        columns - 1,
                        cursorColumn +
                            max(
                                1,
                                parameter(0, 1)
                            )
                    )

            'D' ->
                cursorColumn =
                    max(
                        0,
                        cursorColumn -
                            max(
                                1,
                                parameter(0, 1)
                            )
                    )

            'E' -> {
                cursorRow =
                    min(
                        rows - 1,
                        cursorRow +
                            max(
                                1,
                                parameter(0, 1)
                            )
                    )
                cursorColumn = 0
            }

            'F' -> {
                cursorRow =
                    max(
                        0,
                        cursorRow -
                            max(
                                1,
                                parameter(0, 1)
                            )
                    )
                cursorColumn = 0
            }

            'G' ->
                cursorColumn =
                    (parameter(0, 1) - 1)
                        .coerceIn(
                            0,
                            columns - 1
                        )

            'H',
            'f' -> {
                cursorRow =
                    (parameter(0, 1) - 1)
                        .coerceIn(
                            0,
                            rows - 1
                        )

                cursorColumn =
                    (parameter(1, 1) - 1)
                        .coerceIn(
                            0,
                            columns - 1
                        )
            }

            'J' ->
                eraseDisplay(
                    parameter(0, 0)
                )

            'K' ->
                eraseLine(
                    parameter(0, 0)
                )

            'm' ->
                applySgr(
                    if (parameters.isEmpty()) {
                        listOf(0)
                    } else {
                        parameters.map {
                            it ?: 0
                        }
                    }
                )

            's' ->
                saveCursor()

            'u' ->
                restoreCursor()

            'h' ->
                if (privateMode) {
                    parameters
                        .filterNotNull()
                        .forEach { mode ->
                            when (mode) {
                                25 -> cursorVisible = true
                                47, 1047, 1049 ->
                                    enterAlternateScreen()
                            }
                        }
                }

            'l' ->
                if (privateMode) {
                    parameters
                        .filterNotNull()
                        .forEach { mode ->
                            when (mode) {
                                25 -> cursorVisible = false
                                47, 1047, 1049 ->
                                    leaveAlternateScreen()
                            }
                        }
                }
        }
    }

    private fun enterAlternateScreen() {
        if (alternateScreen) return

        savedMainScreen =
            screen.map { row ->
                row.map { it.copy() }
                    .toMutableList()
            }.toMutableList()
        savedMainCursorRow = cursorRow
        savedMainCursorColumn = cursorColumn
        savedMainCursorVisible = cursorVisible

        alternateScreen = true
        screen = newScreen(rows, columns)
        cursorRow = 0
        cursorColumn = 0
    }

    private fun leaveAlternateScreen() {
        if (!alternateScreen) return

        savedMainScreen?.let { restored ->
            screen = restored
        }
        cursorRow =
            savedMainCursorRow.coerceIn(0, rows - 1)
        cursorColumn =
            savedMainCursorColumn.coerceIn(0, columns - 1)
        cursorVisible = savedMainCursorVisible
        savedMainScreen = null
        alternateScreen = false
    }

    private fun eraseDisplay(
        mode: Int
    ) {
        when (mode) {
            0 -> {
                clearRange(
                    cursorRow,
                    cursorColumn,
                    columns
                )

                for (
                    row in
                    cursorRow + 1 until rows
                ) {
                    clearRange(
                        row,
                        0,
                        columns
                    )
                }
            }

            1 -> {
                for (
                    row in
                    0 until cursorRow
                ) {
                    clearRange(
                        row,
                        0,
                        columns
                    )
                }

                clearRange(
                    cursorRow,
                    0,
                    cursorColumn + 1
                )
            }

            2 ->
                clearScreen()

            3 ->
                clear()
        }
    }

    private fun eraseLine(
        mode: Int
    ) {
        when (mode) {
            0 ->
                clearRange(
                    cursorRow,
                    cursorColumn,
                    columns
                )

            1 ->
                clearRange(
                    cursorRow,
                    0,
                    cursorColumn + 1
                )

            2 ->
                clearRange(
                    cursorRow,
                    0,
                    columns
                )
        }
    }

    private fun clearScreen() {
        for (row in 0 until rows) {
            clearRange(
                row,
                0,
                columns
            )
        }
        cursorRow = 0
        cursorColumn = 0
    }

    private fun clearRange(
        row: Int,
        fromColumn: Int,
        toColumnExclusive: Int
    ) {
        val start =
            fromColumn.coerceIn(
                0,
                columns
            )
        val end =
            toColumnExclusive.coerceIn(
                start,
                columns
            )

        for (column in start until end) {
            screen[row][column] =
                MutableCell()
        }
    }

    private fun saveCursor() {
        savedCursorRow = cursorRow
        savedCursorColumn =
            min(
                cursorColumn,
                columns - 1
            )
    }

    private fun restoreCursor() {
        cursorRow =
            savedCursorRow.coerceIn(
                0,
                rows - 1
            )
        cursorColumn =
            savedCursorColumn.coerceIn(
                0,
                columns - 1
            )
    }

    private fun applySgr(
        values: List<Int>
    ) {
        var index = 0

        while (index < values.size) {
            when (val code = values[index]) {
                0 ->
                    currentStyle =
                        AnsiTerminalStyle()

                1 ->
                    currentStyle =
                        currentStyle.copy(
                            bold = true
                        )

                4 ->
                    currentStyle =
                        currentStyle.copy(
                            underline = true
                        )

                7 ->
                    currentStyle =
                        currentStyle.copy(
                            inverse = true
                        )

                22 ->
                    currentStyle =
                        currentStyle.copy(
                            bold = false
                        )

                24 ->
                    currentStyle =
                        currentStyle.copy(
                            underline = false
                        )

                27 ->
                    currentStyle =
                        currentStyle.copy(
                            inverse = false
                        )

                39 ->
                    currentStyle =
                        currentStyle.copy(
                            foregroundRgb = null
                        )

                49 ->
                    currentStyle =
                        currentStyle.copy(
                            backgroundRgb = null
                        )

                in 30..37 ->
                    currentStyle =
                        currentStyle.copy(
                            foregroundRgb =
                                ansi16(
                                    code - 30
                                )
                        )

                in 40..47 ->
                    currentStyle =
                        currentStyle.copy(
                            backgroundRgb =
                                ansi16(
                                    code - 40
                                )
                        )

                in 90..97 ->
                    currentStyle =
                        currentStyle.copy(
                            foregroundRgb =
                                ansi16(
                                    code - 90 + 8
                                )
                        )

                in 100..107 ->
                    currentStyle =
                        currentStyle.copy(
                            backgroundRgb =
                                ansi16(
                                    code - 100 + 8
                                )
                        )

                38,
                48 -> {
                    val foreground =
                        code == 38

                    if (
                        index + 2 < values.size &&
                        values[index + 1] == 5
                    ) {
                        val rgb =
                            ansi256(
                                values[index + 2]
                            )

                        currentStyle =
                            if (foreground) {
                                currentStyle.copy(
                                    foregroundRgb = rgb
                                )
                            } else {
                                currentStyle.copy(
                                    backgroundRgb = rgb
                                )
                            }

                        index += 2
                    } else if (
                        index + 4 < values.size &&
                        values[index + 1] == 2
                    ) {
                        val red =
                            values[index + 2]
                                .coerceIn(0, 255)
                        val green =
                            values[index + 3]
                                .coerceIn(0, 255)
                        val blue =
                            values[index + 4]
                                .coerceIn(0, 255)

                        val rgb =
                            (red shl 16) or
                                (green shl 8) or
                                blue

                        currentStyle =
                            if (foreground) {
                                currentStyle.copy(
                                    foregroundRgb = rgb
                                )
                            } else {
                                currentStyle.copy(
                                    backgroundRgb = rgb
                                )
                            }

                        index += 4
                    }
                }
            }

            index += 1
        }
    }

    private fun ansi16(
        index: Int
    ): Int =
        ANSI_16[
            index.coerceIn(
                0,
                ANSI_16.lastIndex
            )
        ]

    private fun ansi256(
        index: Int
    ): Int {
        val safe =
            index.coerceIn(
                0,
                255
            )

        if (safe < 16) {
            return ansi16(safe)
        }

        if (safe >= 232) {
            val level =
                8 +
                    (safe - 232) * 10

            return (level shl 16) or
                (level shl 8) or
                level
        }

        val cube = safe - 16
        val redIndex = cube / 36
        val greenIndex =
            (cube % 36) / 6
        val blueIndex = cube % 6

        fun channel(value: Int): Int =
            if (value == 0) {
                0
            } else {
                55 + value * 40
            }

        return (channel(redIndex) shl 16) or
            (channel(greenIndex) shl 8) or
            channel(blueIndex)
    }

    private fun newScreen(
        rowCount: Int,
        columnCount: Int
    ): MutableList<MutableList<MutableCell>> =
        MutableList(rowCount) {
            MutableList(columnCount) {
                MutableCell()
            }
        }

    companion object {
        private const val
            MAX_ESCAPE_SEQUENCE =
                128

        private val ANSI_16 =
            intArrayOf(
                0x000000,
                0xCD3131,
                0x0DBC79,
                0xE5E510,
                0x2472C8,
                0xBC3FBC,
                0x11A8CD,
                0xE5E5E5,
                0x666666,
                0xF14C4C,
                0x23D18B,
                0xF5F543,
                0x3B8EEA,
                0xD670D6,
                0x29B8DB,
                0xFFFFFF
            )
    }
}
