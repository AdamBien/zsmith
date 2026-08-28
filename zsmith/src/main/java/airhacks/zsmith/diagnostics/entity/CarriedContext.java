package airhacks.zsmith.diagnostics.entity;

import java.util.List;

/// What one tool cost a run by what it left in the conversation.
///
/// The cost of a tool result is not its size. A result is appended to the conversation and re-sent
/// on every turn that follows, so what leaves the machine is its size multiplied by the turns that
/// carried it — and that is the number worth comparing, because the two rank differently. A single
/// 67 KB read looks worse than three recalls of 27 KB until the turns are counted, at which point
/// the recalls cost half again as much.
///
/// Summed per tool rather than per call for the same reason: a tool called three times is one thing
/// to fix, and three findings about it read as three problems.
public record CarriedContext(String toolName, int calls, long bytes, long carriedBytes,
        int longestCarry) {

    public static CarriedContext of(List<ToolCall> calls, int turns) {
        return new CarriedContext(
                calls.getFirst().toolName(),
                calls.size(),
                calls.stream().mapToLong(ToolCall::resultSize).sum(),
                calls.stream().mapToLong(call -> (long) call.resultSize() * call.turnsCarried(turns)).sum(),
                calls.stream().mapToInt(call -> call.turnsCarried(turns)).max().orElse(0));
    }

    public long carriedKilobytes() {
        return this.carriedBytes / 1024;
    }

    /// Reads differently for one call and for several, because "longest" means nothing when there
    /// is only one and "totalling" invites the reader to look for the parts.
    public String describe() {
        if (this.calls == 1) {
            return "1 call of %d bytes, carried %d turns".formatted(this.bytes, this.longestCarry);
        }
        return "%d calls totalling %d bytes, longest carried %d turns".formatted(
                this.calls, this.bytes, this.longestCarry);
    }
}
