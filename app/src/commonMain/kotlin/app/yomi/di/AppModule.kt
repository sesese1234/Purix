package app.yomi.di

import app.yomi.data.YomiRepository
import app.yomi.data.di.dataModule
import app.yomi.feature.goals.GoalsViewModel
import app.yomi.feature.insights.InsightsViewModel
import app.yomi.feature.planner.PlannerViewModel
import app.yomi.feature.settings.SettingsViewModel
import app.yomi.feature.today.TodayViewModel
import app.yomi.notification.Notifier
import app.yomi.notification.createPlatformNotifier
import okio.FileSystem
import okio.Path
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Screen models. Everything below them comes from [dataModule]. */
val viewModelModule: Module = module {
    viewModel { TodayViewModel(get<YomiRepository>()) }
    viewModel { PlannerViewModel(get<YomiRepository>()) }
    viewModel { GoalsViewModel(get<YomiRepository>()) }
    viewModel { InsightsViewModel(get<YomiRepository>()) }
    viewModel { SettingsViewModel(get<YomiRepository>()) }
}

val notificationModule: Module = module {
    single<Notifier> { createPlatformNotifier() }
}

/** The whole graph, in the order it has to be built. */
fun yomiModules(dataDirectory: Path, fileSystem: FileSystem): List<Module> = listOf(
    dataModule(dataDirectory, fileSystem),
    notificationModule,
    viewModelModule
)
