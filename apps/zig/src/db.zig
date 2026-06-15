const std = @import("std");
const c = @cImport({
    @cInclude("libpq-fe.h");
});

pub const DbError = error{
    DatabaseNotConfigured,
    ConnectionFailed,
    QueryFailed,
    OutOfMemory,
};

pub const Item = struct {
    id: i64,
    name: []const u8,
    created_at: []const u8,
};

pub const Db = struct {
    conn: ?*c.PGconn,

    pub fn connect(allocator: std.mem.Allocator, conninfo: []const u8) DbError!Db {
        const conninfo_z = try allocator.allocSentinel(u8, conninfo.len, 0);
        defer allocator.free(conninfo_z);
        @memcpy(conninfo_z, conninfo);
        const conn = c.PQconnectdb(conninfo_z.ptr);
        if (conn == null) return error.ConnectionFailed;
        if (c.PQstatus(conn) != c.CONNECTION_OK) {
            const msg = c.PQerrorMessage(conn);
            std.log.err("postgres connect failed: {s}", .{msg});
            c.PQfinish(conn);
            return error.ConnectionFailed;
        }
        return .{ .conn = conn };
    }

    pub fn deinit(self: *Db) void {
        if (self.conn) |conn| c.PQfinish(conn);
        self.conn = null;
    }

    pub fn listItems(self: *Db, allocator: std.mem.Allocator) DbError![]Item {
        const conn = self.conn orelse return error.DatabaseNotConfigured;
        const sql = "SELECT id, name, created_at::text FROM items ORDER BY id";
        const result = c.PQexec(conn, sql);
        if (result == null) return error.QueryFailed;
        defer c.PQclear(result);

        const status = c.PQresultStatus(result);
        if (status != c.PGRES_TUPLES_OK) {
            std.log.err("list items failed: {s}", .{c.PQerrorMessage(conn)});
            return error.QueryFailed;
        }

        const count = c.PQntuples(result);
        var items = try allocator.alloc(Item, @intCast(count));
        errdefer allocator.free(items);

        var i: usize = 0;
        while (i < items.len) : (i += 1) {
            const row: c_int = @intCast(i);
            const id_text = c.PQgetvalue(result, row, 0) orelse return error.QueryFailed;
            const name_text = c.PQgetvalue(result, row, 1) orelse return error.QueryFailed;
            const created_text = c.PQgetvalue(result, row, 2) orelse return error.QueryFailed;
            items[i] = .{
                .id = std.fmt.parseInt(i64, std.mem.span(id_text), 10) catch return error.QueryFailed,
                .name = try allocator.dupe(u8, std.mem.span(name_text)),
                .created_at = try allocator.dupe(u8, std.mem.span(created_text)),
            };
        }
        return items;
    }

    pub fn freeItems(items: []Item, allocator: std.mem.Allocator) void {
        for (items) |item| {
            allocator.free(item.name);
            allocator.free(item.created_at);
        }
        allocator.free(items);
    }

    pub fn insertItem(self: *Db, allocator: std.mem.Allocator, name: []const u8) DbError!Item {
        const conn = self.conn orelse return error.DatabaseNotConfigured;
        const sql = "INSERT INTO items (name, created_at) VALUES ($1, NOW()) RETURNING id, name, created_at::text";
        const name_z = try allocator.allocSentinel(u8, name.len, 0);
        defer allocator.free(name_z);
        @memcpy(name_z, name);
        const name_ptr: *const u8 = &name_z[0];
        const param_values = [_]?*const u8{name_ptr};
        const param_lengths = [_]c_int{@intCast(name.len)};
        const param_formats = [_]c_int{0};
        const result = c.PQexecParams(
            conn,
            sql,
            1,
            null,
            &param_values,
            &param_lengths,
            &param_formats,
            0,
        );
        if (result == null) return error.QueryFailed;
        defer c.PQclear(result);

        if (c.PQresultStatus(result) != c.PGRES_TUPLES_OK) {
            std.log.err("insert item failed: {s}", .{c.PQerrorMessage(conn)});
            return error.QueryFailed;
        }

        const id_text = c.PQgetvalue(result, 0, 0) orelse return error.QueryFailed;
        const name_text = c.PQgetvalue(result, 0, 1) orelse return error.QueryFailed;
        const created_text = c.PQgetvalue(result, 0, 2) orelse return error.QueryFailed;

        return .{
            .id = std.fmt.parseInt(i64, std.mem.span(id_text), 10) catch return error.QueryFailed,
            .name = try allocator.dupe(u8, std.mem.span(name_text)),
            .created_at = try allocator.dupe(u8, std.mem.span(created_text)),
        };
    }

    pub fn getItem(self: *Db, allocator: std.mem.Allocator, item_id: i64) DbError!?Item {
        const conn = self.conn orelse return error.DatabaseNotConfigured;
        var id_buf: [32]u8 = undefined;
        const id_text = std.fmt.bufPrint(&id_buf, "{d}", .{item_id}) catch return error.QueryFailed;
        const sql = "SELECT id, name, created_at::text FROM items WHERE id = $1";
        const id_ptr: *const u8 = &id_buf[0];
        const param_values = [_]?*const u8{id_ptr};
        const param_lengths = [_]c_int{@intCast(id_text.len)};
        const param_formats = [_]c_int{0};
        const result = c.PQexecParams(
            conn,
            sql,
            1,
            null,
            &param_values,
            &param_lengths,
            &param_formats,
            0,
        );
        if (result == null) return error.QueryFailed;
        defer c.PQclear(result);

        if (c.PQresultStatus(result) != c.PGRES_TUPLES_OK) return error.QueryFailed;
        if (c.PQntuples(result) == 0) return null;

        const id_val = c.PQgetvalue(result, 0, 0) orelse return error.QueryFailed;
        const name_val = c.PQgetvalue(result, 0, 1) orelse return error.QueryFailed;
        const created_val = c.PQgetvalue(result, 0, 2) orelse return error.QueryFailed;
        return .{
            .id = std.fmt.parseInt(i64, std.mem.span(id_val), 10) catch return error.QueryFailed,
            .name = try allocator.dupe(u8, std.mem.span(name_val)),
            .created_at = try allocator.dupe(u8, std.mem.span(created_val)),
        };
    }

    pub fn deleteItem(self: *Db, item_id: i64) DbError!bool {
        const conn = self.conn orelse return error.DatabaseNotConfigured;
        var id_buf: [32]u8 = undefined;
        const id_text = std.fmt.bufPrint(&id_buf, "{d}", .{item_id}) catch return error.QueryFailed;
        const sql = "DELETE FROM items WHERE id = $1";
        const id_ptr: *const u8 = &id_buf[0];
        const param_values = [_]?*const u8{id_ptr};
        const param_lengths = [_]c_int{@intCast(id_text.len)};
        const param_formats = [_]c_int{0};
        const result = c.PQexecParams(
            conn,
            sql,
            1,
            null,
            &param_values,
            &param_lengths,
            &param_formats,
            0,
        );
        if (result == null) return error.QueryFailed;
        defer c.PQclear(result);
        if (c.PQresultStatus(result) != c.PGRES_COMMAND_OK) return error.QueryFailed;
        const affected = c.PQcmdTuples(result);
        return std.fmt.parseInt(usize, std.mem.span(affected), 10) catch 0 > 0;
    }
};
