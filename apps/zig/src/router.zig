const std = @import("std");
const app_mod = @import("app.zig");
const http = @import("http_response.zig");
const metrics_mod = @import("metrics.zig");
const ctrl_log = @import("controller_logging.zig");
const home_handler = @import("handlers/home.zig");
const stack_handler = @import("handlers/stack.zig");
const items_handler = @import("handlers/items.zig");
const api_items_handler = @import("handlers/api_items.zig");
const observability_handler = @import("handlers/observability.zig");
const health_handler = @import("handlers/health.zig");

const SOURCE = "src/router.zig";

pub fn handleRequest(app: *app_mod.App, request: *std.http.Server.Request) !void {
    const path = http.targetOnly(http.pathname(request.head.target));

    if (request.head.method == .GET and std.mem.eql(u8, path, "/health")) {
        return try health_handler.handleHealth(request);
    }
    if (request.head.method == .GET and std.mem.eql(u8, path, "/metrics")) {
        return try health_handler.handleMetrics(app, request);
    }
    if (request.head.method == .GET and std.mem.eql(u8, path, "/")) {
        return try home_handler.handleShell(app, request);
    }
    if (request.head.method == .GET and std.mem.eql(u8, path, "/htmx/view/home")) {
        return try home_handler.handleView(request);
    }
    if (request.head.method == .GET and std.mem.eql(u8, path, "/htmx/view/stack")) {
        return try stack_handler.handleView(request);
    }
    if (request.head.method == .GET and std.mem.eql(u8, path, "/htmx/view/items")) {
        return try items_handler.handleView(request);
    }
    if (request.head.method == .GET and std.mem.eql(u8, path, "/htmx/view/observability")) {
        return try observability_handler.handleView(request);
    }
    if (request.head.method == .GET and std.mem.eql(u8, path, "/htmx/stack")) {
        return try stack_handler.handleProbeRows(app, request);
    }
    if (request.head.method == .GET and std.mem.eql(u8, path, "/htmx/items")) {
        return try items_handler.handleRows(app, request);
    }
    if (request.head.method == .POST and std.mem.eql(u8, path, "/htmx/items")) {
        return try items_handler.handleCreate(app, request);
    }
    if (request.head.method == .GET and std.mem.eql(u8, path, "/api/items")) {
        return try api_items_handler.handleList(app, request);
    }
    if (request.head.method == .POST and std.mem.eql(u8, path, "/api/items")) {
        return try api_items_handler.handleCreate(app, request);
    }
    if (std.mem.startsWith(u8, path, "/api/items/")) {
        const id_text = path["/api/items/".len..];
        const item_id = std.fmt.parseInt(i64, id_text, 10) catch {
            return try http.writeJsonResponse(request, .bad_request, "{\"error\":\"invalid id\"}");
        };
        if (request.head.method == .GET) {
            return try api_items_handler.handleGet(app, request, item_id);
        }
        if (request.head.method == .DELETE) {
            return try api_items_handler.handleDelete(app, request, item_id);
        }
    }

    try http.writeTextResponse(request, .not_found, "text/plain", "not found");
}

pub fn recordRequest() void {
    metrics_mod.incRequests();
}

pub fn logRequestError(request: *std.http.Server.Request, err: anyerror) void {
    ctrl_log.logError("handle_request", SOURCE, "path={s} error={s}", .{
        http.pathname(request.head.target),
        @errorName(err),
    });
}
