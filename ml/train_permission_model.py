#!/usr/bin/env python3
"""Train a permission-only logistic model from a labeled CSV.

The CSV must have one row per app. Permission columns should be binary values
(0/1, true/false, yes/no). Alternatively, pass --permissions-column for a
comma/semicolon-separated permission list. The label column must identify
benign/malicious examples; use --positive-label to choose the malicious value.
"""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path
from typing import Iterable

from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split


TRUE_VALUES = {"1", "true", "yes", "y", "granted", "present"}
FALSE_VALUES = {"0", "false", "no", "n", "benign", "absent", ""}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dataset", type=Path, help="Labeled CSV downloaded from a public Android-permission dataset")
    parser.add_argument("--label-column", default="label", help="Column containing benign/malware labels")
    parser.add_argument("--positive-label", default="malware", help="Value treated as malicious (case-insensitive)")
    parser.add_argument("--permissions-column", help="Optional column containing a delimited permission list per row")
    parser.add_argument("--output", type=Path, default=Path("app/src/main/assets/ml_model.json"))
    parser.add_argument("--seed", type=int, default=17)
    return parser.parse_args()


def parse_presence(value: str) -> int:
    normalized = value.strip().lower()
    if normalized in TRUE_VALUES:
        return 1
    if normalized in FALSE_VALUES:
        return 0
    try:
        return int(float(normalized) != 0)
    except ValueError as exc:
        raise ValueError(f"Expected a binary feature value, received {value!r}") from exc


def read_rows(path: Path) -> tuple[list[dict[str, str]], list[str]]:
    with path.open(newline="", encoding="utf-8-sig") as source:
        reader = csv.DictReader(source)
        if not reader.fieldnames:
            raise ValueError("The dataset has no header row.")
        return list(reader), list(reader.fieldnames)


def permission_sets(
    rows: Iterable[dict[str, str]],
    feature_names: list[str],
    permissions_column: str | None,
) -> list[dict[str, int]]:
    result = []
    for row in rows:
        if permissions_column:
            raw = row.get(permissions_column, "")
            present = {item.strip() for item in raw.replace(";", ",").split(",") if item.strip()}
            result.append({feature: int(feature in present) for feature in feature_names})
        else:
            result.append({feature: parse_presence(row.get(feature, "")) for feature in feature_names})
    return result


def main() -> None:
    args = parse_args()
    rows, headers = read_rows(args.dataset)
    if args.label_column not in headers:
        raise ValueError(f"Label column {args.label_column!r} is not present.")
    if args.permissions_column and args.permissions_column not in headers:
        raise ValueError(f"Permissions column {args.permissions_column!r} is not present.")

    excluded = {args.label_column}
    if args.permissions_column:
        excluded.add(args.permissions_column)
        feature_names = sorted(
            {
                permission.strip()
                for row in rows
                for permission in row.get(args.permissions_column, "").replace(";", ",").split(",")
                if permission.strip()
            }
        )
    else:
        feature_names = [header for header in headers if header not in excluded]
    if not feature_names:
        raise ValueError("No permission features were found.")

    x_rows = permission_sets(rows, feature_names, args.permissions_column)
    positive = args.positive_label.strip().lower()
    labels = [int(row[args.label_column].strip().lower() == positive) for row in rows]
    if len(set(labels)) != 2:
        raise ValueError("The selected dataset must include both benign and malicious examples.")

    x_train, _, y_train, _ = train_test_split(
        x_rows,
        labels,
        test_size=0.2,
        random_state=args.seed,
        stratify=labels,
    )
    model = LogisticRegression(max_iter=2000, class_weight="balanced", random_state=args.seed)
    model.fit(x_train, y_train)

    output = {
        "model": "scikit-learn LogisticRegression",
        "trained": True,
        "dataset": args.dataset.name,
        "labelColumn": args.label_column,
        "positiveLabel": args.positive_label,
        "featureOrder": feature_names,
        "intercept": float(model.intercept_[0]),
        "weights": {name: float(weight) for name, weight in zip(feature_names, model.coef_[0])},
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, indent=2) + "\n", encoding="utf-8")
    print(f"Exported {len(feature_names)} permission coefficients to {args.output}")


if __name__ == "__main__":
    main()
