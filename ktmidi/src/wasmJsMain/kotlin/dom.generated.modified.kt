// It was generated by dukat from dom.generated.d.ts at TypeScript dom library,
// then extracted only required bits, and added some typealias and made some edits.
import org.khronos.webgl.Uint8Array
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener
import org.w3c.dom.events.EventTarget
import kotlin.js.Promise

typealias DOMHighResTimeStamp = Long
typealias AddEventListenerOptions = JsAny?
typealias EventListenerObject = JsAny
typealias EventListenerOptions = JsAny?

internal external interface MIDIAccessEventMap {
    var statechange: Event
}

@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
internal abstract external class MIDIAccess : EventTarget {
    var inputs: MIDIInputMap
    var onstatechange: ((self: MIDIAccess, ev: Event) -> JsAny)?
    var outputs: MIDIOutputMap
    var sysexEnabled: Boolean
    fun <K : String> addEventListener(type: K, listener: (self: MIDIAccess, ev: JsAny) -> JsAny, options: Boolean = definedExternally)
    fun <K : String> addEventListener(type: K, listener: (self: MIDIAccess, ev: JsAny) -> JsAny)
    fun <K : String> addEventListener(type: K, listener: (self: MIDIAccess, ev: JsAny) -> JsAny, options: AddEventListenerOptions = definedExternally)
    fun addEventListener(type: String, listener: EventListener, options: Boolean = definedExternally)
    fun addEventListener(type: String, listener: EventListener)
    fun addEventListener(type: String, listener: EventListener, options: AddEventListenerOptions = definedExternally)
    fun addEventListener(type: String, listener: EventListenerObject, options: Boolean = definedExternally)
    fun addEventListener(type: String, listener: EventListenerObject)
    fun addEventListener(type: String, listener: EventListenerObject, options: AddEventListenerOptions = definedExternally)
    fun <K : String> removeEventListener(type: K, listener: (self: MIDIAccess, ev: JsAny) -> JsAny, options: Boolean = definedExternally)
    fun <K : String> removeEventListener(type: K, listener: (self: MIDIAccess, ev: JsAny) -> JsAny)
    fun <K : String> removeEventListener(type: K, listener: (self: MIDIAccess, ev: JsAny) -> JsAny, options: EventListenerOptions = definedExternally)
    fun removeEventListener(type: String, listener: EventListener, options: Boolean = definedExternally)
    fun removeEventListener(type: String, listener: EventListener)
    fun removeEventListener(type: String, listener: EventListener, options: EventListenerOptions = definedExternally)
    fun removeEventListener(type: String, listener: EventListenerObject, options: Boolean = definedExternally)
    fun removeEventListener(type: String, listener: EventListenerObject)
    fun removeEventListener(type: String, listener: EventListenerObject, options: EventListenerOptions = definedExternally)

    companion object {
        var prototype: MIDIAccess
    }
}

@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
internal abstract external class MIDIConnectionEvent : Event {
    var port: MIDIPort

    companion object {
        var prototype: MIDIConnectionEvent
    }
}

internal abstract external class MIDIInputEventMap : MIDIPortEventMap {
    var midimessage: Event
}

@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
internal abstract external class MIDIInput : MIDIPort {
    var onmidimessage: ((self: MIDIInput, ev: Event) -> JsAny)?
    fun <K : String> addEventListener(type: K, listener: (self: MIDIInput, ev: JsAny) -> JsAny, options: Boolean = definedExternally)
    fun <K : String> addEventListener(type: K, listener: (self: MIDIInput, ev: JsAny) -> JsAny)
    fun <K : String> addEventListener(type: K, listener: (self: MIDIInput, ev: JsAny) -> JsAny, options: AddEventListenerOptions = definedExternally)
    fun <K : String> removeEventListener(type: K, listener: (self: MIDIInput, ev: JsAny) -> JsAny, options: Boolean = definedExternally)
    fun <K : String> removeEventListener(type: K, listener: (self: MIDIInput, ev: JsAny) -> JsAny)
    fun <K : String> removeEventListener(type: K, listener: (self: MIDIInput, ev: JsAny) -> JsAny, options: EventListenerOptions = definedExternally)

    companion object {
        var prototype: MIDIInput
    }
}

@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
internal abstract external class MIDIInputMap {
    fun forEach(callbackfn: (value: MIDIInput, key: String, parent: MIDIInputMap) -> Unit, thisArg: JsAny = definedExternally)

    companion object {
        var prototype: MIDIInputMap
    }
}

@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
internal abstract external class MIDIMessageEvent : Event {
    var data: Uint8Array

    companion object {
        var prototype: MIDIMessageEvent
    }
}

@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
internal abstract external class MIDIOutput : MIDIPort {
    fun send(data: Uint8Array, timestamp: DOMHighResTimeStamp = definedExternally)
    fun <K : String> addEventListener(type: K, listener: (self: MIDIOutput, ev: JsAny) -> JsAny, options: Boolean = definedExternally)
    fun <K : String> addEventListener(type: K, listener: (self: MIDIOutput, ev: JsAny) -> JsAny)
    fun <K : String> addEventListener(type: K, listener: (self: MIDIOutput, ev: JsAny) -> JsAny, options: AddEventListenerOptions = definedExternally)
    fun <K : String> removeEventListener(type: K, listener: (self: MIDIOutput, ev: JsAny) -> JsAny, options: Boolean = definedExternally)
    fun <K : String> removeEventListener(type: K, listener: (self: MIDIOutput, ev: JsAny) -> JsAny)
    fun <K : String> removeEventListener(type: K, listener: (self: MIDIOutput, ev: JsAny) -> JsAny, options: EventListenerOptions = definedExternally)

    companion object {
        var prototype: MIDIOutput
    }
}

@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
internal abstract external class MIDIOutputMap {
    fun forEach(callbackfn: (value: MIDIOutput, key: String, parent: MIDIOutputMap) -> Unit, thisArg: JsAny = definedExternally)

    companion object {
        var prototype: MIDIOutputMap
    }
}

internal abstract external class MIDIPortEventMap {
    var statechange: Event
}

@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
internal abstract external class MIDIPort : EventTarget {
    var connection: JsAny
    var id: String
    var manufacturer: String?
    var name: String?
    var onstatechange: ((self: MIDIPort, ev: Event) -> JsAny)?
    var state: JsAny
    var type: JsAny
    var version: String?
    fun close(): Promise<MIDIPort>
    fun open(): Promise<MIDIPort>
    fun <K : String> addEventListener(type: K, listener: (self: MIDIPort, ev: JsAny) -> JsAny, options: Boolean = definedExternally)
    fun <K : String> addEventListener(type: K, listener: (self: MIDIPort, ev: JsAny) -> JsAny)
    fun <K : String> addEventListener(type: K, listener: (self: MIDIPort, ev: JsAny) -> JsAny, options: AddEventListenerOptions = definedExternally)
    fun addEventListener(type: String, listener: EventListener, options: Boolean = definedExternally)
    fun addEventListener(type: String, listener: EventListener)
    fun addEventListener(type: String, listener: EventListener, options: AddEventListenerOptions = definedExternally)
    fun addEventListener(type: String, listener: EventListenerObject, options: Boolean = definedExternally)
    fun addEventListener(type: String, listener: EventListenerObject)
    fun addEventListener(type: String, listener: EventListenerObject, options: AddEventListenerOptions = definedExternally)
    fun <K : String> removeEventListener(type: K, listener: (self: MIDIPort, ev: JsAny) -> JsAny, options: Boolean = definedExternally)
    fun <K : String> removeEventListener(type: K, listener: (self: MIDIPort, ev: JsAny) -> JsAny)
    fun <K : String> removeEventListener(type: K, listener: (self: MIDIPort, ev: JsAny) -> JsAny, options: EventListenerOptions = definedExternally)
    fun removeEventListener(type: String, listener: EventListener, options: Boolean = definedExternally)
    fun removeEventListener(type: String, listener: EventListener)
    fun removeEventListener(type: String, listener: EventListener, options: EventListenerOptions = definedExternally)
    fun removeEventListener(type: String, listener: EventListenerObject, options: Boolean = definedExternally)
    fun removeEventListener(type: String, listener: EventListenerObject)
    fun removeEventListener(type: String, listener: EventListenerObject, options: EventListenerOptions = definedExternally)

    companion object {
        var prototype: MIDIPort
    }
}
