const std = @import("std");
const http = @import("../http_response.zig");
const templates = @import("../templates.zig");
const ctrl_log = @import("../controller_logging.zig");

const SOURCE = "src/handlers/observability.zig";

pub fn handleView(request: *std.http.Server.Request) !void {
    ctrl_log.logReceived("handleObservabilityView", SOURCE, "GET", "/htmx/view/observability");
    try http.writeTextResponse(request, .ok, "text/html; charset=utf-8", templates.observability);
    ctrl_log.logSucceeded("handleObservabilityView", SOURCE, "", .{});
}
