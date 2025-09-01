#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
K8S_DIR="$ROOT_DIR/kubernetes-stack/logging"

echo "📦 Applying logging namespace..."
kubectl apply -f "$K8S_DIR/namespace.yaml"

echo "🔐 Applying Fluent Bit RBAC..."
kubectl apply -f "$K8S_DIR/fluent-bit-rbac.yaml"

echo "⚙️  Applying Fluent Bit config..."
kubectl apply -f "$K8S_DIR/fluent-bit-config.yaml"

echo "🧠 Deploying OpenSearch..."
kubectl apply -f "$K8S_DIR/opensearch.yaml"

echo "🖥️  Deploying OpenSearch Dashboards (NodePort)..."
kubectl apply -f "$K8S_DIR/opensearch-dashboards.yaml"

echo "🗂️  Applying ISM policy job (7-day retention)..."
kubectl apply -f "$K8S_DIR/opensearch-ism-policy.yaml"

echo "🪵 Deploying Fluent Bit DaemonSet..."
kubectl apply -f "$K8S_DIR/fluent-bit-daemonset.yaml"

echo "⏳ Waiting for OpenSearch to be ready..."
kubectl -n logging rollout status statefulset/opensearch --timeout=180s

echo "⏳ Waiting for OpenSearch Dashboards to be ready..."
kubectl -n logging rollout status deployment/opensearch-dashboards --timeout=180s

echo "⏳ Waiting for Fluent Bit to be ready..."
kubectl -n logging rollout status daemonset/fluent-bit --timeout=180s

NODE_PORT=$(kubectl -n logging get svc opensearch-dashboards -o jsonpath='{.spec.ports[0].nodePort}')
echo "✅ Logging stack is up. OpenSearch Dashboards: http://localhost:${NODE_PORT}"
echo "➡️  In Dashboards, create an Index Pattern: logs-ai-loadbalancer-*"
