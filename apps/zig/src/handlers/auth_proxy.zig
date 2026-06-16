const std = @import("std");
const app_mod = @import("../app.zig");
const http = @import("../http_response.zig");
const outbound = @import("../outbound_http.zig");
const ctrl_log = @import("../controller_logging.zig");

const SOURCE = "src/handlers/auth_proxy.zig";
const SESSION_COOKIE = "exercises_session";
const SESSION_TTL_SECS: u32 = 86_400;

pub fn handleEnsure(app: *app_mod.App, request: *std.http.Server.Request) !void {
    return proxyAuth(app, request, .POST, "/api/auth/ensure", .set_on_success);
}

pub fn handleLogin(app: *app_mod.App, request: *std.http.Server.Request) !void {
    return proxyAuth(app, request, .POST, "/api/auth/login", .set_on_success);
}

pub fn handleLogout(app: *app_mod.App, request: *std.http.Server.Request) !void {
    return proxyAuth(app, request, .POST, "/api/auth/logout", .clear_on_success);
}

pub fn handleRefresh(app: *app_mod.App, request: *std.http.Server.Request) !void {
    return proxyAuth(app, request, .POST, "/api/auth/refresh", .set_on_success);
}

pub fn handleSession(app: *app_mod.App, request: *std.http.Server.Request) !void {
    return proxyAuth(app, request, .GET, "/api/auth/session", .none);
}

const CookieAction = enum { none, set_on_success, clear_on_success };

fn proxyAuth(
    app: *app_mod.App,
    request: *std.http.Server.Request,
    method: std.http.Method,
    path: []const u8,
    cookie_action: CookieAction,
) !void {
    const allocator = app.allocator;
    ctrl_log.logReceived("proxyAuth", SOURCE, @tagName(method), path);

    const url = try std.fmt.allocPrint(allocator, "{s}{s}", .{ app.config.rust_base_url, path });
    defer allocator.free(url);

    const body = try http.readBody(allocator, request);
    defer allocator.free(body);

    const cookie_value = try readSessionCookie(allocator, request);
    defer allocator.free(cookie_value);

    const cookie_header = if (cookie_value.len > 0)
        try std.fmt.allocPrint(allocator, "{s}={s}", .{ SESSION_COOKIE, cookie_value })
    else
        try allocator.dupe(u8, "");
    defer allocator.free(cookie_header);

    const payload = if (method == .POST)
        if (body.len > 0) body else "{}"
    else
        null;

    const result = try outbound.fetch(allocator, method, url, path, .{
        .payload = payload,
        .content_type = if (method == .POST) "application/json" else null,
        .cookie = if (cookie_header.len > 0) cookie_header else null,
    });
    defer allocator.free(result.body);

    const status: std.http.Status = @enumFromInt(@min(result.status, 599));

    if (cookie_action == .clear_on_success and result.status == 204) {
        const clear_cookie = try sessionCookieHeader(allocator, "", 0);
        defer allocator.free(clear_cookie);
        const extra = [_]std.http.Header{.{ .name = "set-cookie", .value = clear_cookie }};
        return try http.writeNoContentResponse(request, &extra);
    }

    if (cookie_action == .set_on_success and result.status >= 200 and result.status < 300) {
        if (extractSessionId(allocator, result.body)) |session_id| {
            defer allocator.free(session_id);
            const set_cookie = try sessionCookieHeader(allocator, session_id, SESSION_TTL_SECS);
            defer allocator.free(set_cookie);
            const extra = [_]std.http.Header{.{ .name = "set-cookie", .value = set_cookie }};
            return try http.writeJsonResponseExtra(request, status, result.body, &extra);
        }
    }

    return try http.writeJsonResponse(request, status, result.body);
}

fn readSessionCookie(allocator: std.mem.Allocator, request: *std.http.Server.Request) ![]const u8 {
    var header_it = request.iterateHeaders();
    while (header_it.next()) |header| {
        if (!std.ascii.eqlIgnoreCase(header.name, "cookie")) continue;
        const raw = std.mem.trim(u8, header.value, " \t\r\n");
        var parts = std.mem.splitScalar(u8, raw, ';');
        while (parts.next()) |part| {
            const trimmed = std.mem.trim(u8, part, " \t");
            if (std.mem.startsWith(u8, trimmed, SESSION_COOKIE)) {
                const eq = std.mem.indexOfScalar(u8, trimmed, '=') orelse continue;
                const value = std.mem.trim(u8, trimmed[eq + 1 ..], " \t");
                if (value.len > 0) return try allocator.dupe(u8, value);
            }
        }
    }
    return try allocator.dupe(u8, "");
}

fn sessionCookieHeader(allocator: std.mem.Allocator, session_id: []const u8, max_age: u32) ![]const u8 {
    return std.fmt.allocPrint(
        allocator,
        "{s}={s}; HttpOnly; Path=/; Max-Age={d}; SameSite=Lax",
        .{ SESSION_COOKIE, session_id, max_age },
    );
}

fn extractSessionId(allocator: std.mem.Allocator, body: []const u8) ?[]const u8 {
    const key = "\"sessionId\"";
    const key_pos = std.mem.indexOf(u8, body, key) orelse return null;
    const after_key = body[key_pos + key.len ..];
    const colon = std.mem.indexOfScalar(u8, after_key, ':') orelse return null;
    var rest = std.mem.trimLeft(u8, after_key[colon + 1 ..], " \t");
    if (rest.len == 0 or rest[0] != '"') return null;
    rest = rest[1..];
    const end = std.mem.indexOfScalar(u8, rest, '"') orelse return null;
    return allocator.dupe(u8, rest[0..end]) catch null;
}
