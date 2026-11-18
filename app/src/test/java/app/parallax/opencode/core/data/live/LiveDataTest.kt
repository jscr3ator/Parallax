package app.parallax.opencode.core.data.live

import app.parallax.opencode.core.model.RunState
import app.parallax.opencode.core.model.ToolStatus
import app.parallax.opencode.core.model.TranscriptItem
import app.parallax.opencode.core.net.ApiJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveDataTest {

    private val sessionId = "ses_example3"

    @Test
    fun `sample event stream reduces to the expected transcript`() {
        var state = TranscriptState()
        events().filter { eventSessionId(it) == sessionId }.forEach { event ->
            state = EventReducer.apply(state, event)
        }

        assertEquals(RunState.DONE, state.state)
        assertFalse(state.runActive)
        assertNull(state.runStartedAt)
        assertNull(state.activity)

        val items = state.items
        assertEquals(4, items.size)
        assertTrue(items[0] is TranscriptItem.User)
        assertEquals(
            "Use the bash tool to run: echo hi. Then reply with exactly: done",
            (items[0] as TranscriptItem.User).text,
        )
        assertFalse((items[0] as TranscriptItem.User).queued)

        val tool = items[1] as TranscriptItem.Tool
        assertEquals("shell", tool.name)
        assertEquals("Ran", tool.verb)
        assertEquals("echo hi", tool.subject)
        assertEquals(ToolStatus.SUCCESS, tool.status)
        assertEquals("hi\n", tool.output)

        val text = items[2] as TranscriptItem.AssistantText
        assertEquals("done", text.text)
        assertFalse(text.streaming)

        assertEquals(RunState.DONE, (items[3] as TranscriptItem.RunEnd).outcome)
    }

    @Test
    fun `sample messages map to transcript rows`() {
        val root = ApiJson.parseToJsonElement(doc("sample-messages.json")).jsonObject
        val messages = root.arrayAt("data")!!.map { it.jsonObject }.reversed()
        val items = mapMessages(messages)

        assertEquals(4, items.size)
        assertEquals("Use the bash tool to run: echo hi. Then reply with exactly: done", (items[0] as TranscriptItem.User).text)
        val tool = items[1] as TranscriptItem.Tool
        assertEquals("Ran", tool.verb)
        assertEquals("echo hi", tool.subject)
        assertEquals(ToolStatus.SUCCESS, tool.status)
        assertEquals("hi\n", tool.output)
        assertEquals("done", (items[2] as TranscriptItem.AssistantText).text)
        assertEquals(RunState.DONE, (items[3] as TranscriptItem.RunEnd).outcome)
    }

    @Test
    fun `tool display follows the transcript naming rules`() {
        assertEquals("Ran", toolDisplay("shell", obj("""{"command":"npm test"}""")).verb)
        assertEquals("npm test", toolDisplay("bash", obj("""{"command":"npm test"}""")).subject)
        assertEquals("Read", toolDisplay("read", obj("""{"filePath":"src/App.kt"}""")).verb)
        assertEquals("Edited", toolDisplay("edit", obj("""{"filePath":"src/App.kt"}""")).verb)
        assertEquals("Searched", toolDisplay("grep", obj("""{"pattern":"TODO"}""")).verb)
        assertEquals("Fetched", toolDisplay("webfetch", obj("""{"url":"https://example.com"}""")).verb)
        assertEquals("Searched web", toolDisplay("websearch", obj("""{"query":"kotlin"}""")).verb)

        val task = toolDisplay(
            "task",
            obj("""{"description":"research the API"}"""),
            obj("""{"sessionID":"ses_child"}"""),
        )
        assertEquals("Delegated", task.verb)
        assertEquals("research the API", task.subject)
        assertEquals("ses_child", task.childSessionId)

        val mcp = toolDisplay("mcp__github__search", obj("""{"query":"find"}"""))
        assertEquals("Used", mcp.verb)
        assertEquals("github · search", mcp.subject)

        assertEquals("custom_tool", toolDisplay("custom_tool", null).verb)
    }

    @Test
    fun `permission request makes the session need you and reply resumes it`() {
        var state = EventReducer.apply(TranscriptState(), event("session.execution.started", """{"sessionID":"ses_x"}"""))
        state = EventReducer.apply(
            state,
            event(
                "permission.asked",
                """{"id":"per_1","sessionID":"ses_x","action":"bash","resources":["rm -rf build"],"message":"Allow?"}""",
            ),
        )

        assertEquals(RunState.NEEDS_YOU, state.state)
        assertEquals(1, state.pendingCount)
        assertEquals("Waiting for you", state.activity)
        assertTrue(state.items.last() is TranscriptItem.Permission)
        assertEquals("rm -rf build", (state.items.last() as TranscriptItem.Permission).request.resources.single())

        state = EventReducer.apply(
            state,
            event("permission.replied", """{"sessionID":"ses_x","requestID":"per_1","reply":"once"}"""),
        )
        assertEquals(0, state.pendingCount)
        assertEquals(RunState.RUNNING, state.state)
        assertTrue(state.items.none { it is TranscriptItem.Permission })
    }

    @Test
    fun `form request makes the session need you and reply removes it`() {
        var state = EventReducer.apply(TranscriptState(), event("session.execution.started", """{"sessionID":"ses_x"}"""))
        state = EventReducer.apply(
            state,
            event(
                "form.created",
                """{"form":{"id":"frm_1","sessionID":"ses_x","title":"Pick one","fields":[{"key":"choice","type":"string","title":"Choice","options":[{"value":"a","label":"A"},{"value":"b","label":"B"}]}]}}""",
            ),
        )
        assertEquals(RunState.NEEDS_YOU, state.state)
        assertEquals(1, state.pendingForms.size)
        val form = (state.items.last() as TranscriptItem.Form).form
        assertEquals("Pick one", form.title)
        assertEquals(listOf("A", "B"), form.fields.single().options)

        state = EventReducer.apply(state, event("form.replied", """{"id":"frm_1","sessionID":"ses_x","answer":{"choice":"a"}}"""))
        assertEquals(0, state.pendingCount)
        assertEquals(RunState.RUNNING, state.state)
        assertTrue(state.items.none { it is TranscriptItem.Form })
    }

    @Test
    fun `history merge keeps the completed text and deduplicates run ends`() {
        var live = TranscriptState()
        live = EventReducer.apply(live, event("session.execution.started", """{"sessionID":"ses_x"}"""))
        live = EventReducer.apply(
            live,
            event("session.text.delta", """{"sessionID":"ses_x","assistantMessageID":"msg_a","ordinal":0,"delta":"do"}"""),
        )
        live = EventReducer.apply(live, event("session.execution.succeeded", """{"sessionID":"ses_x"}"""))
        assertEquals("do", (live.items.first { it is TranscriptItem.AssistantText } as TranscriptItem.AssistantText).text)

        val history = listOf(
            TranscriptItem.User("msg_u", "hi", 1),
            TranscriptItem.AssistantText("text:msg_a:0", "done", streaming = false),
            TranscriptItem.RunEnd("msg_idle", RunState.DONE, live.items.last().let { (it as TranscriptItem.RunEnd).time }, null),
        )
        val merged = EventReducer.mergeHistory(live, history)

        assertEquals("done", (merged.items.first { it is TranscriptItem.AssistantText } as TranscriptItem.AssistantText).text)
        assertEquals(1, merged.items.count { it is TranscriptItem.RunEnd })
        assertTrue(merged.items.any { it is TranscriptItem.User && it.text == "hi" })
    }

    @Test
    fun `session info maps running and finished states`() {
        val running = mapSession(
            obj(
                """{"id":"ses_1","projectID":"p","model":{"id":"deepseek-v4.1-flash","providerID":"opencode-go","variant":"default"},"cost":0.5,"tokens":{"input":10,"output":5,"reasoning":0,"cache":{"read":0,"write":0}},"time":{"created":100,"updated":200,"busy":150},"title":"probe","location":{"directory":"/tmp"}}""",
            ),
            "srv",
        )!!
        assertEquals(RunState.RUNNING, running.state)
        assertEquals(150L, running.runStartedAt)
        assertEquals("Thinking…", running.activity)
        assertEquals(10L, running.tokensIn)

        val done = mapSession(
            obj(
                """{"id":"ses_2","projectID":"p","outcome":"succeeded","time":{"created":100,"updated":220,"busy":150,"idle":230},"title":"done","location":{"directory":"/tmp"}}""",
            ),
            "srv",
        )!!
        assertEquals(RunState.DONE, done.state)
        assertNull(done.runStartedAt)
        assertTrue(done.unread)
    }

    @Test
    fun `usage limits map go meters`() {
        val summaries = mapUsage(
            obj(
                """{"data":{"workspace":{"id":"w","name":"Personal"},"go":{"rolling":{"percent":12.5,"limited":false,"resetsAt":1794978057000},"weekly":{"percent":100,"limited":true,"resetsAt":1795392000000},"monthly":{"percent":36,"limited":false,"resetsAt":1796366532000}},"budgets":[]}}""",
            ),
        )
        assertEquals(3, summaries.size)
        assertEquals(0.125f, summaries[0].usedFraction!!, 0.001f)
        assertTrue(summaries[1].detail!!.startsWith("Limit reached"))
        assertEquals("Monthly usage", summaries[2].label)
    }

    // -----------------------------------------------------------------------

    @Test
    fun `delivery and cancellation affect only their queued prompt`() {
        var state = TranscriptState()
        for (id in listOf("first", "second")) {
            val data = """{"sessionID":"s","inboxID":"$id","item":{"type":"user","payload":{"text":"follow up"}}}"""
            state = EventReducer.apply(state, event("session.inbox.enqueued", data))
        }
        state = EventReducer.apply(state, event("session.inbox.cancelled", """{"sessionID":"s","inboxID":"second"}"""))
        val waiting = state.items.single() as TranscriptItem.User
        assertEquals("first", waiting.id)
        assertTrue(waiting.queued)
        state = EventReducer.apply(state, event("session.inbox.delivered", """{"sessionID":"s","inboxID":"first"}"""))
        assertEquals(waiting.copy(queued = false), state.items.single())
    }

    private fun doc(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/$name")).bufferedReader().use { it.readText() }

    private fun events(): List<AgentEvent> =
        doc("sample-events.sse")
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .filter { it.isNotEmpty() }
            .map { ApiJson.parseToJsonElement(it).jsonObject }
            .mapNotNull(::parseAgentEvent)
            .toList()

    private fun event(type: String, data: String): AgentEvent =
        AgentEvent(id = "evt_${type}_${counter++}", type = type, created = counter * 1000L, directory = null, data = obj(data))

    private var counter = 0

    private fun obj(json: String): JsonObject = ApiJson.parseToJsonElement(json).jsonObject
}
