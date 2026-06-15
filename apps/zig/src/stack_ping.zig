const std = @import("std");
const http = std.http;
const log = @import("controller_logging.zig");

const SOURCE = "src/stack_ping.zig";

pub const Probe = struct {
    name: []const u8,
    ok: bool,
};

const ProbeCtx = struct {
    allocator: std.mem.Allocator,
    name: []const u8,
    base_url: []const u8,
    extra_path: ?[]const u8,
    ok: *bool,
};

/// GET probe — matches Java/Rust/Python `emptyGet`: root URL for most services,
/// explicit path only for react-node (`/api/health`).
pub fn probeHealth(allocator: std.mem.Allocator, service: []const u8, base_url: []const u8, extra_path: ?[]const u8) bool {
    const url = buildProbeUrl(allocator, base_url, extra_path) catch {
        log.logWarn("stack_probe", SOURCE, "service={s} base_url={s} error=build_url_failed", .{ service, base_url });
        return false;
    };
    defer allocator.free(url);

    var client = http.Client{ .allocator = allocator };
    defer client.deinit();

    const result = client.fetch(.{
        .location = .{ .url = url },
        .method = .GET,
        .response_storage = .{ .ignore = {} },
        .keep_alive = false,
    }) catch {
        log.logWarn("stack_probe", SOURCE, "service={s} target={s} error=fetch_failed", .{ service, url });
        return false;
    };

    const status_code: u16 = @intCast(@intFromEnum(result.status));
    const ok = status_code >= 200 and status_code < 300;
    if (ok) {
        log.logSucceeded("stack_probe", SOURCE, "service={s} target={s} status={d}", .{ service, url, status_code });
    } else {
        log.logWarn("stack_probe", SOURCE, "service={s} target={s} status={d}", .{ service, url, status_code });
    }
    return ok;
}

fn runProbe(ctx: *const ProbeCtx) void {
    ctx.ok.* = probeHealth(ctx.allocator, ctx.name, ctx.base_url, ctx.extra_path);
}

fn buildProbeUrl(allocator: std.mem.Allocator, base_url: []const u8, extra_path: ?[]const u8) ![]const u8 {
    const trimmed_base = std.mem.trimRight(u8, base_url, "/");
    if (extra_path) |suffix| {
        const trimmed_suffix = std.mem.trimLeft(u8, suffix, "/");
        return std.fmt.allocPrint(allocator, "{s}/{s}", .{ trimmed_base, trimmed_suffix });
    }
    return std.fmt.allocPrint(allocator, "{s}/", .{trimmed_base});
}

pub fn stackProbes(
    allocator: std.mem.Allocator,
    java_url: []const u8,
    python_url: []const u8,
    rust_url: []const u8,
    react_url: []const u8,
    prometheus_url: []const u8,
    grafana_url: []const u8,
    elasticsearch_url: []const u8,
    kibana_url: []const u8,
) ![]Probe {
    log.logReceived("stack_probes", SOURCE, "GET", "/htmx/stack");
    std.log.info(
        "stack_probes config source={s} java={s} python={s} rust={s} react_node={s} prometheus={s} grafana={s} elasticsearch={s} kibana={s}",
        .{ SOURCE, java_url, python_url, rust_url, react_url, prometheus_url, grafana_url, elasticsearch_url, kibana_url },
    );

    const specs = [_]struct { []const u8, []const u8, ?[]const u8 }{
        .{ "java", java_url, null },
        .{ "python", python_url, null },
        .{ "rust", rust_url, null },
        .{ "react-node", react_url, "/api/health" },
        .{ "prometheus", prometheus_url, null },
        .{ "grafana", grafana_url, null },
        .{ "elasticsearch", elasticsearch_url, null },
        .{ "kibana", kibana_url, null },
    };

    var probes = try allocator.alloc(Probe, specs.len);
    var results: [specs.len]bool = undefined;
    var ctxs: [specs.len]ProbeCtx = undefined;
    var threads: [specs.len]std.Thread = undefined;

    for (specs, 0..) |spec, i| {
        ctxs[i] = .{
            .allocator = allocator,
            .name = spec[0],
            .base_url = spec[1],
            .extra_path = spec[2],
            .ok = &results[i],
        };
        threads[i] = try std.Thread.spawn(.{}, runProbe, .{&ctxs[i]});
    }
    for (threads) |thread| thread.join();

    var up_count: usize = 0;
    for (specs, results, 0..) |spec, ok, i| {
        probes[i] = .{ .name = spec[0], .ok = ok };
        if (ok) up_count += 1;
    }

    log.logSucceeded("stack_probes", SOURCE, "up={d} down={d}", .{ up_count, specs.len - up_count });
    return probes;
}

pub fn renderProbeRows(allocator: std.mem.Allocator, probes: []const Probe) ![]u8 {
    var list = std.ArrayList(u8).init(allocator);
    errdefer list.deinit();
    for (probes) |probe| {
        const status = if (probe.ok) "<span class=\"ok\">up</span>" else "<span class=\"err\">down</span>";
        try list.writer().print("<tr><td>{s}</td><td>{s}</td></tr>", .{ probe.name, status });
    }
    return list.toOwnedSlice();
}
