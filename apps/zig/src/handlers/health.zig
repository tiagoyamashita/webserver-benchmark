const std = @import("std");
const app_mod = @import("../app.zig");
const http = @import("../http_response.zig");
const metrics_mod = @import("../metrics.zig");
const ctrl_log = @import("../controller_logging.zig");

const SOURCE = "src/handlers/health.zig";

pub fn handleHealth(request: *std.http.Server.Request) !void {
    ctrl_log.logReceived("handleHealth", SOURCE, "GET", "/health");
    try http.writeJsonResponse(request, .ok, "{\"status\":\"ok\",\"service\":\"zig\"}");
    ctrl_log.logSucceeded("handleHealth", SOURCE, "", .{});
}

pub fn handleMetrics(app: *app_mod.App, request: *std.http.Server.Request) !void {
    ctrl_log.logReceived("handleMetrics", SOURCE, "GET", "/metrics");
    const body = try metrics_mod.render(app.allocator);
    defer app.allocator.free(body);
    try http.writeTextResponse(request, .ok, "text/plain; version=0.0.4", body);
    ctrl_log.logSucceeded("handleMetrics", SOURCE, "", .{});
}
