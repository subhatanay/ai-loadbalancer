#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
K8S_DIR="$ROOT_DIR/kubernetes-stack/logging"

echo "🗑️  Deleting Fluent Bit DaemonSet..."
kubectl delete -f "$K8S_DIR/fluent-bit-daemonset.yaml" --ignore-not-found

echo "🗑️  Deleting ISM policy job and config..."
kubectl delete -f "$K8S_DIR/opensearch-ism-policy.yaml" --ignore-not-found

echo "🗑️  Deleting OpenSearch Dashboards..."
kubectl delete -f "$K8S_DIR/opensearch-dashboards.yaml" --ignore-not-found

echo "🗑️  Deleting OpenSearch..."
kubectl delete -f "$K8S_DIR/opensearch.yaml" --ignore-not-found

echo "🗑️  Deleting Fluent Bit config and RBAC..."
kubectl delete -f "$K8S_DIR/fluent-bit-config.yaml" --ignore-not-found
kubectl delete -f "$K8S_DIR/fluent-bit-rbac.yaml" --ignore-not-found

# Note: Keep namespace by default to avoid accidental deletion of other logging resources
# To delete the namespace as well, uncomment the following line:
# kubectl delete -f "$K8S_DIR/namespace.yaml" --ignore-not-found

echo "✅ Logging stack resources deleted (namespace preserved)."
