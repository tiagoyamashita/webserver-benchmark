const std = @import("std");
const obs = @import("observability_log.zig");
const snap = @import("request_snapshot.zig");

const quiet_get_paths = [_][]const u8{"/metrics"};

pub fn shouldLogHttpAccess(method: []const u8, path: []const u8, status: ?u16) bool {
    const pathname = requestPathname(path);
    if (std.ascii.eqlIgnoreCase(method, "GET") and isQuietGetPath(pathname)) {
        return status != null and status.? != 200;
    }
    return true;
}

pub fn logReceived(ctx: *const snap.Context) void {
    if (!shouldLogHttpAccess(ctx.method, ctx.path, null)) return;
    var msg_buf: [256]u8 = undefined;
    const message = std.fmt.bufPrint(&msg_buf, "{s} {s} request received", .{ ctx.method, ctx.path }) catch return;
    var fields_buf: [8]obs.Field = undefined;
    const fields = appendRequestId(&fields_buf, &.{
        .{ .string = .{ .key = "method", .value = ctx.method } },
        .{ .string = .{ .key = "path", .value = ctx.path } },
        .{ .string = .{ .key = "phase", .value = "received" } },
        .{ .json = .{ .key = "headers", .value = ctx.headers_json } },
        .{ .json = .{ .key = "url_params", .value = ctx.url_params_json } },
        .{ .json = .{ .key = "body", .value = ctx.body_json } },
    });
    obs.httpInfo("http.request", message, fields);
}

pub fn logCompleted(ctx: *const snap.Context) void {
    const status: u16 = @intFromEnum(ctx.response_status);
    if (!shouldLogHttpAccess(ctx.method, ctx.path, status)) return;
    var status_buf: [8]u8 = undefined;
    const status_text = std.fmt.bufPrint(&status_buf, "{d}", .{status}) catch "0";
    var ms_buf: [24]u8 = undefined;
    const ms_text = std.fmt.bufPrint(&ms_buf, "{d}", .{snap.elapsedMs(ctx)}) catch "0";
    var msg_buf: [256]u8 = undefined;
    const message = std.fmt.bufPrint(&msg_buf, "{s} {s} {s}", .{ ctx.method, ctx.path, status_text }) catch return;

    var fields_buf: [8]obs.Field = undefined;
    var base: [6]obs.Field = .{
        .{ .string = .{ .key = "method", .value = ctx.method } },
        .{ .string = .{ .key = "path", .value = ctx.path } },
        .{ .string = .{ .key = "status", .value = status_text } },
        .{ .string = .{ .key = "ms", .value = ms_text } },
        .{ .string = .{ .key = "phase", .value = "completed" } },
        undefined,
    };
    var field_count: usize = 5;
    if (ctx.response_body_json) |body| {
        base[field_count] = .{ .json = .{ .key = "body", .value = body } };
        field_count += 1;
    }
    const fields = appendRequestId(&fields_buf, base[0..field_count]);
    obs.httpInfo("http.request", message, fields);
}

fn requestPathname(path: []const u8) []const u8 {
    const q = std.mem.indexOfScalar(u8, path, '?') orelse return path;
    return path[0..q];
}

fn isQuietGetPath(pathname: []const u8) bool {
    for (quiet_get_paths) |quiet| {
        if (std.mem.eql(u8, pathname, quiet)) return true;
    }
    return false;
}

fn appendRequestId(out: *[8]obs.Field, base: []const obs.Field) []const obs.Field {
    var count: usize = 0;
    if (snap.activeRequestId()) |request_id| {
        out[count] = .{ .string = .{ .key = "request_id", .value = request_id } };
        count += 1;
    }
    for (base) |field| {
        if (count >= out.len) break;
        out[count] = field;
        count += 1;
    }
    return out[0..count];
}
