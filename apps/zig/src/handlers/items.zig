const std = @import("std");
const app_mod = @import("../app.zig");
const db_mod = @import("../db.zig");
const html_mod = @import("../html.zig");
const http = @import("../http_response.zig");
const templates = @import("../templates.zig");
const ctrl_log = @import("../controller_logging.zig");

const SOURCE = "src/handlers/items.zig";

pub fn handleView(request: *std.http.Server.Request) !void {
    ctrl_log.logReceived("handleItemsView", SOURCE, "GET", "/htmx/view/items");
    try http.writeTextResponse(request, .ok, "text/html; charset=utf-8", templates.items);
    ctrl_log.logSucceeded("handleItemsView", SOURCE, "", .{});
}

pub fn handleRows(app: *app_mod.App, request: *std.http.Server.Request) !void {
    ctrl_log.logReceived("handleItemsRows", SOURCE, @tagName(request.head.method), "/htmx/items");
    var database = app.db orelse {
        ctrl_log.logWarn("handleItemsRows", SOURCE, "error=postgres_not_configured", .{});
        return try writeMessageBody(app, request, .service_unavailable, "Postgres not configured (set DB_HOST).");
    };
    app.db_mutex.lock();
    defer app.db_mutex.unlock();
    return try renderRowsLocked(app, request, &database);
}

pub fn handleCreate(app: *app_mod.App, request: *std.http.Server.Request) !void {
    ctrl_log.logReceived("handleItemsCreate", SOURCE, "POST", "/htmx/items");
    var database = app.db orelse {
        return try writeMessageBody(app, request, .service_unavailable, "Postgres not configured.");
    };
    const body = try http.readBody(app.allocator, request);
    defer app.allocator.free(body);
    const name_owned = try app.allocator.dupe(u8, html_mod.parseFormName(body) orelse "");
    defer app.allocator.free(name_owned);
    const decoded = html_mod.urlDecodeInPlace(name_owned);
    const trimmed = std.mem.trim(u8, decoded, " \t\r\n");
    if (trimmed.len == 0) {
        ctrl_log.logWarn("handleItemsCreate", SOURCE, "reason=blank-name", .{});
        return try writeMessageBody(app, request, .bad_request, "Name must not be blank.");
    }
    app.db_mutex.lock();
    defer app.db_mutex.unlock();
    const inserted = database.insertItem(app.allocator, trimmed) catch {
        return try writeMessageBody(app, request, .internal_server_error, "Insert failed.");
    };
    defer {
        app.allocator.free(inserted.name);
        app.allocator.free(inserted.created_at);
    }
    try renderRowsLocked(app, request, &database);
    ctrl_log.logSucceeded("handleItemsCreate", SOURCE, "item_id={d}", .{inserted.id});
}

fn writeMessageBody(
    app: *app_mod.App,
    request: *std.http.Server.Request,
    status: std.http.Status,
    message: []const u8,
) !void {
    const body = try html_mod.renderItemsMessageBody(app.allocator, message);
    defer app.allocator.free(body);
    try http.writeTextResponse(request, status, "text/html; charset=utf-8", body);
}

fn renderRowsLocked(app: *app_mod.App, request: *std.http.Server.Request, database: *db_mod.Db) !void {
    const items = database.listItems(app.allocator) catch {
        ctrl_log.logWarn("handleItemsRows", SOURCE, "error=list_items_failed", .{});
        return try writeMessageBody(app, request, .internal_server_error, "Failed to load items.");
    };
    defer db_mod.Db.freeItems(items, app.allocator);
    const body = try html_mod.renderItemsBody(app.allocator, items);
    defer app.allocator.free(body);
    try http.writeTextResponse(request, .ok, "text/html; charset=utf-8", body);
    if (!std.mem.eql(u8, @tagName(request.head.method), "POST")) {
        ctrl_log.logSucceeded("handleItemsRows", SOURCE, "item_count={d}", .{items.len});
    }
}
