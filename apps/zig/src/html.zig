const std = @import("std");

/// Opening tag for htmx partial responses (must match templates/views/items.html id only).
/// Do not include hx-trigger="load" here — outerHTML swap would re-fire load in a loop.
pub const items_tbody_open =
    \\<tbody id="items-body">
;

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
    const rows = try renderItemsRows(allocator, items);
    defer allocator.free(rows);
    return std.fmt.allocPrint(allocator, "{s}{s}</tbody>", .{ items_tbody_open, rows });
}

pub fn renderItemsMessageBody(allocator: std.mem.Allocator, message: []const u8) ![]u8 {
    const row = try std.fmt.allocPrint(
        allocator,
        "<tr><td colspan=\"3\">{s}</td></tr>",
        .{message},
    );
    defer allocator.free(row);
    return std.fmt.allocPrint(allocator, "{s}{s}</tbody>", .{ items_tbody_open, row });
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
