#!/usr/bin/env python3
"""Compare two k6 JSON outputs and emit a markdown diff table.

Usage: python3 compare.py <baseline.json> <after.json> > report.md
"""
import json
import sys
from pathlib import Path


def load_metrics(path: Path) -> dict:
    """Read k6 summary JSON and extract p50/p95/p99 + rps + failure rate."""
    with path.open() as f:
        data = json.load(f)

    metrics = data.get('metrics', {})
    duration = metrics.get('http_req_duration', {}).get('values', {})
    failed = metrics.get('http_req_failed', {}).get('values', {})
    iterations = metrics.get('iterations', {}).get('values', {})
    count = duration.get('count', 0)
    period_s = 30  # matching hold stage in scripts

    return {
        'p50_ms': duration.get('p(50)', 0) * 1000,
        'p95_ms': duration.get('p(95)', 0) * 1000,
        'p99_ms': duration.get('p(99)', 0) * 1000,
        'rps': count / period_s if count else 0,
        'fail_rate': failed.get('rate', 0),
    }


def fmt_delta(before: float, after: float, unit: str = 'ms') -> str:
    if before == 0:
        return 'n/a'
    pct = (after - before) / before * 100
    sign = '+' if pct > 0 else ''
    return f"{sign}{pct:.1f}%"


def main() -> int:
    if len(sys.argv) != 3:
        print('Usage: compare.py <baseline.json> <after.json>', file=sys.stderr)
        return 2

    before = load_metrics(Path(sys.argv[1]))
    after = load_metrics(Path(sys.argv[2]))

    print(f"# k6 Comparison: {Path(sys.argv[1]).name} vs {Path(sys.argv[2]).name}\n")
    print("| Metric | Before | After | Delta |")
    print("|--------|--------|-------|-------|")
    print(f"| p50    | {before['p50_ms']:.1f}ms | {after['p50_ms']:.1f}ms | {fmt_delta(before['p50_ms'], after['p50_ms'])} |")
    print(f"| p95    | {before['p95_ms']:.1f}ms | {after['p95_ms']:.1f}ms | {fmt_delta(before['p95_ms'], after['p95_ms'])} |")
    print(f"| p99    | {before['p99_ms']:.1f}ms | {after['p99_ms']:.1f}ms | {fmt_delta(before['p99_ms'], after['p99_ms'])} |")
    print(f"| RPS    | {before['rps']:.0f}   | {after['rps']:.0f}   | {fmt_delta(before['rps'], after['rps'])} |")
    print(f"| Fail   | {before['fail_rate']*100:.2f}% | {after['fail_rate']*100:.2f}% | - |")
    return 0


if __name__ == '__main__':
    sys.exit(main())
