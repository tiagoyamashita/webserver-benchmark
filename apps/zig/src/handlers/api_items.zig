const std = @import("std");
const app_mod = @import("../app.zig");
const db_mod = @import("../db.zig");
const http = @import("../http_response.zig");
const ctrl_log = @import("../controller_logging.zig");

const SOURCE = "src/handlers/api_items.zig";

pub fn handleList(app: *app_mod.App, request: *std.http.Server.Request) !void {
    ctrl_log.logReceived("handleApiListItems", SOURCE, "GET", "/api/items");
    var database = app.db orelse {
        return try http.writeJsonResponse(request, .service_unavailable, "{\"error\":\"database not configured\"}");
    };
    app.db_mutex.lock();
    defer app.db_mutex.unlock();
    const items = try database.listItems(app.allocator);
    defer db_mod.Db.freeItems(items, app.allocator);
    const body = try itemsToJson(app.allocator, items);
    defer app.allocator.free(body);
    try http.writeJsonResponse(request, .ok, body);
    ctrl_log.logSucceeded("handleApiListItems", SOURCE, "item_count={d}", .{items.len});
}

pub fn handleCreate(app: *app_mod.App, request: *std.http.Server.Request) !void {
    ctrl_log.logReceived("handleApiCreateItem", SOURCE, "POST", "/api/items");
    var database = app.db orelse {
        return try http.writeJsonResponse(request, .service_unavailable, "{\"error\":\"database not configured\"}");
    };
    const body = try http.readBody(app.allocator, request);
    defer app.allocator.free(body);
    const name = parseJsonName(body) orelse {
        return try http.writeJsonResponse(request, .bad_request, "{\"error\":\"name must not be blank\"}");
    };
    app.db_mutex.lock();
    defer app.db_mutex.unlock();
    const inserted = database.insertItem(app.allocator, name) catch {
        return try http.writeJsonResponse(request, .internal_server_error, "{\"error\":\"insert failed\"}");
    };
    defer {
        app.allocator.free(inserted.name);
        app.allocator.free(inserted.created_at);
    }
    const safe_name = try jsonEscape(app.allocator, inserted.name);
    defer app.allocator.free(safe_name);
    const safe_created = try jsonEscape(app.allocator, inserted.created_at);
    defer app.allocator.free(safe_created);
    const payload = try std.fmt.allocPrint(
        app.allocator,
        "{{\"id\":{d},\"name\":\"{s}\",\"createdAt\":\"{s}\"}}",
        .{ inserted.id, safe_name, safe_created },
    );
    defer app.allocator.free(payload);
    try http.writeJsonResponse(request, .created, payload);
    ctrl_log.logSucceeded("handleApiCreateItem", SOURCE, "item_id={d}", .{inserted.id});
}

pub fn handleGet(app: *app_mod.App, request: *std.http.Server.Request, item_id: i64) !void {
    ctrl_log.logReceived("handleApiGetItem", SOURCE, "GET", "/api/items/{id}");
    var database = app.db orelse {
        return try http.writeJsonResponse(request, .service_unavailable, "{\"error\":\"database not configured\"}");
    };
    app.db_mutex.lock();
    defer app.db_mutex.unlock();
    const item = try database.getItem(app.allocator, item_id);
    if (item) |row| {
        defer {
            app.allocator.free(row.name);
            app.allocator.free(row.created_at);
        }
        const safe_name = try jsonEscape(app.allocator, row.name);
        defer app.allocator.free(safe_name);
        const safe_created = try jsonEscape(app.allocator, row.created_at);
        defer app.allocator.free(safe_created);
        const payload = try std.fmt.allocPrint(
            app.allocator,
            "{{\"id\":{d},\"name\":\"{s}\",\"createdAt\":\"{s}\"}}",
            .{ row.id, safe_name, safe_created },
        );
        defer app.allocator.free(payload);
        return try http.writeJsonResponse(request, .ok, payload);
    }
    try http.writeJsonResponse(request, .not_found, "{\"error\":\"not found\"}");
}

pub fn handleDelete(app: *app_mod.App, request: *std.http.Server.Request, item_id: i64) !void {
    ctrl_log.logReceived("handleApiDeleteItem", SOURCE, "DELETE", "/api/items/{id}");
    var database = app.db orelse {
        return try http.writeJsonResponse(request, .service_unavailable, "{\"error\":\"database not configured\"}");
    };
    app.db_mutex.lock();
    defer app.db_mutex.unlock();
    const deleted = try database.deleteItem(item_id);
    if (!deleted) return try http.writeJsonResponse(request, .not_found, "{\"error\":\"not found\"}");
    try http.writeJsonResponse(request, .no_content, "");
    ctrl_log.logSucceeded("handleApiDeleteItem", SOURCE, "item_id={d}", .{item_id});
}

fn itemsToJson(allocator: std.mem.Allocator, items: []const db_mod.Item) ![]u8 {
    var list = std.ArrayList(u8).init(allocator);
    errdefer list.deinit();
    try list.append('[');
    for (items, 0..) |item, i| {
        if (i > 0) try list.append(',');
        const safe_name = try jsonEscape(allocator, item.name);
        defer allocator.free(safe_name);
        const safe_created = try jsonEscape(allocator, item.created_at);
        defer allocator.free(safe_created);
        try list.writer().print(
            "{{\"id\":{d},\"name\":\"{s}\",\"createdAt\":\"{s}\"}}",
            .{ item.id, safe_name, safe_created },
        );
    }
    try list.append(']');
    return list.toOwnedSlice();
}

fn jsonEscape(allocator: std.mem.Allocator, input: []const u8) ![]u8 {
    var list = std.ArrayList(u8).init(allocator);
    errdefer list.deinit();
    for (input) |ch| {
        switch (ch) {
            '\\' => try list.appendSlice("\\\\"),
            '"' => try list.appendSlice("\\\""),
            else => try list.append(ch),
        }
    }
    return list.toOwnedSlice();
}

fn parseJsonName(body: []const u8) ?[]const u8 {
    const key = "\"name\"";
    const idx = std.mem.indexOf(u8, body, key) orelse return null;
    const rest = body[idx + key.len ..];
    const colon = std.mem.indexOf(u8, rest, ":") orelse return null;
    const after = std.mem.trim(u8, rest[colon + 1 ..], " \t\r\n");
    if (after.len < 2 or after[0] != '"') return null;
    const end = std.mem.indexOf(u8, after[1..], "\"") orelse return null;
    const name = std.mem.trim(u8, after[1 .. 1 + end], " \t\r\n");
    if (name.len == 0) return null;
    return name;
}
