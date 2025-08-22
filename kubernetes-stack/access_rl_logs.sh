#!/bin/bash

# RL Experience Collector Log Access Script
# This script helps you access logs from the volume-mounted directories

echo "🔍 RL Experience Collector Log Access"
echo "======================================"

# Check if Kind cluster is running
if ! docker ps | grep -q "ai-loadbalancer-cluster-control-plane"; then
    echo "❌ Kind cluster 'ai-loadbalancer-cluster' is not running"
    echo "   Please start the cluster first with: kind create cluster --name ai-loadbalancer-cluster"
    exit 1
fi

# Since Kind runs in Docker, we need to access logs from the Kind node
KIND_NODE="ai-loadbalancer-cluster-control-plane"

echo "📋 Available log access methods:"
echo ""

echo "1. 📁 View current collector.log:"
echo "   docker exec $KIND_NODE cat /tmp/rl-collector-logs/collector.log"
echo ""

echo "2. 📊 Tail logs in real-time:"
echo "   docker exec $KIND_NODE tail -f /tmp/rl-collector-logs/collector.log"
echo ""

echo "3. 📂 List all log files:"
echo "   docker exec $KIND_NODE ls -la /tmp/rl-collector-logs/"
echo ""

echo "4. 💾 Copy logs to local machine:"
echo "   docker cp $KIND_NODE:/tmp/rl-collector-logs/ ./rl-collector-logs/"
echo ""

echo "5. 🔍 Search logs for specific patterns:"
echo "   docker exec $KIND_NODE grep -i 'experience recorded' /tmp/rl-collector-logs/collector.log"
echo ""

echo "6. 📈 View RL experience data:"
echo "   docker exec $KIND_NODE cat /tmp/rl-collector-data/rl_experiences.jsonl"
echo ""

# Interactive menu
echo "Choose an option (1-6) or press Enter to exit:"
read -r choice

case $choice in
    1)
        echo "📁 Viewing current collector.log:"
        docker exec $KIND_NODE cat /tmp/rl-collector-logs/collector.log 2>/dev/null || echo "❌ Log file not found or collector not running"
        ;;
    2)
        echo "📊 Tailing logs (Press Ctrl+C to exit):"
        docker exec $KIND_NODE tail -f /tmp/rl-collector-logs/collector.log 2>/dev/null || echo "❌ Log file not found or collector not running"
        ;;
    3)
        echo "📂 Listing all log files:"
        docker exec $KIND_NODE ls -la /tmp/rl-collector-logs/ 2>/dev/null || echo "❌ Log directory not found"
        ;;
    4)
        echo "💾 Copying logs to ./rl-collector-logs/:"
        docker cp $KIND_NODE:/tmp/rl-collector-logs/ ./rl-collector-logs/ 2>/dev/null && echo "✅ Logs copied successfully" || echo "❌ Failed to copy logs"
        ;;
    5)
        echo "🔍 Searching for 'Experience recorded' entries:"
        docker exec $KIND_NODE grep -i 'experience recorded' /tmp/rl-collector-logs/collector.log 2>/dev/null || echo "❌ No matching entries found"
        ;;
    6)
        echo "📈 Viewing RL experience data:"
        docker exec $KIND_NODE cat /tmp/rl-collector-data/rl_experiences.jsonl 2>/dev/null || echo "❌ Experience data file not found"
        ;;
    "")
        echo "👋 Exiting..."
        ;;
    *)
        echo "❌ Invalid option"
        ;;
esac
