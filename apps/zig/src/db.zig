const std = @import("std");
const c = @cImport({
    @cInclude("libpq-fe.h");
});
const postgres_log = @import("postgres_log.zig");
const request_id_mod = @import("request_id.zig");
const snap = @import("request_snapshot.zig");

const SERVICE = "exercises-zig";

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

    fn stampApplicationName(self: *Db) DbError!void {
        const conn = self.conn orelse return error.DatabaseNotConfigured;
        var app_name_buf: [64]u8 = undefined;
        const app_name = blk: {
            if (snap.activeRequestId()) |request_id| {
                break :blk request_id_mod.postgresApplicationName(&app_name_buf, SERVICE, request_id);
            }
            break :blk SERVICE;
        };
        const sql = "SELECT set_config('application_name', $1, false)";
        var name_z: [64]u8 = undefined;
        @memcpy(name_z[0..app_name.len], app_name);
        name_z[app_name.len] = 0;
        const name_ptr: *const u8 = &name_z[0];
        const param_values = [_]?*const u8{name_ptr};
        const param_lengths = [_]c_int{@intCast(app_name.len)};
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
    }

    pub fn listItems(self: *Db, allocator: std.mem.Allocator) DbError![]Item {
        const conn = self.conn orelse return error.DatabaseNotConfigured;
        const operation = "list_items";
        const sql = "SELECT id, name, created_at::text FROM items ORDER BY id";
        try self.stampApplicationName();
        postgres_log.logQuery(operation, sql);
        // PQexecParams (extended protocol) so Postgres logs "execute …" lines that
        // survive Logstash filtering; PQexec simple queries log as "statement:" and are dropped.
        const result = c.PQexecParams(
            conn,
            sql,
            0,
            null,
            null,
            null,
            null,
            0,
        );
        if (result == null) return error.QueryFailed;
        defer c.PQclear(result);

        const status = c.PQresultStatus(result);
        if (status != c.PGRES_TUPLES_OK) {
            postgres_log.logQueryFailed(operation, sql, std.mem.span(c.PQerrorMessage(conn)));
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
        const operation = "insert_item";
        const sql = "INSERT INTO items (name, created_at) VALUES ($1, NOW()) RETURNING id, name, created_at::text";
        try self.stampApplicationName();
        postgres_log.logQuery(operation, sql);
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
            postgres_log.logQueryFailed(operation, sql, std.mem.span(c.PQerrorMessage(conn)));
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
        const operation = "get_item";
        const sql = "SELECT id, name, created_at::text FROM items WHERE id = $1";
        try self.stampApplicationName();
        postgres_log.logQuery(operation, sql);
        var id_buf: [32]u8 = undefined;
        const id_text = std.fmt.bufPrint(&id_buf, "{d}", .{item_id}) catch return error.QueryFailed;
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

        if (c.PQresultStatus(result) != c.PGRES_TUPLES_OK) {
            postgres_log.logQueryFailed(operation, sql, std.mem.span(c.PQerrorMessage(conn)));
            return error.QueryFailed;
        }
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
        const operation = "delete_item";
        const sql = "DELETE FROM items WHERE id = $1";
        var id_buf: [32]u8 = undefined;
        const id_text = std.fmt.bufPrint(&id_buf, "{d}", .{item_id}) catch return error.QueryFailed;
        const id_ptr: *const u8 = &id_buf[0];
        const param_values = [_]?*const u8{id_ptr};
        const param_lengths = [_]c_int{@intCast(id_text.len)};
        const param_formats = [_]c_int{0};
        try self.stampApplicationName();
        postgres_log.logQuery(operation, sql);
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
        if (c.PQresultStatus(result) != c.PGRES_COMMAND_OK) {
            postgres_log.logQueryFailed(operation, sql, std.mem.span(c.PQerrorMessage(conn)));
            return error.QueryFailed;
        }
        const affected = c.PQcmdTuples(result);
        return std.fmt.parseInt(usize, std.mem.span(affected), 10) catch 0 > 0;
    }
};
