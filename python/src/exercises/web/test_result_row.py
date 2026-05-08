from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class Status(Enum):
    PASSED = "PASSED"
    FAILED = "FAILED"
    SKIPPED = "SKIPPED"


@dataclass(frozen=True)
class TestResultRow:
    method_name: str
    class_name: str
    status: Status
    seconds: float
    detail: str
    node_id: str

    def duration_label(self) -> str:
        return f"{self.seconds:.3f} s"

    def case_id(self) -> str:
        return f"{self.class_name}#{self.method_name}"

    def python_package(self) -> str:
        dot = self.class_name.rfind(".")
        return "(no package)" if dot < 0 else self.class_name[:dot]

    def simple_class_name(self) -> str:
        dot = self.class_name.rfind(".")
        return self.class_name if dot < 0 else self.class_name[dot + 1 :]
