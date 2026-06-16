const std = @import("std");
const http = @import("../http_response.zig");
const session_mod = @import("session.zig");

pub fn readSessionCookie(allocator: std.mem.Allocator, request: *std.http.Server.Request, cookie_name: []const u8) ![]const u8 {
    var header_it = request.iterateHeaders();
    while (header_it.next()) |header| {
        if (!std.ascii.eqlIgnoreCase(header.name, "cookie")) continue;
        const raw = std.mem.trim(u8, header.value, " \t\r\n");
        var parts = std.mem.splitScalar(u8, raw, ';');
        while (parts.next()) |part| {
            const trimmed = std.mem.trim(u8, part, " \t");
            if (trimmed.len <= cookie_name.len + 1) continue;
            if (!std.mem.startsWith(u8, trimmed, cookie_name)) continue;
            if (trimmed[cookie_name.len] != '=') continue;
            const value = std.mem.trim(u8, trimmed[cookie_name.len + 1 ..], " \t");
            if (value.len > 0) return try allocator.dupe(u8, value);
        }
    }
    return try allocator.dupe(u8, "");
}

pub fn sessionCookieHeader(allocator: std.mem.Allocator, cookie_name: []const u8, session_id: []const u8, max_age: u32) ![]const u8 {
    return std.fmt.allocPrint(
        allocator,
        "{s}={s}; HttpOnly; Path=/; Max-Age={d}; SameSite=Lax",
        .{ cookie_name, session_id, max_age },
    );
}

pub fn clearSessionCookieHeader(allocator: std.mem.Allocator, cookie_name: []const u8) ![]const u8 {
    return std.fmt.allocPrint(allocator, "{s}=; HttpOnly; Path=/; Max-Age=0; SameSite=Lax", .{cookie_name});
}

pub fn writeSessionJson(
    request: *std.http.Server.Request,
    allocator: std.mem.Allocator,
    config: *const session_mod.SessionConfig,
    session: *const session_mod.SharedSession,
    set_cookie: bool,
) !void {
    const redis_key = try config.redisKey(allocator, session.session_id);
    defer allocator.free(redis_key);
    const body = try session.toResponseJson(allocator, redis_key);
    defer allocator.free(body);
    if (set_cookie) {
        const cookie = try sessionCookieHeader(allocator, config.cookie_name, session.session_id, @intCast(config.ttl_secs));
        defer allocator.free(cookie);
        const extra = [_]std.http.Header{.{ .name = "set-cookie", .value = cookie }};
        return try http.writeJsonResponseExtra(request, .ok, body, &extra);
    }
    return try http.writeJsonResponse(request, .ok, body);
}

pub fn writeSessionCleared(request: *std.http.Server.Request, allocator: std.mem.Allocator, config: *const session_mod.SessionConfig) !void {
    const cookie = try clearSessionCookieHeader(allocator, config.cookie_name);
    defer allocator.free(cookie);
    const extra = [_]std.http.Header{.{ .name = "set-cookie", .value = cookie }};
    return try http.writeNoContentResponse(request, &extra);
}

pub fn extractJsonString(allocator: std.mem.Allocator, json: []const u8, key: []const u8) ?[]const u8 {
    var needle_buf: [64]u8 = undefined;
    const needle = std.fmt.bufPrint(&needle_buf, "\"{s}\"", .{key}) catch return null;
    const key_pos = std.mem.indexOf(u8, json, needle) orelse return null;
    const after_key = json[key_pos + needle.len ..];
    const colon = std.mem.indexOfScalar(u8, after_key, ':') orelse return null;
    var rest = std.mem.trimLeft(u8, after_key[colon + 1 ..], " \t");
    if (rest.len == 0 or rest[0] != '"') return null;
    rest = rest[1..];
    const end = std.mem.indexOfScalar(u8, rest, '"') orelse return null;
    return allocator.dupe(u8, rest[0..end]) catch null;
}
