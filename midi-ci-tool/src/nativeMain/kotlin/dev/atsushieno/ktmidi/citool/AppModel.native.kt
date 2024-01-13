package dev.atsushieno.ktmidi.citool

import com.arkivanov.essenty.instancekeeper.InstanceKeeperDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher

actual fun initializeAppModel(context: Any) {
    AppModel = CIToolRepository(LifecycleRegistry(), StateKeeperDispatcher(), InstanceKeeperDispatcher())
}