#!/bin/bash

# Toggle script for RL-collector deployment
# Usage: ./toggle-rl-collector.sh [enable|disable|status]

ACTION=${1:-status}

case $ACTION in
  "enable")
    echo "🔄 Enabling RL-collector..."
    kubectl apply -f config-yaml/rl-collector.yaml
    echo "✅ RL-collector deployment applied"
    echo "📊 Waiting for RL-collector to be ready..."
    kubectl wait --for=condition=available --timeout=120s deployment/rl-experience-collector -n ai-loadbalancer
    echo "🎉 RL-collector is now ENABLED and running"
    ;;
    
  "disable")
    echo "🔄 Disabling RL-collector..."
    kubectl delete -f config-yaml/rl-collector.yaml --ignore-not-found=true
    echo "✅ RL-collector deployment removed"
    echo "🎉 RL-collector is now DISABLED"
    ;;
    
  "status")
    echo "📊 RL-collector Status:"
    echo "======================"
    
    # Check if RL-collector deployment exists
    if kubectl get deployment rl-experience-collector -n ai-loadbalancer >/dev/null 2>&1; then
      echo "🟢 Status: ENABLED"
      kubectl get pods -n ai-loadbalancer -l app=rl-experience-collector
      echo ""
      echo "📈 Recent logs:"
      kubectl logs -n ai-loadbalancer -l app=rl-experience-collector --tail=5
    else
      echo "🔴 Status: DISABLED"
      echo "💡 To enable: ./toggle-rl-collector.sh enable"
    fi
    
    echo ""
    echo "🎯 Current Architecture:"
    if kubectl get deployment rl-experience-collector -n ai-loadbalancer >/dev/null 2>&1; then
      echo "   Microservices → Prometheus → RL-Collector → RL Agent → Load Balancer"
    else
      echo "   Microservices → Prometheus → RL Agent → Load Balancer (Direct)"
    fi
    ;;
    
  *)
    echo "Usage: $0 [enable|disable|status]"
    echo ""
    echo "Commands:"
    echo "  enable  - Deploy and start RL-collector"
    echo "  disable - Remove RL-collector deployment"
    echo "  status  - Show current RL-collector status"
    exit 1
    ;;
esac
