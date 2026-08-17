package app.yomi.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The app's icon set, built from the standard 24dp Material vector paths that
 * ship as XML drawables with the Android SDK.
 *
 * They are declared here as path data rather than pulled in as a dependency for
 * three reasons: the icon library's multiplatform builds lag several Compose
 * versions behind, an app needs twenty icons rather than two thousand, and the
 * same definitions then render identically on Android and on desktop — an
 * Android `res/drawable` would only work on one of them.
 */
object YomiIcons {

    // Navigation
    val Today: ImageVector by lazy { icon("Today", TODAY) }
    val Planner: ImageVector by lazy { icon("Planner", DATE_RANGE) }
    val Goals: ImageVector by lazy { icon("Goals", FLAG) }
    val Insights: ImageVector by lazy { icon("Insights", BAR_CHART) }
    val Settings: ImageVector by lazy { icon("Settings", SETTINGS) }

    // Status
    val Check: ImageVector by lazy { icon("Check", CHECK) }
    val CheckCircle: ImageVector by lazy { icon("CheckCircle", CHECK_CIRCLE) }
    val Close: ImageVector by lazy { icon("Close", CLOSE) }
    val Remove: ImageVector by lazy { icon("Remove", REMOVE) }
    val PartlyDone: ImageVector by lazy { icon("PartlyDone", DONUT_LARGE) }
    val Play: ImageVector by lazy { icon("Play", PLAY_ARROW) }
    val Pending: ImageVector by lazy { icon("Pending", CIRCLE_OUTLINE) }

    // Actions
    val Add: ImageVector by lazy { icon("Add", ADD) }
    val Delete: ImageVector by lazy { icon("Delete", DELETE) }
    val Edit: ImageVector by lazy { icon("Edit", EDIT) }
    val More: ImageVector by lazy { icon("More", MORE_HORIZ) }
    val Forward: ImageVector by lazy { icon("Forward", ARROW_FORWARD) }
    val ChevronStart: ImageVector by lazy { icon("ChevronStart", CHEVRON_START) }
    val ChevronEnd: ImageVector by lazy { icon("ChevronEnd", CHEVRON_END) }
    val Copy: ImageVector by lazy { icon("Copy", CONTENT_COPY) }
    val Template: ImageVector by lazy { icon("Template", BOOKMARK) }
    val Archive: ImageVector by lazy { icon("Archive", ARCHIVE) }
    val Restore: ImageVector by lazy { icon("Restore", RESTORE) }

    // Meaning
    val Sun: ImageVector by lazy { icon("Sun", WB_SUNNY) }
    val Moon: ImageVector by lazy { icon("Moon", BEDTIME) }
    val Streak: ImageVector by lazy { icon("Streak", WHATSHOT) }
    val Star: ImageVector by lazy { icon("Star", STAR) }
    val Schedule: ImageVector by lazy { icon("Schedule", SCHEDULE, SCHEDULE_HAND) }
    val Label: ImageVector by lazy { icon("Label", LABEL) }
    val Warning: ImageVector by lazy { icon("Warning", ERROR_OUTLINE) }
    val Inbox: ImageVector by lazy { icon("Inbox", INBOX) }
    val Notification: ImageVector by lazy { icon("Notification", NOTIFICATIONS) }

    /**
     * Builds a 24dp icon from one or more path strings, exactly as the vector
     * drawable loader does with `android:pathData`.
     */
    private fun icon(name: String, vararg pathData: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = SIZE.dp,
            defaultHeight = SIZE.dp,
            viewportWidth = SIZE,
            viewportHeight = SIZE
        )
        for (data in pathData) {
            builder.addPath(
                pathData = PathParser().pathStringToNodes(data),
                fill = SolidColor(Color.Black)
            )
        }
        return builder.build()
    }

    private const val SIZE = 24f

    // --- Path data, 24x24, from the Material vector drawable set ------------

    private const val TODAY =
        "M17,12h-5v5h5v-5zM16,1v2H8V1H6v2H5c-1.11,0 -1.99,0.9 -1.99,2L3,19c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 " +
            "2,-2V5c0,-1.1 -0.9,-2 -2,-2h-1V1h-2zM19,19H5V8h14v11z"

    private const val DATE_RANGE =
        "M9,11H7v2h2v-2zM13,11h-2v2h2v-2zM17,11h-2v2h2v-2zM19,4h-1V2h-2v2H8V2H6v2H5c-1.11,0 -1.99,0.9 " +
            "-1.99,2L3,20c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM19,20H5V9h14v11z"

    private const val FLAG = "M14.4,6L14,4H5v17h2v-7h5.6l0.4,2h7V6z"

    private const val BAR_CHART = "M5,9.2h3V19H5zM10.6,5h2.8v14h-2.8zM16.2,13H19v6h-2.8z"

    private const val SETTINGS =
        "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 " +
            "0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 " +
            "-1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 " +
            "-0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87" +
            "C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58" +
            "c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 " +
            "1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 " +
            "0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 " +
            "0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 " +
            "-3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z"

    private const val CHECK = "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z"

    private const val CHECK_CIRCLE =
        "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zm-2,15l-5,-5 1.41,-1.41L10,14.17" +
            "l7.59,-7.59L19,8l-9,9z"

    private const val CLOSE =
        "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z"

    private const val REMOVE = "M19,13H5v-2h14v2z"

    private const val DONUT_LARGE =
        "M13,5.08c3.06,0.44 5.48,2.86 5.92,5.92h3.03c-0.47,-4.72 -4.23,-8.48 -8.95,-8.95v3.03zM18.92,13" +
            "c-0.44,3.06 -2.86,5.48 -5.92,5.92v3.03c4.72,-0.47 8.48,-4.23 8.95,-8.95h-3.03zM11,18.92" +
            "c-3.39,-0.49 -6,-3.4 -6,-6.92s2.61,-6.43 6,-6.92V2.05c-5.05,0.5 -9,4.76 -9,9.95c0,5.19 3.95,9.45 9,9.95v-3.03z"

    private const val PLAY_ARROW = "M8,5v14l11,-7z"

    private const val CIRCLE_OUTLINE =
        "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8" +
            "s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z"

    private const val ADD = "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"

    private const val DELETE =
        "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z"

    private const val EDIT =
        "M20.41,4.94l-1.35,-1.35c-0.78,-0.78 -2.05,-0.78 -2.83,0l0,0L3,16.82V21h4.18L20.41,7.77" +
            "C21.2,6.99 21.2,5.72 20.41,4.94zM6.41,19.06L5,19v-1.36l9.82,-9.82l1.41,1.41L6.41,19.06z"

    private const val MORE_HORIZ =
        "M6,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM18,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 " +
            "2,-0.9 2,-2 -0.9,-2 -2,-2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z"

    private const val ARROW_FORWARD = "M12,4l-1.41,1.41L16.17,11H4v2h12.17l-5.58,5.59L12,20l8,-8z"

    private const val CHEVRON_START = "M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z"

    private const val CHEVRON_END = "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z"

    private const val CONTENT_COPY =
        "M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1zM19,5H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 " +
            "2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z"

    private const val BOOKMARK = "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z"

    private const val ARCHIVE =
        "M20.54,5.23l-1.39,-1.68C18.88,3.21 18.47,3 18,3H6c-0.47,0 -0.88,0.21 -1.16,0.55L3.46,5.23" +
            "C3.17,5.57 3,6.02 3,6.5V19c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V6.5c0,-0.48 -0.17,-0.93 " +
            "-0.46,-1.27zM12,17.5L6.5,12H10v-2h4v2h3.5L12,17.5zM5.12,5l0.81,-1h12l0.94,1H5.12z"

    private const val RESTORE =
        "M13,3c-4.97,0 -9,4.03 -9,9H1l3.89,3.89 0.07,0.14L9,12H6c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7" +
            "c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 " +
            "-9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08V8H12z"

    private const val WB_SUNNY =
        "M6.76,4.84l-1.8,-1.79 -1.41,1.41 1.79,1.79 1.42,-1.41zM4,10.5H1v2h3v-2zM13,0.55h-2V3.5h2V0.55z" +
            "M20.45,4.46l-1.41,-1.41 -1.79,1.79 1.41,1.41 1.79,-1.79zM17.24,18.16l1.79,1.8 1.41,-1.41 " +
            "-1.8,-1.79 -1.4,1.4zM20,10.5v2h3v-2h-3zM12,5.5c-3.31,0 -6,2.69 -6,6s2.69,6 6,6 6,-2.69 6,-6 " +
            "-2.69,-6 -6,-6zM11,22.45h2V19.5h-2v2.95zM3.55,18.54l1.41,1.41 1.79,-1.8 -1.41,-1.41 -1.79,1.8z"

    private const val BEDTIME =
        "M12.34,2.02C6.59,1.82 2,6.42 2,12c0,5.52 4.48,10 10,10 3.71,0 6.93,-2.02 8.66,-5.02 -7.51,-0.25 " +
            "-12.09,-8.43 -8.32,-14.96z"

    private const val WHATSHOT =
        "M13.5,0.67s0.74,2.65 0.74,4.8c0,2.06 -1.35,3.73 -3.41,3.73 -2.07,0 -3.63,-1.67 -3.63,-3.73l0.03,-0.36" +
            "C5.21,7.51 4,10.62 4,14c0,4.42 3.58,8 8,8s8,-3.58 8,-8C20,8.61 17.41,3.8 13.5,0.67zM11.71,19" +
            "c-1.78,0 -3.22,-1.4 -3.22,-3.14 0,-1.62 1.05,-2.76 2.81,-3.12 1.77,-0.36 3.6,-1.21 4.62,-2.58 " +
            "0.39,1.29 0.59,2.65 0.59,4.04 0,2.65 -2.15,4.8 -4.8,4.8z"

    private const val STAR =
        "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z"

    private const val SCHEDULE =
        "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20" +
            "c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z"

    private const val SCHEDULE_HAND = "M12.5,7H11v6l5.25,3.1 0.75,-1.23 -4.5,-2.67z"

    private const val LABEL =
        "M17.63,5.84C17.27,5.33 16.67,5 16,5L5,5.01C3.9,5.01 3,5.9 3,7v10c0,1.1 0.9,1.99 2,1.99L16,19" +
            "c0.67,0 1.27,-0.33 1.63,-0.84L22,12l-4.37,-6.16z"

    private const val ERROR_OUTLINE =
        "M11,15h2v2h-2zM11,7h2v6h-2zM11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12" +
            "S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z"

    private const val INBOX =
        "M19,3H4.99c-1.11,0 -1.98,0.89 -1.98,2L3,19c0,1.1 0.88,2 1.99,2H19c1.1,0 2,-0.9 2,-2V5c0,-1.11 " +
            "-0.9,-2 -2,-2zM19,15h-4c0,1.66 -1.35,3 -3,3s-3,-1.34 -3,-3H4.99V5H19v10z"

    private const val NOTIFICATIONS =
        "M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.9,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 " +
            "-0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z"
}
