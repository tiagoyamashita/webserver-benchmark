const std = @import("std");

var seq: std.atomic.Value(u64) = std.atomic.Value(u64).init(0);

pub fn generate(allocator: std.mem.Allocator) ![]const u8 {
    const nanos = std.time.nanoTimestamp();
    const n = seq.fetchAdd(1, .monotonic);
    return std.fmt.allocPrint(allocator, "{x}-{x}", .{ nanos, n });
}

/// Match Java/Python/Rust: `^[a-zA-Z0-9._-]{8,64}$` or standard UUID.
pub fn isAcceptable(value: []const u8) bool {
    const trimmed = std.mem.trim(u8, value, " \t\r\n");
    if (trimmed.len == 0) return false;
    if (isUuid(trimmed)) return true;
    if (trimmed.len < 8 or trimmed.len > 64) return false;
    for (trimmed) |ch| {
        if (!isSafeTokenChar(ch)) return false;
    }
    return true;
}

/// Inbound HTTP: reuse valid header value or generate.
pub fn resolveInbound(allocator: std.mem.Allocator, header_value: []const u8) ![]const u8 {
    const trimmed = std.mem.trim(u8, header_value, " \t\r\n");
    if (isAcceptable(trimmed)) return allocator.dupe(u8, trimmed);
    return generate(allocator);
}

/// Outbound HTTP: reuse valid current id or generate.
pub fn resolveOutbound(allocator: std.mem.Allocator, current: ?[]const u8) ![]const u8 {
    if (current) |id| {
        const trimmed = std.mem.trim(u8, id, " \t\r\n");
        if (isAcceptable(trimmed)) return allocator.dupe(u8, trimmed);
    }
    return generate(allocator);
}

/// Postgres truncates application_name at 63 bytes (matches Java/Python/Rust).
pub fn postgresApplicationName(buf: []u8, service: []const u8, request_id: []const u8) []const u8 {
    const value = std.fmt.bufPrint(buf, "{s};req={s}", .{ service, request_id }) catch return service;
    if (value.len > 63) return value[0..63];
    return value;
}

fn isSafeTokenChar(ch: u8) bool {
    return std.ascii.isAlphanumeric(ch) or ch == '.' or ch == '_' or ch == '-';
}

fn isUuid(value: []const u8) bool {
    if (value.len != 36) return false;
    if (value[8] != '-' or value[13] != '-' or value[18] != '-' or value[23] != '-') return false;
    for (value) |ch| {
        if (!(std.ascii.isHex(ch) or ch == '-')) return false;
    }
    return true;
}

test "isAcceptable safe token and uuid" {
    try std.testing.expect(isAcceptable("abcd1234"));
    try std.testing.expect(isAcceptable("550e8400-e29b-41d4-a716-446655440000"));
    try std.testing.expect(!isAcceptable(""));
    try std.testing.expect(!isAcceptable("short"));
    try std.testing.expect(!isAcceptable("bad id!"));
}

test "resolveOutbound generates when missing" {
    const allocator = std.testing.allocator;
    const id = try resolveOutbound(allocator, null);
    defer allocator.free(id);
    try std.testing.expect(isAcceptable(id));
}
