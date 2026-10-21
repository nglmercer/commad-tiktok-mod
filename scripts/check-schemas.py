#!/usr/bin/env python3
"""Check the shipped examples against the JSON schemas.

The plugin validates its own configuration when it loads it, so the examples
are also checked structurally here. This catches a schema that no longer
matches the format the plugin accepts.

Usage: check-schemas.py
"""

import json
import pathlib
import sys

try:
    import yaml
except ImportError:
    yaml = None

try:
    import jsonschema
except ImportError:
    jsonschema = None

ROOT = pathlib.Path(__file__).resolve().parent.parent


def load_json(path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def main():
    problems = []

    function_schema = load_json(ROOT / "schemas" / "function.schema.json")
    rule_schema = load_json(ROOT / "schemas" / "event-rule.schema.json")

    for path in sorted((ROOT / "examples" / "functions").glob("*.json")):
        document = load_json(path)
        if jsonschema is not None:
            try:
                jsonschema.validate(document, function_schema)
            except jsonschema.ValidationError as failure:
                problems.append("%s: %s" % (path.name, failure.message))
        if document.get("schemaVersion") != 1:
            problems.append("%s: schemaVersion must be 1" % path.name)
        if "steps" not in document or not document["steps"]:
            problems.append("%s: the function has no steps" % path.name)

    for path in sorted((ROOT / "examples").glob("*.yml")):
        if yaml is None:
            print("PyYAML is not installed; the YAML examples were not checked")
            break
        with path.open(encoding="utf-8") as handle:
            document = yaml.safe_load(handle)
        if not isinstance(document, dict) or "rules" not in document:
            problems.append("%s: the file has no 'rules' list" % path.name)
            continue
        for index, rule in enumerate(document["rules"]):
            where = "%s rules[%d]" % (path.name, index)
            for field in ("id", "trigger", "actions"):
                if field not in rule:
                    problems.append("%s: '%s' is missing" % (where, field))
            if jsonschema is not None:
                try:
                    jsonschema.validate(document, rule_schema)
                except jsonschema.ValidationError as failure:
                    problems.append("%s: %s" % (where, failure.message))

    if problems:
        print("The examples do not match the schemas:")
        for problem in problems:
            print("  - " + problem)
        return 1
    print("The examples match the schemas.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
