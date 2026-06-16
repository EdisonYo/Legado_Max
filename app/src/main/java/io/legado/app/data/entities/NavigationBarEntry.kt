package io.legado.app.data.entities

data class NavigationBarEntry(
    val config: NavigationBarConfig,
    val source: Source,
    val dirName: String
)

enum class Source {
    BUILTIN,
    LOCAL
}