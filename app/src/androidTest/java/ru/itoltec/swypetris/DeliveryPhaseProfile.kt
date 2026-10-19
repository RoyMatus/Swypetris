package ru.itoltec.swypetris

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject

/** Opt-in timings only; never changes synchronization, assertions or the tested matrix. */
internal class DeliveryPhaseProfile(private val id: String) : AutoCloseable {
    private val enabled = InstrumentationRegistry.getArguments().getString("deliveryPhaseProfile") == "true"
    private val phases = linkedMapOf<String, Phase>()

    fun <T> measure(name: String, block: () -> T): T {
        if (!enabled) return block()
        val started = System.nanoTime()
        try {
            return block()
        } finally {
            val phase = phases.getOrPut(name) { Phase() }
            phase.count++
            phase.nanos += System.nanoTime() - started
        }
    }

    override fun close() {
        if (!enabled) return
        val arguments = InstrumentationRegistry.getArguments()
        val runId = requireNotNull(arguments.getString("deliveryPhaseProfileRunId"))
        val directory = File(requireNotNull(arguments.getString("additionalTestOutputDir")))
        check(directory.isDirectory || directory.mkdirs())
        val values = JSONObject()
        phases.forEach { (name, phase) ->
            values.put(name, JSONObject().put("count", phase.count).put("nanos", phase.nanos))
        }
        val output = JSONObject().put("schema", 1).put("test", id).put("runId", runId).put("phases", values)
        File(directory, "delivery-profile-$id-$runId.json").writeText(output.toString())
    }

    private class Phase(var count: Int = 0, var nanos: Long = 0)
}
