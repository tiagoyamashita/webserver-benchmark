const std = @import("std");
const app_mod = @import("../app.zig");
const http = @import("../http_response.zig");
const templates = @import("../templates.zig");
const stack_ping_mod = @import("../stack_ping.zig");
const ctrl_log = @import("../controller_logging.zig");

const SOURCE = "src/handlers/stack.zig";

pub fn handleView(request: *std.http.Server.Request) !void {
    ctrl_log.logReceived("handleStackView", SOURCE, "GET", "/htmx/view/stack");
    try http.writeTextResponse(request, .ok, "text/html; charset=utf-8", templates.stack);
    ctrl_log.logSucceeded("handleStackView", SOURCE, "", .{});
}

pub fn handleProbeRows(app: *app_mod.App, request: *std.http.Server.Request) !void {
    ctrl_log.logReceived("handleStackProbes", SOURCE, "GET", "/htmx/stack");
    const probes = try stack_ping_mod.stackProbes(
        app.allocator,
        app.config.java_base_url,
        app.config.python_base_url,
        app.config.rust_base_url,
        app.config.react_node_base_url,
        app.config.prometheus_base_url,
        app.config.grafana_base_url,
        app.config.elasticsearch_base_url,
        app.config.kibana_base_url,
    );
    defer app.allocator.free(probes);
    const rows = try stack_ping_mod.renderProbeRows(app.allocator, probes);
    defer app.allocator.free(rows);
    try http.writeTextResponse(request, .ok, "text/html; charset=utf-8", rows);
    ctrl_log.logSucceeded("handleStackProbes", SOURCE, "probe_count={d}", .{probes.len});
}
