package dev.atsushieno.ktmidi
import kotlinx.cinterop.*
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFStringRefVar
import platform.CoreFoundation.CFTypeRef
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSString
import platform.darwin.NSObject

// a lot of ideas are taken from r4zzz4k/kmidi so far
@Suppress("CAST_NEVER_SUCCEEDS")
fun String.asNSString() = this as NSString
@Suppress("CAST_NEVER_SUCCEEDS")
fun NSString.asString() = this as String

// It seems they need to be invoked within memScoped {} ...
@OptIn(ExperimentalForeignApi::class)
inline fun <reified T: NSObject> CFTypeRef.toNSObjectReleased() = CFBridgingRelease(this) as T
@OptIn(ExperimentalForeignApi::class)
inline fun <reified T: NSObject> CFTypeRef.toNSObjectRetained() = CFBridgingRetain(this) as T
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

@OptIn(ExperimentalForeignApi::class)
inline fun <reified T: CVariable> viaPtrVar(block: (ptr: CPointer<T>) -> Unit): T = memScoped {
    val result = alloc<T>()
    block(result.ptr)
    return result
}
@OptIn(ExperimentalForeignApi::class)
inline fun <T : CPointer<*>> viaPtr(block: (CPointer<CPointerVarOf<T>>) -> Unit): T? = viaPtrVar(block).value
