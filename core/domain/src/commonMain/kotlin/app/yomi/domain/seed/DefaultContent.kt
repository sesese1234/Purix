package app.yomi.domain.seed

import app.yomi.model.AppLanguage
import app.yomi.model.Catalog
import app.yomi.model.Category
import app.yomi.model.EnergyLevel
import app.yomi.model.Goal
import app.yomi.model.GoalLink
import app.yomi.model.GoalPeriod
import app.yomi.model.GoalType
import app.yomi.model.Priority
import app.yomi.model.Recurrence
import app.yomi.model.RecurrenceRule
import app.yomi.model.SubtaskDefinition
import app.yomi.model.SubtaskRule
import app.yomi.model.TaskDefinition
import app.yomi.model.TaskScoring
import app.yomi.model.TaskTiming
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * The starter library a brand-new install comes with: enough structure that the
 * Today screen is immediately meaningful, little enough that it is easy to
 * throw away. Available in both supported languages.
 */
object DefaultContent {

    fun catalog(today: LocalDate, language: AppLanguage): Catalog {
        val t = if (language == AppLanguage.Hebrew) Hebrew else English

        val morning = Category("cat-morning", t.morning, "", 0xFFFFB4A2, 1.0, 0)
        val work = Category("cat-work", t.work, "", 0xFF7C6BF2, 1.2, 1)
        val body = Category("cat-body", t.body, "", 0xFF4CC9A7, 1.0, 2)
        val mind = Category("cat-mind", t.mind, "", 0xFFFFC857, 1.0, 3)
        val home = Category("cat-home", t.home, "", 0xFF8ECAE6, 0.8, 4)
        val evening = Category("cat-evening", t.evening, "", 0xFFB39DDB, 1.0, 5)

        val readingGoal = Goal(
            id = "goal-reading",
            title = t.readingGoal,
            emoji = "",
            type = GoalType.Quantity,
            target = 150.0,
            unit = t.pages,
            period = GoalPeriod.Week,
            startDate = today,
            colorArgb = 0xFFFFC857,
            linkedTaskIds = setOf("task-read"),
            minimumDailyProgress = 15.0,
            order = 0
        )

        val movementGoal = Goal(
            id = "goal-movement",
            title = t.movementGoal,
            emoji = "",
            type = GoalType.Count,
            target = 4.0,
            unit = t.sessions,
            period = GoalPeriod.Week,
            startDate = today,
            colorArgb = 0xFF4CC9A7,
            linkedCategoryIds = setOf(body.id),
            order = 1
        )

        val scoreGoal = Goal(
            id = "goal-score",
            title = t.scoreGoal,
            emoji = "",
            type = GoalType.AverageScore,
            target = 85.0,
            period = GoalPeriod.Week,
            startDate = today,
            colorArgb = 0xFF7C6BF2,
            order = 2
        )

        val everyDay = RecurrenceRule(Recurrence.Daily(), today)
        val weekdays = RecurrenceRule(
            Recurrence.Weekly(
                setOf(
                    DayOfWeek.SUNDAY,
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY
                )
            ),
            today
        )
        val thrice = RecurrenceRule(
            Recurrence.Weekly(setOf(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)),
            today
        )

        val tasks = listOf(
            TaskDefinition(
                id = "task-wake",
                title = t.wakeRoutine,
                emoji = "",
                categoryId = morning.id,
                priority = Priority.High,
                energy = EnergyLevel.Light,
                timing = TaskTiming.Fixed(LocalTime(7, 0), LocalTime(7, 30)),
                schedule = everyDay,
                subtasks = listOf(
                    SubtaskDefinition("st-water", t.drinkWater, 1),
                    SubtaskDefinition("st-stretch", t.stretch, 1),
                    SubtaskDefinition("st-bed", t.makeBed, 1)
                ),
                scoring = TaskScoring(points = 14, subtaskRule = SubtaskRule.Weighted),
                order = 0
            ),
            TaskDefinition(
                id = "task-plan",
                title = t.planDay,
                emoji = "",
                categoryId = work.id,
                priority = Priority.Critical,
                timing = TaskTiming.Fixed(LocalTime(8, 30), LocalTime(8, 45)),
                schedule = weekdays,
                scoring = TaskScoring(points = 12),
                order = 1
            ),
            TaskDefinition(
                id = "task-deep",
                title = t.deepWork,
                emoji = "",
                categoryId = work.id,
                priority = Priority.Critical,
                energy = EnergyLevel.Deep,
                timing = TaskTiming.Fixed(LocalTime(9, 0), LocalTime(11, 30)),
                schedule = weekdays,
                subtasks = listOf(
                    SubtaskDefinition("st-focus1", t.block1, 1),
                    SubtaskDefinition("st-focus2", t.block2, 1)
                ),
                scoring = TaskScoring(points = 25, subtaskRule = SubtaskRule.Weighted),
                order = 2
            ),
            TaskDefinition(
                id = "task-move",
                title = t.workout,
                emoji = "",
                categoryId = body.id,
                priority = Priority.High,
                energy = EnergyLevel.Deep,
                timing = TaskTiming.Window(LocalTime(16, 0), LocalTime(20, 0), 45),
                schedule = thrice,
                scoring = TaskScoring(points = 18),
                order = 3
            ),
            TaskDefinition(
                id = "task-read",
                title = t.read,
                emoji = "",
                categoryId = mind.id,
                energy = EnergyLevel.Light,
                timing = TaskTiming.Deadline(LocalTime(22, 30)),
                schedule = everyDay,
                scoring = TaskScoring(
                    points = 10,
                    quantityTarget = 20.0,
                    quantityUnit = t.pages
                ),
                goalLinks = listOf(GoalLink(readingGoal.id, app.yomi.model.GoalContribution.PerQuantity)),
                order = 4
            ),
            TaskDefinition(
                id = "task-tidy",
                title = t.tidy,
                emoji = "",
                categoryId = home.id,
                priority = Priority.Low,
                timing = TaskTiming.Anytime,
                schedule = everyDay,
                scoring = TaskScoring(points = 6),
                order = 5
            ),
            TaskDefinition(
                id = "task-winddown",
                title = t.windDown,
                emoji = "",
                categoryId = evening.id,
                timing = TaskTiming.Fixed(LocalTime(22, 30), LocalTime(23, 0)),
                schedule = everyDay,
                subtasks = listOf(
                    SubtaskDefinition("st-screens", t.screensOff, 1),
                    SubtaskDefinition("st-journal", t.journal, 1)
                ),
                scoring = TaskScoring(points = 10, subtaskRule = SubtaskRule.Weighted),
                order = 6
            )
        )

        return Catalog(
            tasks = tasks,
            categories = listOf(morning, work, body, mind, home, evening),
            goals = listOf(readingGoal, movementGoal, scoreGoal)
        )
    }

    private interface Strings {
        val morning: String
        val work: String
        val body: String
        val mind: String
        val home: String
        val evening: String
        val wakeRoutine: String
        val planDay: String
        val deepWork: String
        val workout: String
        val read: String
        val tidy: String
        val windDown: String
        val drinkWater: String
        val stretch: String
        val makeBed: String
        val block1: String
        val block2: String
        val screensOff: String
        val journal: String
        val readingGoal: String
        val movementGoal: String
        val scoreGoal: String
        val pages: String
        val sessions: String
    }

    private object English : Strings {
        override val morning = "Morning"
        override val work = "Work"
        override val body = "Body"
        override val mind = "Mind"
        override val home = "Home"
        override val evening = "Evening"
        override val wakeRoutine = "Morning routine"
        override val planDay = "Plan the day"
        override val deepWork = "Deep work"
        override val workout = "Workout"
        override val read = "Read"
        override val tidy = "Tidy up"
        override val windDown = "Wind down"
        override val drinkWater = "Drink water"
        override val stretch = "Stretch"
        override val makeBed = "Make the bed"
        override val block1 = "First block"
        override val block2 = "Second block"
        override val screensOff = "Screens off"
        override val journal = "Journal"
        override val readingGoal = "Read 150 pages"
        override val movementGoal = "Move 4 times"
        override val scoreGoal = "Average 85"
        override val pages = "pages"
        override val sessions = "sessions"
    }

    private object Hebrew : Strings {
        override val morning = "בוקר"
        override val work = "עבודה"
        override val body = "גוף"
        override val mind = "ראש"
        override val home = "בית"
        override val evening = "ערב"
        override val wakeRoutine = "שגרת בוקר"
        override val planDay = "תכנון היום"
        override val deepWork = "עבודה עמוקה"
        override val workout = "אימון"
        override val read = "קריאה"
        override val tidy = "סידור הבית"
        override val windDown = "סגירת יום"
        override val drinkWater = "לשתות מים"
        override val stretch = "מתיחות"
        override val makeBed = "לסדר את המיטה"
        override val block1 = "בלוק ראשון"
        override val block2 = "בלוק שני"
        override val screensOff = "לכבות מסכים"
        override val journal = "יומן"
        override val readingGoal = "לקרוא 150 עמודים"
        override val movementGoal = "4 אימונים"
        override val scoreGoal = "ממוצע 85"
        override val pages = "עמודים"
        override val sessions = "אימונים"
    }
}
