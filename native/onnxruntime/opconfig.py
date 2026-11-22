"""Collects every operator the two Automix models can need at runtime.

For each model: its own graph, plus the graph ONNX Runtime 1.28.0 rewrites it
into at EXTENDED and at ALL optimisation (the level the app uses). Fusions add
contrib ops (FusedConv, MatMulIntegerToFloat, ...) that never appear in the
.onnx file; a reduced build missing one fails at session creation. EXTENDED is
captured as well as ALL because on x86 the NCHWc layout pass at ALL swallows
convolutions that on ARM go through the ordinary Conv fusions instead.
"""
import collections
import os
import sys

import onnx
import onnxruntime as ort

models = sys.argv[1:-1]
out_dir = sys.argv[-1]
os.makedirs(out_dir, exist_ok=True)

ops = collections.defaultdict(set)  # (domain, opset) -> ops


def collect(path, label):
    m = onnx.load(path, load_external_data=False)
    opsets = {(i.domain or "ai.onnx"): i.version for i in m.opset_import}
    seen = collections.Counter()

    def walk(graph):
        for n in graph.node:
            d = n.domain or "ai.onnx"
            ops[(d, opsets.get(d, 1))].add(n.op_type)
            seen[f"{d}:{n.op_type}"] += 1
            for a in n.attribute:
                if a.g is not None and a.g.node:
                    walk(a.g)
                for g in a.graphs:
                    walk(g)

    walk(m.graph)
    print(f"--- {label}: opsets {opsets}")
    print("   ", dict(sorted(seen.items())))


levels = {
    "basic": ort.GraphOptimizationLevel.ORT_ENABLE_BASIC,
    "extended": ort.GraphOptimizationLevel.ORT_ENABLE_EXTENDED,
    "all": ort.GraphOptimizationLevel.ORT_ENABLE_ALL,
}
for path in models:
    name = os.path.splitext(os.path.basename(path))[0]
    collect(path, f"{name} (file)")
    for lvl, value in levels.items():
        so = ort.SessionOptions()
        so.graph_optimization_level = value
        out = os.path.join(out_dir, f"{name}.{lvl}.onnx")
        so.optimized_model_filepath = out
        ort.InferenceSession(path, so, providers=["CPUExecutionProvider"])
        collect(out, f"{name} ({lvl})")

with open(os.path.join(out_dir, "required_operators.config"), "w", newline="\n") as f:
    f.write("# Operators the Automix models need: see native/onnxruntime/README.md\n")
    for (domain, opset), names in sorted(ops.items()):
        f.write(f"{domain};{opset};{','.join(sorted(names))}\n")
print(open(os.path.join(out_dir, "required_operators.config")).read())
