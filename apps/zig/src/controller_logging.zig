const std = @import("std");
const obs = @import("observability_log.zig");

pub fn logReceived(handler: []const u8, source: []const u8, method: []const u8, path: []const u8) void {
    std.log.info("{s} request received source={s} method={s} path={s}", .{ handler, source, method, path });
    obs.info(handler, source, "request received", &.{
        .{ .key = "method", .value = method },
        .{ .key = "path", .value = path },
    });
}

pub fn logSucceeded(handler: []const u8, source: []const u8, comptime detail_fmt: []const u8, detail_args: anytype) void {
    std.log.info("{s} succeeded source={s} " ++ detail_fmt, .{ handler, source } ++ detail_args);
    var msg_buf: [512]u8 = undefined;
    const message = if (comptime detail_fmt.len == 0)
        std.fmt.bufPrint(&msg_buf, "{s} succeeded", .{handler}) catch handler
    else
        std.fmt.bufPrint(&msg_buf, "{s} succeeded " ++ detail_fmt, .{handler} ++ detail_args) catch handler;
    obs.info(handler, source, message, &.{});
}

pub fn logWarn(handler: []const u8, source: []const u8, comptime detail_fmt: []const u8, detail_args: anytype) void {
    std.log.warn("{s} source={s} " ++ detail_fmt, .{ handler, source } ++ detail_args);
    var msg_buf: [512]u8 = undefined;
    const message = std.fmt.bufPrint(&msg_buf, "{s} " ++ detail_fmt, .{handler} ++ detail_args) catch handler;
    obs.warn(handler, source, message, &.{});
}

pub fn logError(handler: []const u8, source: []const u8, comptime detail_fmt: []const u8, detail_args: anytype) void {
    std.log.err("{s} failed source={s} " ++ detail_fmt, .{ handler, source } ++ detail_args);
    var msg_buf: [512]u8 = undefined;
    const message = std.fmt.bufPrint(&msg_buf, "{s} failed " ++ detail_fmt, .{handler} ++ detail_args) catch handler;
    obs.err(handler, source, message, &.{});
}
