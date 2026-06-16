const std = @import("std");

/// Opening tag for htmx partial responses (must match templates/views/items.html id only).
/// Do not include hx-trigger="load" here — outerHTML swap would re-fire load in a loop.
pub const items_tbody_open =
    \\<tbody id="items-body">
;

pub const ItemsStatus = struct {
    kind: Kind,
    message: []const u8,

    pub const Kind = enum {
        success,
        failure,
    };
};

pub fn escapeHtml(allocator: std.mem.Allocator, input: []const u8) ![]u8 {
    var list = std.ArrayList(u8).init(allocator);
    errdefer list.deinit();
    for (input) |ch| {
        switch (ch) {
            '&' => try list.appendSlice("&amp;"),
            '<' => try list.appendSlice("&lt;"),
            '>' => try list.appendSlice("&gt;"),
            '"' => try list.appendSlice("&quot;"),
            '\'' => try list.appendSlice("&#39;"),
            else => try list.append(ch),
        }
    }
    return list.toOwnedSlice();
}

pub fn renderStatusRow(allocator: std.mem.Allocator, kind: ItemsStatus.Kind, message: []const u8) ![]u8 {
    const safe = try escapeHtml(allocator, message);
    defer allocator.free(safe);
    const class_name: []const u8 = switch (kind) {
        .success => "ok",
        .failure => "err",
    };
    return std.fmt.allocPrint(
        allocator,
        "<tr id=\"items-status-row\"><td colspan=\"3\" class=\"{s}\">{s}</td></tr>",
        .{ class_name, safe },
    );
}

pub fn renderItemsRows(allocator: std.mem.Allocator, items: []const @import("db.zig").Item) ![]u8 {
    var list = std.ArrayList(u8).init(allocator);
    errdefer list.deinit();
    if (items.len == 0) {
        try list.appendSlice("<tr><td colspan=\"3\" class=\"muted\">No items yet.</td></tr>");
        return list.toOwnedSlice();
    }
    for (items) |item| {
        const safe_name = try escapeHtml(allocator, item.name);
        defer allocator.free(safe_name);
        const safe_created = try escapeHtml(allocator, item.created_at);
        defer allocator.free(safe_created);
        try list.writer().print(
            "<tr><td>{d}</td><td>{s}</td><td><code>{s}</code></td></tr>",
            .{ item.id, safe_name, safe_created },
        );
    }
    return list.toOwnedSlice();
}

pub fn renderItemsBody(allocator: std.mem.Allocator, items: []const @import("db.zig").Item) ![]u8 {
    return renderItemsBodyWithStatus(allocator, items, null);
}

pub fn renderItemsBodyWithStatus(
    allocator: std.mem.Allocator,
    items: []const @import("db.zig").Item,
    status: ?ItemsStatus,
) ![]u8 {
    const status_row_owned = if (status) |s|
        try renderStatusRow(allocator, s.kind, s.message)
    else
        @as([]u8, "");
    defer if (status_row_owned.len > 0) allocator.free(status_row_owned);

    const rows = try renderItemsRows(allocator, items);
    defer allocator.free(rows);
    return std.fmt.allocPrint(allocator, "{s}{s}{s}</tbody>", .{ items_tbody_open, status_row_owned, rows });
}

pub fn renderItemsMessageBody(allocator: std.mem.Allocator, kind: ItemsStatus.Kind, message: []const u8) ![]u8 {
    const status_row = try renderStatusRow(allocator, kind, message);
    defer allocator.free(status_row);
    return std.fmt.allocPrint(allocator, "{s}{s}</tbody>", .{ items_tbody_open, status_row });
}

pub fn parseFormName(body: []const u8) ?[]const u8 {
    var it = std.mem.splitScalar(u8, body, '&');
    while (it.next()) |pair| {
        if (std.mem.eql(u8, pair, "name=")) return "";
        if (std.mem.startsWith(u8, pair, "name=")) {
            return pair["name=".len..];
        }
    }
    return null;
}

pub fn urlDecodeInPlace(buf: []u8) []u8 {
    var read: usize = 0;
    var write: usize = 0;
    while (read < buf.len) : (read += 1) {
        if (buf[read] == '%' and read + 2 < buf.len) {
            const hi = hexVal(buf[read + 1]) orelse {
                buf[write] = buf[read];
                write += 1;
                continue;
            };
            const lo = hexVal(buf[read + 2]) orelse {
                buf[write] = buf[read];
                write += 1;
                continue;
            };
            buf[write] = @intCast((hi << 4) + lo);
            write += 1;
            read += 2;
        } else if (buf[read] == '+') {
            buf[write] = ' ';
            write += 1;
        } else {
            buf[write] = buf[read];
            write += 1;
        }
    }
    return buf[0..write];
}

fn hexVal(ch: u8) ?u8 {
    return switch (ch) {
        '0'...'9' => ch - '0',
        'a'...'f' => ch - 'a' + 10,
        'A'...'F' => ch - 'A' + 10,
        else => null,
    };
}
