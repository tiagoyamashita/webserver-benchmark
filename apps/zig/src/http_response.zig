const std = @import("std");

pub fn readBody(allocator: std.mem.Allocator, request: *std.http.Server.Request) ![]u8 {
    const length = request.head.content_length orelse 0;
    if (length == 0) return try allocator.dupe(u8, "");
    const body = try allocator.alloc(u8, length);
    errdefer allocator.free(body);
    var reader = try request.reader();
    var read_total: usize = 0;
    while (read_total < length) {
        const n = try reader.read(body[read_total..]);
        if (n == 0) break;
        read_total += n;
    }
    return body[0..read_total];
}

pub fn writeTextResponse(request: *std.http.Server.Request, status: std.http.Status, content_type: []const u8, body: []const u8) !void {
    const extra_headers = [_]std.http.Header{
        .{ .name = "content-type", .value = content_type },
        .{ .name = "connection", .value = "close" },
    };
    try request.respond(body, .{
        .status = status,
        .extra_headers = &extra_headers,
        .keep_alive = false,
    });
}

pub fn writeJsonResponse(request: *std.http.Server.Request, status: std.http.Status, body: []const u8) !void {
    const extra_headers = [_]std.http.Header{
        .{ .name = "content-type", .value = "application/json" },
        .{ .name = "connection", .value = "close" },
    };
    try request.respond(body, .{
        .status = status,
        .extra_headers = &extra_headers,
        .keep_alive = false,
    });
}

pub fn pathname(target: []const u8) []const u8 {
    const q = std.mem.indexOfScalar(u8, target, '?') orelse return target;
    return target[0..q];
}

pub fn targetOnly(path: []const u8) []const u8 {
    if (std.mem.startsWith(u8, path, "http://") or std.mem.startsWith(u8, path, "https://")) {
        const slash = std.mem.indexOfScalar(u8, path[8..], '/') orelse return "/";
        return path[8 + slash ..];
    }
    return path;
}
