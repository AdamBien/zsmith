package airhacks.zsmith.agentcore.entity;

import org.json.JSONObject;

public record ResultEnvelope(String response, String status) {

    public static ResultEnvelope success(String response) {
        return new ResultEnvelope(response == null ? "" : response, "success");
    }

    public static ResultEnvelope error(String message) {
        return new ResultEnvelope(message == null ? "Internal error" : message, "error");
    }

    public String toJson() {
        return new JSONObject()
                .put("response", this.response)
                .put("status", this.status)
                .toString();
    }
}
