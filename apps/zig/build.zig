const std = @import("std");

pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{});
    const optimize = b.standardOptimizeOption(.{});

    const exe = b.addExecutable(.{
        .name = "exercises-zig",
        .root_source_file = .{ .cwd_relative = "src/main.zig" },
        .target = target,
        .optimize = optimize,
    });
    addPostgresDeps(exe);
    b.installArtifact(exe);

    const unit_tests = b.addTest(.{
        .root_source_file = .{ .cwd_relative = "src/main.zig" },
        .target = target,
        .optimize = optimize,
    });
    addPostgresDeps(unit_tests);
    const run_unit_tests = b.addRunArtifact(unit_tests);
    const test_step = b.step("test", "Run unit tests");
    test_step.dependOn(&run_unit_tests.step);
}

fn addPostgresDeps(step: *std.Build.Step.Compile) void {
    // Zig 0.13 LazyPath: use cwd_relative (works for absolute paths on Linux).
    step.addIncludePath(.{ .cwd_relative = "/usr/include/postgresql" });
    step.linkSystemLibrary("pq");
    step.linkLibC();
}
