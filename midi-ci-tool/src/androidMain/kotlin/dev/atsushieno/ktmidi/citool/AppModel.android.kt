package dev.atsushieno.ktmidi.citool

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.arkivanov.essenty.instancekeeper.instanceKeeper
import com.arkivanov.essenty.lifecycle.essentyLifecycle
import com.arkivanov.essenty.statekeeper.stateKeeper

actual fun initializeAppModel(context: Any) {
    AppModel = CIToolRepository(
        (context as LifecycleOwner).essentyLifecycle(),
        (context as SavedStateRegistryOwner).stateKeeper(),
        (context as ViewModelStoreOwner).instanceKeeper()
    )
}