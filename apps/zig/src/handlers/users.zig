const std = @import("std");
const app_mod = @import("../app.zig");
const db_mod = @import("../db.zig");
const password_mod = @import("../auth/password.zig");
const cookies = @import("../auth/cookies.zig");
const http = @import("../http_response.zig");
const ctrl_log = @import("../controller_logging.zig");

const SOURCE = "src/handlers/users.zig";

pub fn handleCreateUser(app: *app_mod.App, request: *std.http.Server.Request) !void {
    ctrl_log.logReceived("create_user", SOURCE, "POST", "/api/users");
    const allocator = app.allocator;

    const body = try http.readBody(allocator, request);
    defer allocator.free(body);

    const name = cookies.extractJsonString(allocator, body, "name");
    defer if (name) |value| allocator.free(value);
    const email = cookies.extractJsonString(allocator, body, "email");
    defer if (email) |value| allocator.free(value);
    const password = cookies.extractJsonString(allocator, body, "password");
    defer if (password) |value| allocator.free(value);

    const name_text = std.mem.trim(u8, name orelse "", " \t\r\n");
    const email_text = std.mem.trim(u8, email orelse "", " \t\r\n");
    const password_text = password orelse "";

    if (name_text.len == 0 or email_text.len == 0 or password_text.len < 8) {
        return try http.writeJsonResponse(
            request,
            .bad_request,
            "{\"error\":\"name, email, and password (min 8 chars) are required\"}",
        );
    }

    var database = app.db orelse {
        return try http.writeJsonResponse(request, .service_unavailable, "{\"error\":\"Postgres not configured\"}");
    };

    const password_hash = password_mod.hashPassword(allocator, password_text) catch {
        return try http.writeJsonResponse(request, .internal_server_error, "{\"error\":\"password hash failed\"}");
    };
    defer allocator.free(password_hash);

    app.db_mutex.lock();
    defer app.db_mutex.unlock();

    const inserted = database.insertUserWithPassword(allocator, name_text, email_text, password_hash) catch |err| {
        if (err == db_mod.DbError.DuplicateEmail) {
            return try http.writeJsonResponse(request, .conflict, "{\"error\":\"Email already registered\"}");
        }
        return try http.writeJsonResponse(request, .internal_server_error, "{\"error\":\"insert failed\"}");
    };
    defer {
        allocator.free(inserted.name);
        allocator.free(inserted.email);
        allocator.free(inserted.created_at);
    }

    const safe_name = try jsonEscape(allocator, inserted.name);
    defer allocator.free(safe_name);
    const safe_email = try jsonEscape(allocator, inserted.email);
    defer allocator.free(safe_email);
    const safe_created = try jsonEscape(allocator, inserted.created_at);
    defer allocator.free(safe_created);

    const payload = try std.fmt.allocPrint(
        allocator,
        "{{\"id\":{d},\"name\":\"{s}\",\"email\":\"{s}\",\"createdAt\":\"{s}\"}}",
        .{ inserted.id, safe_name, safe_email, safe_created },
    );
    defer allocator.free(payload);

    ctrl_log.logSucceeded("create_user", SOURCE, "user_id={d} email={s}", .{ inserted.id, inserted.email });
    return try http.writeJsonResponse(request, .created, payload);
}

fn jsonEscape(allocator: std.mem.Allocator, text: []const u8) ![]const u8 {
    var list = std.ArrayList(u8).init(allocator);
    defer list.deinit();
    for (text) |byte| {
        switch (byte) {
            '"' => try list.appendSlice("\\\""),
            '\\' => try list.appendSlice("\\\\"),
            '\n' => try list.appendSlice("\\n"),
            '\r' => try list.appendSlice("\\r"),
            '\t' => try list.appendSlice("\\t"),
            else => try list.append(byte),
        }
    }
    return list.toOwnedSlice();
}
