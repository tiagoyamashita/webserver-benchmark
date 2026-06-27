const std = @import("std");

pub const Config = struct {
    host: []const u8,
    port: u16,
    db_host: ?[]const u8,
    db_port: u16,
    db_name: []const u8,
    db_user: []const u8,
    db_password: []const u8,
    log_path: []const u8,
    java_base_url: []const u8,
    python_base_url: []const u8,
    rust_base_url: []const u8,
    react_node_base_url: []const u8,
    prometheus_base_url: []const u8,
    grafana_base_url: []const u8,
    elasticsearch_base_url: []const u8,
    kibana_base_url: []const u8,

    pub fn fromEnv(allocator: std.mem.Allocator) !Config {
        const host = try dupEnv(allocator, "WEBSERVER_BENCHMARK_WEB_HOST", "0.0.0.0");
        errdefer allocator.free(host);
        const port = parseU16Env("WEBSERVER_BENCHMARK_WEB_PORT", 8083);
        const db_host_raw = std.posix.getenv("DB_HOST");
        const db_host: ?[]const u8 = if (db_host_raw) |h| blk: {
            const trimmed = trimEnv(h);
            if (trimmed.len == 0) break :blk null;
            break :blk try allocator.dupe(u8, trimmed);
        } else null;
        errdefer if (db_host) |h| allocator.free(h);

        const db_name = try dupEnv(allocator, "DB_NAME", "demo");
        errdefer allocator.free(db_name);
        const db_user = try dupEnv(allocator, "DB_USERNAME", "postgres");
        errdefer allocator.free(db_user);
        const db_password = try dupEnv(allocator, "DB_PASSWORD", "postgres");
        errdefer allocator.free(db_password);
        const log_path = try dupEnv(allocator, "LOG_PATH", "logs");
        errdefer allocator.free(log_path);
        const java_base_url = try dupEnv(allocator, "APP_STACK_JAVA_BASE_URL", "http://java:8080");
        errdefer allocator.free(java_base_url);
        const python_base_url = try dupEnv(allocator, "APP_STACK_PYTHON_BASE_URL", "http://python:5000");
        errdefer allocator.free(python_base_url);
        const rust_base_url = try dupEnv(allocator, "APP_STACK_RUST_BASE_URL", "http://rust:8082");
        errdefer allocator.free(rust_base_url);
        const react_node_base_url = try dupEnv(allocator, "APP_STACK_REACT_NODE_BASE_URL", "http://react-node:5174");
        errdefer allocator.free(react_node_base_url);
        const prometheus_base_url = try dupEnv(allocator, "APP_STACK_PROMETHEUS_BASE_URL", "http://prometheus:9090");
        errdefer allocator.free(prometheus_base_url);
        const grafana_base_url = try dupEnv(allocator, "APP_STACK_GRAFANA_BASE_URL", "http://grafana:3000");
        errdefer allocator.free(grafana_base_url);
        const elasticsearch_base_url = try dupEnv(allocator, "APP_STACK_ELASTICSEARCH_BASE_URL", "http://elasticsearch:9200");
        errdefer allocator.free(elasticsearch_base_url);
        const kibana_base_url = try dupEnv(allocator, "APP_STACK_KIBANA_BASE_URL", "http://kibana:5601");
        errdefer allocator.free(kibana_base_url);

        return .{
            .host = host,
            .port = port,
            .db_host = db_host,
            .db_port = parseU16Env("DB_PORT", 5432),
            .db_name = db_name,
            .db_user = db_user,
            .db_password = db_password,
            .log_path = log_path,
            .java_base_url = java_base_url,
            .python_base_url = python_base_url,
            .rust_base_url = rust_base_url,
            .react_node_base_url = react_node_base_url,
            .prometheus_base_url = prometheus_base_url,
            .grafana_base_url = grafana_base_url,
            .elasticsearch_base_url = elasticsearch_base_url,
            .kibana_base_url = kibana_base_url,
        };
    }

    pub fn deinit(self: *Config, allocator: std.mem.Allocator) void {
        allocator.free(self.host);
        if (self.db_host) |h| allocator.free(h);
        allocator.free(self.db_name);
        allocator.free(self.db_user);
        allocator.free(self.db_password);
        allocator.free(self.log_path);
        allocator.free(self.java_base_url);
        allocator.free(self.python_base_url);
        allocator.free(self.rust_base_url);
        allocator.free(self.react_node_base_url);
        allocator.free(self.prometheus_base_url);
        allocator.free(self.grafana_base_url);
        allocator.free(self.elasticsearch_base_url);
        allocator.free(self.kibana_base_url);
    }

    pub fn databaseConfigured(self: *const Config) bool {
        return self.db_host != null and self.db_host.?.len > 0;
    }

    pub fn connectionString(self: *const Config, allocator: std.mem.Allocator) ![]u8 {
        const host = self.db_host orelse return error.DatabaseNotConfigured;
        const password = try quoteConnValue(allocator, self.db_password);
        defer allocator.free(password);
        return std.fmt.allocPrint(
            allocator,
            "host={s} port={d} dbname={s} user={s} password={s}",
            .{ host, self.db_port, self.db_name, self.db_user, password },
        );
    }
};

fn trimEnv(value: []const u8) []const u8 {
    return std.mem.trim(u8, value, " \t\r\n");
}

/// libpq keyword values with spaces or quotes must be single-quoted.
fn quoteConnValue(allocator: std.mem.Allocator, value: []const u8) ![]u8 {
    var needs_quotes = false;
    for (value) |ch| {
        if (ch == ' ' or ch == '\'' or ch == '\\') {
            needs_quotes = true;
            break;
        }
    }
    if (!needs_quotes) return try allocator.dupe(u8, value);

    var list = std.ArrayList(u8).init(allocator);
    errdefer list.deinit();
    try list.append('\'');
    for (value) |ch| {
        if (ch == '\'') try list.appendSlice("\\'");
        try list.append(ch);
    }
    try list.append('\'');
    return list.toOwnedSlice();
}

fn dupEnv(allocator: std.mem.Allocator, key: []const u8, default: []const u8) ![]u8 {
    if (std.posix.getenv(key)) |value| {
        const trimmed = trimEnv(value);
        if (trimmed.len > 0) return try allocator.dupe(u8, trimmed);
    }
    return try allocator.dupe(u8, default);
}

fn parseU16Env(key: []const u8, default: u16) u16 {
    const raw = std.posix.getenv(key) orelse return default;
    const trimmed = trimEnv(raw);
    if (trimmed.len == 0) return default;
    return std.fmt.parseInt(u16, trimmed, 10) catch default;
}
