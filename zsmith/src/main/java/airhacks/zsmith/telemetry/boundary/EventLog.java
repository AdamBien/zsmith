package airhacks.zsmith.telemetry.boundary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordingStream;

import airhacks.zsmith.agent.entity.AgentTurnEvent;
import airhacks.zsmith.claude.entity.ClaudeAPICallEvent;
import airhacks.zsmith.lightmetal.entity.LightMetalAPICallEvent;
import airhacks.zsmith.openai.entity.OpenAIAPICallEvent;
import airhacks.zsmith.subagent.entity.SubAgentDispatchEvent;
import airhacks.zsmith.telemetry.control.RunAggregator;
import airhacks.zsmith.telemetry.entity.RunReport;
import airhacks.zsmith.tools.entity.ToolInvocationEvent;

/// Reads the zsmith event stream and answers what each run cost and where it went wrong.
///
/// The same subscription serves both sources, because a live recording and a written file
/// are both an `EventStream` — but they are not equally trustworthy. [#replay] sees a
/// finished file whole and scores it identically every time it is read. [#live] sees only
/// what is committed after it starts and flushed while it runs, which is why the recording
/// has to be started before whatever it is meant to observe. Prefer [#replay] for anything
/// whose number is going to be compared against another run's.
public class EventLog implements AutoCloseable {

    static final List<String> EVENT_NAMES = List.of(
            AgentTurnEvent.NAME,
            ToolInvocationEvent.NAME,
            ClaudeAPICallEvent.NAME,
            OpenAIAPICallEvent.NAME,
            LightMetalAPICallEvent.NAME,
            SubAgentDispatchEvent.NAME);

    private final EventStream stream;
    private final RunAggregator aggregator;

    EventLog(EventStream stream, RunAggregator aggregator) {
        this.stream = stream;
        this.aggregator = aggregator;
    }

    /// Replays a recording to its end. Blocks until the file is exhausted.
    public static Map<String, RunReport> replay(Path recording) {
        var aggregator = new RunAggregator();
        try (var stream = EventStream.openFile(recording)) {
            subscribe(stream, aggregator);
            stream.start();
        } catch (IOException e) {
            throw new UncheckedIOException("could not replay recording " + recording, e);
        }
        return aggregator.reports();
    }

    /// Consumes this JVM's events as they are flushed. Start it before constructing the agent
    /// it should observe — events committed before the stream is running are never delivered,
    /// and a flush interval sits between a commit and this seeing it.
    public static EventLog live() {
        var aggregator = new RunAggregator();
        var stream = new RecordingStream();
        EVENT_NAMES.forEach(stream::enable);
        subscribe(stream, aggregator);
        stream.startAsync();
        return new EventLog(stream, aggregator);
    }

    /// What has arrived so far — a snapshot, not a view. A run still in flight reports the
    /// turns it has taken up to the last flush.
    public Map<String, RunReport> reports() {
        return this.aggregator.reports();
    }

    @Override
    public void close() {
        this.stream.close();
    }

    static void subscribe(EventStream stream, RunAggregator aggregator) {
        EVENT_NAMES.forEach(name -> stream.onEvent(name, aggregator));
    }
}
