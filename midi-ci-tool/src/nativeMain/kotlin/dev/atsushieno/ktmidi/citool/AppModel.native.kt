package dev.atsushieno.ktmidi.citool

import com.arkivanov.essenty.instancekeeper.InstanceKeeperDispatcher

actual fun initializeAppModel(context: Any) {
    AppModel = CIToolRepository(InstanceKeeperDispatcher())
}