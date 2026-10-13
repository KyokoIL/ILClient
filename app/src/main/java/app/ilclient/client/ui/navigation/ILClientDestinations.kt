package app.ilclient.client.ui.navigation

import kotlinx.serialization.Serializable

sealed interface ILClientRoute {
    @Serializable data object Home : ILClientRoute
    @Serializable data object Configs : ILClientRoute
    @Serializable data object Rules : ILClientRoute
    @Serializable data object Settings : ILClientRoute
    @Serializable data object Diagnostics : ILClientRoute
}