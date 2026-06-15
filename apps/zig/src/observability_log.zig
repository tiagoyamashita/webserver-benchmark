const std = @import("std");

const SERVICE = "exercises-zig";

var log_file: ?std.fs.File = null;
var log_mutex: std.Thread.Mutex = .{};
var enabled: bool = false;
var log_seq: u64 = 0;

pub const Field = union(enum) {
    string: struct {
        key: []const u8,
        value: []const u8,
    },
    json: struct {
        key: []const u8,
        value: []const u8,
    },
};

pub fn observabilityEnabled() bool {
    const v = std.posix.getenv("EXERCISES_OBSERVABILITY") orelse return false;
    return std.ascii.eqlIgnoreCase(v, "1") or
        std.ascii.eqlIgnoreCase(v, "true") or
        std.ascii.eqlIgnoreCase(v, "yes");
}

pub fn init(log_path: []const u8) !void {
    enabled = observabilityEnabled();
    if (!enabled) return;

    try std.fs.cwd().makePath(log_path);
    const file_path = try std.fmt.allocPrint(std.heap.page_allocator, "{s}/demo-app.json.log", .{log_path});
    defer std.heap.page_allocator.free(file_path);

    const file = try std.fs.cwd().createFile(file_path, .{ .read = true });
    errdefer file.close();
    try file.seekFromEnd(0);
    log_file = file;
}

pub fn deinit() void {
    if (log_file) |*file| {
        file.close();
        log_file = null;
    }
}

pub fn info(controller: []const u8, source: []const u8, message: []const u8, extra: []const Field) void {
    writeController("INFO", controller, source, message, extra);
}

pub fn warn(controller: []const u8, source: []const u8, message: []const u8, extra: []const Field) void {
    writeController("WARN", controller, source, message, extra);
}

pub fn err(controller: []const u8, source: []const u8, message: []const u8, extra: []const Field) void {
    writeController("ERROR", controller, source, message, extra);
}

pub fn httpInfo(logger: []const u8, message: []const u8, extra: []const Field) void {
    writeHttp("INFO", logger, message, extra);
}

pub fn httpWarn(logger: []const u8, message: []const u8, extra: []const Field) void {
    writeHttp("WARN", logger, message, extra);
}

fn writeHttp(level: []const u8, logger: []const u8, message: []const u8, extra: []const Field) void {
    if (!enabled or log_file == null) return;

    var line_buf: [8192]u8 = undefined;
    const line = formatHttpLine(&line_buf, level, logger, message, extra) orelse return;
    writeLine(line);
}

fn writeController(level: []const u8, controller: []const u8, source: []const u8, message: []const u8, extra: []const Field) void {
    if (!enabled or log_file == null) return;

    var line_buf: [8192]u8 = undefined;
    const line = formatControllerLine(&line_buf, level, controller, source, message, extra) orelse return;
    writeLine(line);
}

fn writeLine(line: []const u8) void {
    log_mutex.lock();
    defer log_mutex.unlock();
    const file = log_file.?;
    _ = file.writeAll(line) catch {};
    _ = file.writeAll("\n") catch {};
    _ = file.sync() catch {};
}

fn formatControllerLine(
    buf: []u8,
    level: []const u8,
    controller: []const u8,
    source: []const u8,
    message: []const u8,
    extra: []const Field,
) ?[]const u8 {
    var ts_buf: [64]u8 = undefined;
    const timestamp = formatUtcIso(&ts_buf);
    const seq = @atomicRmw(u64, &log_seq, .Add, 1, .monotonic);

    var fba = std.heap.FixedBufferAllocator.init(buf);
    const allocator = fba.allocator();
    var list = std.ArrayList(u8).init(allocator);
    list.appendSlice("{\"timestamp\":") catch return null;
    appendJsonString(&list, timestamp) catch return null;
    list.appendSlice(",\"level\":") catch return null;
    appendJsonString(&list, level) catch return null;
    list.appendSlice(",\"service\":") catch return null;
    appendJsonString(&list, SERVICE) catch return null;
    list.appendSlice(",\"controller\":") catch return null;
    appendJsonString(&list, controller) catch return null;
    list.appendSlice(",\"source\":") catch return null;
    appendJsonString(&list, source) catch return null;
    list.appendSlice(",\"message\":") catch return null;
    appendJsonString(&list, message) catch return null;
    std.fmt.format(list.writer(), ",\"log_seq\":{d}", .{seq}) catch return null;
    appendFields(&list, extra) catch return null;
    list.appendSlice("}") catch return null;
    return list.items;
}

fn formatHttpLine(
    buf: []u8,
    level: []const u8,
    logger: []const u8,
    message: []const u8,
    extra: []const Field,
) ?[]const u8 {
    var ts_buf: [64]u8 = undefined;
    const timestamp = formatUtcIso(&ts_buf);
    const seq = @atomicRmw(u64, &log_seq, .Add, 1, .monotonic);

    var fba = std.heap.FixedBufferAllocator.init(buf);
    const allocator = fba.allocator();
    var list = std.ArrayList(u8).init(allocator);
    list.appendSlice("{\"timestamp\":") catch return null;
    appendJsonString(&list, timestamp) catch return null;
    list.appendSlice(",\"level\":") catch return null;
    appendJsonString(&list, level) catch return null;
    list.appendSlice(",\"service\":") catch return null;
    appendJsonString(&list, SERVICE) catch return null;
    list.appendSlice(",\"logger\":") catch return null;
    appendJsonString(&list, logger) catch return null;
    list.appendSlice(",\"message\":") catch return null;
    appendJsonString(&list, message) catch return null;
    std.fmt.format(list.writer(), ",\"log_seq\":{d}", .{seq}) catch return null;
    appendFields(&list, extra) catch return null;
    list.appendSlice("}") catch return null;
    return list.items;
}

fn appendFields(list: *std.ArrayList(u8), extra: []const Field) !void {
    for (extra) |field| {
        switch (field) {
            .string => |entry| {
                try list.appendSlice(",\"");
                try list.appendSlice(entry.key);
                try list.appendSlice("\":");
                try appendJsonString(list, entry.value);
            },
            .json => |entry| {
                try list.appendSlice(",\"");
                try list.appendSlice(entry.key);
                try list.appendSlice("\":");
                try list.appendSlice(entry.value);
            },
        }
    }
}

fn formatUtcIso(buf: []u8) []const u8 {
    const secs = std.time.timestamp();
    const epoch_sec = std.time.epoch.EpochSeconds{ .secs = @intCast(secs) };
    const epoch_day = epoch_sec.getEpochDay();
    const day_sec = epoch_sec.getDaySeconds();
    const year_day = epoch_day.calculateYearDay();
    const month_day = year_day.calculateMonthDay();
    const ms: i64 = std.time.milliTimestamp() - @as(i64, @intCast(secs)) * 1000;

    return std.fmt.bufPrint(buf, "{d:0>4}-{d:0>2}-{d:0>2}T{d:0>2}:{d:0>2}:{d:0>2}.{d:0>3}Z", .{
        year_day.year,
        month_day.month.numeric(),
        month_day.day_index + 1,
        day_sec.getHoursIntoDay(),
        day_sec.getMinutesIntoHour(),
        day_sec.getSecondsIntoMinute(),
        @as(u16, @intCast(@max(ms, 0))),
    }) catch "1970-01-01T00:00:00.000Z";
}

fn appendJsonString(list: *std.ArrayList(u8), value: []const u8) !void {
    try list.append('"');
    for (value) |ch| {
        switch (ch) {
            '\\' => try list.appendSlice("\\\\"),
            '"' => try list.appendSlice("\\\""),
            '\n' => try list.appendSlice("\\n"),
            '\r' => try list.appendSlice("\\r"),
            '\t' => try list.appendSlice("\\t"),
            else => {
                if (ch < 0x20) {
                    try std.fmt.format(list.writer(), "\\u{:0>4}", .{ch});
                } else {
                    try list.append(ch);
                }
            },
        }
    }
    try list.append('"');
}
