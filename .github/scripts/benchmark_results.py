"""
Reads Macrobenchmark's *benchmarkData.json files: shared by
benchmark-summary.py (the run's table) and benchmark-gate.py (the check
against the saved baseline).
"""
import json
import pathlib

METRICS = ("frameDurationCpuMs", "frameOverrunMs", "timeToInitialDisplayMs")
PERCENTILES = ("P50", "P90", "P99")


def rows(root: pathlib.Path):
    """(benchmark, metric, [P50, P90, P99]) for each metric found; a
    single-valued metric (startup) gives its median as P50."""
    for path in sorted(root.rglob("*benchmarkData.json")):
        for bench in json.loads(path.read_text()).get("benchmarks", []):
            name = f"{bench.get('className', '').rsplit('.', 1)[-1]}.{bench.get('name', '')}"
            sampled = bench.get("sampledMetrics", {})
            single = bench.get("metrics", {})
            for metric in METRICS:
                if metric in sampled:
                    values = sampled[metric]
                    yield name, metric, [values.get(p) for p in PERCENTILES]
                elif metric in single:
                    yield name, metric, [single[metric].get("median"), None, None]


def medians(root: pathlib.Path) -> dict:
    """{"benchmark|metric": P50}: the steadiest number each metric has."""
    return {f"{name}|{metric}": values[0] for name, metric, values in rows(root) if values[0] is not None}


def annotation(level: str, title: str, message: str) -> str:
    """A workflow-command annotation, escaped as GitHub requires: a title
    with a comma or colon in it (a benchmark's parameters) would otherwise
    be cut short there, and a newline would end the message."""

    def data(text: str) -> str:
        return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")

    def prop(text: str) -> str:
        return data(text).replace(":", "%3A").replace(",", "%2C")

    return f"::{level} title={prop(title)}::{data(message)}"
