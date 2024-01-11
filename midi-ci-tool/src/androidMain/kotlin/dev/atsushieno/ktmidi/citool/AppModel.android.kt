package dev.atsushieno.ktmidi.citool

import androidx.lifecycle.ViewModelStoreOwner
import com.arkivanov.essenty.instancekeeper.instanceKeeper

actual fun initializeAppModel(context: Any) {
    AppModel = CIToolRepository((context as ViewModelStoreOwner).instanceKeeper())
}