package dev.atsushieno.ktmidi
import kotlinx.cinterop.*
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFStringRefVar
import platform.CoreFoundation.CFTypeRef
import platform.CoreMIDI.MIDIObjectGetIntegerProperty
import platform.CoreMIDI.MIDIObjectGetStringProperty
import platform.CoreMIDI.MIDIObjectRef
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSString
import platform.darwin.NSObject
import platform.darwin.OSStatus
import platform.darwin.SInt32Var

// a lot of ideas are taken from r4zzz4k/kmidi so far
@Suppress("CAST_NEVER_SUCCEEDS")
fun String.asNSString() = this as NSString
@Suppress("CAST_NEVER_SUCCEEDS")
fun NSString.asString() = this as String

// It seems they need to be invoked within memScoped {} ...
@OptIn(ExperimentalForeignApi::class)
inline fun <reified T: NSObject> CFTypeRef.toNSObjectReleased() = CFBridgingRelease(this) as T
@OptIn(ExperimentalForeignApi::class)
fun CFStringRef.toNSStringReleased() = toNSObjectReleased<NSString>()
@OptIn(ExperimentalForeignApi::class)
fun CFStringRef.getString() = toNSStringReleased().asString()

@OptIn(ExperimentalForeignApi::class)
inline fun <reified T: CFTypeRef> NSObject.toCFTypeRef() = CFBridgingRetain(this) as T
@OptIn(ExperimentalForeignApi::class)
fun NSString.toCFStringRef(): CFStringRef = this.toCFTypeRef()
@OptIn(ExperimentalForeignApi::class)
fun String.toCFStringRef() = asNSString().toCFStringRef()

internal fun checkStatus(func: ()->OSStatus) {
    val status = func()
    if (status != 0)
        throw CoreMidiException(status)
}

@OptIn(ExperimentalForeignApi::class)
internal fun getPropertyString(obj: MIDIObjectRef, property: CFStringRef?): String? = memScoped {
    val str = alloc<CFStringRefVar>()
    checkStatus { MIDIObjectGetStringProperty(obj, property, str.ptr) }
    if (str.value.rawValue == NativePtr.NULL)
        return null
    return str.value?.getString()
}

@OptIn(ExperimentalForeignApi::class)
internal fun getPropertyInt(obj: MIDIObjectRef, property: CFStringRef?): Int {
    memScoped {
        val i = cValue<SInt32Var>()
        checkStatus { MIDIObjectGetIntegerProperty(obj, property, i.ptr) }
        return i.ptr.pointed.value
    }
}
