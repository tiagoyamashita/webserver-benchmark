const std = @import("std");
const snap = @import("request_snapshot.zig");

pub fn readBody(allocator: std.mem.Allocator, request: *std.http.Server.Request) ![]u8 {
    if (snap.preReadBody()) |body| return try allocator.dupe(u8, body);
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
    snap.recordResponse(status, body, false);
    var extra_headers_buf: [3]std.http.Header = undefined;
    var extra_count: usize = 2;
    extra_headers_buf[0] = .{ .name = "content-type", .value = content_type };
    extra_headers_buf[1] = .{ .name = "connection", .value = "close" };
    if (snap.activeRequestId()) |request_id| {
        extra_headers_buf[extra_count] = .{ .name = "x-request-id", .value = request_id };
        extra_count += 1;
    }
    try request.respond(body, .{
        .status = status,
        .extra_headers = extra_headers_buf[0..extra_count],
        .keep_alive = false,
    });
}

pub fn writeJsonResponse(request: *std.http.Server.Request, status: std.http.Status, body: []const u8) !void {
    try writeJsonResponseExtra(request, status, body, &.{});
}

pub fn writeJsonResponseExtra(
    request: *std.http.Server.Request,
    status: std.http.Status,
    body: []const u8,
    extra: []const std.http.Header,
) !void {
    snap.recordResponse(status, body, true);
    var extra_headers_buf: [6]std.http.Header = undefined;
    var extra_count: usize = 2;
    extra_headers_buf[0] = .{ .name = "content-type", .value = "application/json" };
    extra_headers_buf[1] = .{ .name = "connection", .value = "close" };
    if (snap.activeRequestId()) |request_id| {
        extra_headers_buf[extra_count] = .{ .name = "x-request-id", .value = request_id };
        extra_count += 1;
    }
    for (extra) |header| {
        extra_headers_buf[extra_count] = header;
        extra_count += 1;
    }
    try request.respond(body, .{
        .status = status,
        .extra_headers = extra_headers_buf[0..extra_count],
        .keep_alive = false,
    });
}

pub fn writeNoContentResponse(request: *std.http.Server.Request, extra: []const std.http.Header) !void {
    snap.recordResponse(.no_content, "", false);
    var extra_headers_buf: [5]std.http.Header = undefined;
    var extra_count: usize = 1;
    extra_headers_buf[0] = .{ .name = "connection", .value = "close" };
    if (snap.activeRequestId()) |request_id| {
        extra_headers_buf[extra_count] = .{ .name = "x-request-id", .value = request_id };
        extra_count += 1;
    }
    for (extra) |header| {
        extra_headers_buf[extra_count] = header;
        extra_count += 1;
    }
    try request.respond("", .{
        .status = .no_content,
        .extra_headers = extra_headers_buf[0..extra_count],
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
