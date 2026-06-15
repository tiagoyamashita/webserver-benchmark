const std = @import("std");
const app_mod = @import("../app.zig");
const http = @import("../http_response.zig");
const templates = @import("../templates.zig");
const ctrl_log = @import("../controller_logging.zig");

const SOURCE = "src/handlers/home.zig";

pub fn handleShell(_: *app_mod.App, request: *std.http.Server.Request) !void {
    ctrl_log.logReceived("handleShell", SOURCE, "GET", "/");
    try http.writeTextResponse(request, .ok, "text/html; charset=utf-8", templates.shell);
    ctrl_log.logSucceeded("handleShell", SOURCE, "", .{});
}

pub fn handleView(request: *std.http.Server.Request) !void {
    ctrl_log.logReceived("handleView", SOURCE, "GET", "/htmx/view/home");
    try http.writeTextResponse(request, .ok, "text/html; charset=utf-8", templates.home);
    ctrl_log.logSucceeded("handleView", SOURCE, "", .{});
}
