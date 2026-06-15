const std = @import("std");
const outbound = @import("outbound_http.zig");
const log = @import("controller_logging.zig");
const request_id_mod = @import("request_id.zig");
const snap = @import("request_snapshot.zig");
const req_ctx = @import("request_context.zig");

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
    origin_method: ?[]const u8,
    origin_path: ?[]const u8,
    origin_request_id: ?[]const u8,
};

/// GET probe — matches Java/Rust/Python `emptyGet`: root URL for most services,
/// explicit path only for react-node (`/api/health`).
pub fn probeHealth(allocator: std.mem.Allocator, service: []const u8, base_url: []const u8, extra_path: ?[]const u8) bool {
    const url = buildProbeUrl(allocator, base_url, extra_path) catch {
        log.logWarn("stack_probe", SOURCE, "service={s} base_url={s} error=build_url_failed", .{ service, base_url });
        return false;
    };
    defer allocator.free(url);

    const result = outbound.get(allocator, url, service) catch {
        return false;
    };
    defer allocator.free(result.body);

    const ok = result.status >= 200 and result.status < 300;
    if (ok) {
        log.logSucceeded("stack_probe", SOURCE, "service={s} target={s} status={d}", .{ service, url, result.status });
    } else {
        log.logWarn("stack_probe", SOURCE, "service={s} target={s} status={d}", .{ service, url, result.status });
    }
    return ok;
}

fn runProbe(ctx: *const ProbeCtx) void {
    req_ctx.setOrigin(ctx.origin_method, ctx.origin_path);
    req_ctx.setRequestId(ctx.origin_request_id);
    defer req_ctx.clearOrigin();
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

    const origin_method = if (snap.active) |ctx| ctx.method else null;
    const origin_path = if (snap.active) |ctx| ctx.path else null;
    const origin_request_id = blk: {
        if (snap.active) |ctx| {
            if (request_id_mod.isAcceptable(ctx.request_id)) {
                break :blk try allocator.dupe(u8, ctx.request_id);
            }
        }
        break :blk null;
    };
    defer if (origin_request_id) |id| allocator.free(id);

    for (specs, 0..) |spec, i| {
        ctxs[i] = .{
            .allocator = allocator,
            .name = spec[0],
            .base_url = spec[1],
            .extra_path = spec[2],
            .ok = &results[i],
            .origin_method = origin_method,
            .origin_path = origin_path,
            .origin_request_id = origin_request_id,
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
