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
        return try http.writeTextResponse(request, .service_unavailable, "text/html; charset=utf-8", "<tr><td colspan=\"3\">Postgres not configured (set DB_HOST).</td></tr>");
    };
    app.db_mutex.lock();
    defer app.db_mutex.unlock();
    return try renderRowsLocked(app, request, &database);
}

pub fn handleCreate(app: *app_mod.App, request: *std.http.Server.Request) !void {
    var database = app.db orelse {
        return try http.writeTextResponse(request, .service_unavailable, "text/html; charset=utf-8", "<tr><td colspan=\"3\">Postgres not configured.</td></tr>");
    };
    const body = try http.readBody(app.allocator, request);
    defer app.allocator.free(body);
    var name_buf = try app.allocator.dupe(u8, html_mod.parseFormName(body) orelse "");
    defer app.allocator.free(name_buf);
    name_buf = html_mod.urlDecodeInPlace(name_buf);
    const trimmed = std.mem.trim(u8, name_buf, " \t\r\n");
    if (trimmed.len == 0) {
        return try http.writeTextResponse(request, .bad_request, "text/html; charset=utf-8", "<tr><td colspan=\"3\">Name must not be blank.</td></tr>");
    }
    app.db_mutex.lock();
    defer app.db_mutex.unlock();
    const inserted = database.insertItem(app.allocator, trimmed) catch {
        return try http.writeTextResponse(request, .internal_server_error, "text/html; charset=utf-8", "<tr><td colspan=\"3\">Insert failed.</td></tr>");
    };
    defer {
        app.allocator.free(inserted.name);
        app.allocator.free(inserted.created_at);
    }
    return try renderRowsLocked(app, request, &database);
}

fn renderRowsLocked(app: *app_mod.App, request: *std.http.Server.Request, database: *db_mod.Db) !void {
    const items = database.listItems(app.allocator) catch {
        ctrl_log.logWarn("handleItemsRows", SOURCE, "error=list_items_failed", .{});
        return try http.writeTextResponse(request, .internal_server_error, "text/html; charset=utf-8", "<tr><td colspan=\"3\">Failed to load items.</td></tr>");
    };
    defer db_mod.Db.freeItems(items, app.allocator);
    const rows = try html_mod.renderItemsRows(app.allocator, items);
    defer app.allocator.free(rows);
    try http.writeTextResponse(request, .ok, "text/html; charset=utf-8", rows);
    ctrl_log.logSucceeded("handleItemsRows", SOURCE, "item_count={d}", .{items.len});
}
