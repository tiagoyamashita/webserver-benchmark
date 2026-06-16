const std = @import("std");
const redis_mod = @import("../redis_client.zig");
const session_mod = @import("session.zig");

pub fn save(
    client: *redis_mod.Client,
    config: *const session_mod.SessionConfig,
    allocator: std.mem.Allocator,
    session: *const session_mod.SharedSession,
) !void {
    const key = try config.redisKey(allocator, session.session_id);
    defer allocator.free(key);
    const json = try session.toJson(allocator);
    defer allocator.free(json);
    try client.setEx(key, json, config.ttl_secs);
}

pub fn findById(
    client: *redis_mod.Client,
    config: *const session_mod.SessionConfig,
    allocator: std.mem.Allocator,
    session_id: []const u8,
) !?session_mod.SharedSession {
    const trimmed = std.mem.trim(u8, session_id, " \t\r\n");
    if (trimmed.len == 0) return null;
    const key = try config.redisKey(allocator, trimmed);
    defer allocator.free(key);
    const raw = try client.get(allocator, key);
    const json = raw orelse return null;
    defer allocator.free(json);
    return try session_mod.SharedSession.parse(allocator, json);
}

pub fn deleteSession(
    client: *redis_mod.Client,
    config: *const session_mod.SessionConfig,
    allocator: std.mem.Allocator,
    session_id: []const u8,
) !void {
    const trimmed = std.mem.trim(u8, session_id, " \t\r\n");
    if (trimmed.len == 0) return;
    const key = try config.redisKey(allocator, trimmed);
    defer allocator.free(key);
    try client.del(key);
}
