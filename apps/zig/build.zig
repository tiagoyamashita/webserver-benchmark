const std = @import("std");

pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{});
    const optimize = b.standardOptimizeOption(.{});

    const exe = b.addExecutable(.{
        .name = "webserver-benchmark-zig",
        .root_source_file = .{ .cwd_relative = "src/main.zig" },
        .target = target,
        .optimize = optimize,
    });
    addPostgresDeps(exe);
    addBcryptDeps(exe);
    b.installArtifact(exe);

    const unit_tests = b.addTest(.{
        .root_source_file = .{ .cwd_relative = "src/main.zig" },
        .target = target,
        .optimize = optimize,
    });
    addPostgresDeps(unit_tests);
    addBcryptDeps(unit_tests);
    const run_unit_tests = b.addRunArtifact(unit_tests);
    const test_step = b.step("test", "Run unit tests");
    test_step.dependOn(&run_unit_tests.step);
}

fn addPostgresDeps(step: *std.Build.Step.Compile) void {
    step.addIncludePath(.{ .cwd_relative = "/usr/include/postgresql" });
    step.linkSystemLibrary("pq");
    step.linkLibC();
}

fn addBcryptDeps(step: *std.Build.Step.Compile) void {
    const c_flags = [_][]const u8{ "-std=c99", "-D_POSIX_C_SOURCE=200809L" };
    step.addIncludePath(.{ .cwd_relative = "vendor/bcrypt" });
    step.addCSourceFile(.{ .file = .{ .cwd_relative = "vendor/bcrypt/bcrypt.c" }, .flags = &c_flags });
    step.addCSourceFile(.{ .file = .{ .cwd_relative = "vendor/bcrypt/crypt_blowfish.c" }, .flags = &c_flags });
    step.addCSourceFile(.{ .file = .{ .cwd_relative = "vendor/bcrypt/crypt_gensalt.c" }, .flags = &c_flags });
    step.addCSourceFile(.{ .file = .{ .cwd_relative = "vendor/bcrypt/wrapper.c" }, .flags = &c_flags });
}
