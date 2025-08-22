#!/bin/bash

# RL Training Pipeline Orchestration Script
# Coordinates load testing, metrics collection, and offline training

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
LOAD_TEST_DIR="$PROJECT_ROOT/load-testing"
DASHBOARD_DIR="$PROJECT_ROOT/training/rl_experience_collector/dashboard"
OFFLINE_TRAINING_DIR="$PROJECT_ROOT/rl_agent/offline-training"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging function
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    log "Checking prerequisites..."
    
    # Check if Kubernetes cluster is running
    if ! kubectl cluster-info &> /dev/null; then
        error "Kubernetes cluster is not accessible"
        exit 1
    fi
    
    # Check if services are deployed
    if ! kubectl get pods -n ai-loadbalancer | grep -q "Running"; then
        error "AI Load Balancer services are not running"
        exit 1
    fi
    
    # Check if RL Experience Collector is accessible
    if ! curl -s http://localhost:8081/health &> /dev/null; then
        warning "RL Experience Collector not accessible at localhost:8081"
        warning "Make sure to port-forward: kubectl port-forward -n ai-loadbalancer svc/rl-experience-collector 8081:8081"
    fi
    
    # Check Python dependencies
    if ! python3 -c "import streamlit, plotly, pandas" &> /dev/null; then
        warning "Dashboard dependencies not installed. Installing..."
        pip3 install -r "$DASHBOARD_DIR/requirements.txt"
    fi
    
    success "Prerequisites check completed"
}

# Phase 1: Start Enhanced Load Testing
start_load_testing() {
    log "Phase 1: Starting Enhanced Load Testing for RL Training Data"
    
    cd "$LOAD_TEST_DIR"
    
    # Make scripts executable
    chmod +x rl_training_load_test.py
    
    # Start load testing in background
    log "Starting diverse load patterns scenario..."
    python3 rl_training_load_test.py \
        --config rl_training_config.json \
        --scenario diverse_load_patterns \
        --output rl_training_results_$(date +%Y%m%d_%H%M%S).json &
    
    LOAD_TEST_PID=$!
    echo $LOAD_TEST_PID > /tmp/rl_load_test.pid
    
    success "Load testing started (PID: $LOAD_TEST_PID)"
    log "Load test will run for 120 minutes with diverse traffic patterns"
    
    cd "$PROJECT_ROOT"
}

# Phase 2: Start Metrics Dashboard
start_dashboard() {
    log "Phase 2: Starting RL Metrics Visualization Dashboard"
    
    cd "$DASHBOARD_DIR"
    
    # Start Streamlit dashboard in background
    log "Starting Streamlit dashboard on port 8501..."
    streamlit run rl_metrics_dashboard.py --server.port 8501 --server.headless true &
    
    DASHBOARD_PID=$!
    echo $DASHBOARD_PID > /tmp/rl_dashboard.pid
    
    success "Dashboard started (PID: $DASHBOARD_PID)"
    log "Dashboard accessible at: http://localhost:8501"
    
    cd "$PROJECT_ROOT"
}

# Phase 3: Monitor and Trigger Offline Training
monitor_and_train() {
    log "Phase 3: Monitoring Experience Collection and Offline Training"
    
    # Wait for some experiences to be collected
    log "Waiting for experience data collection (5 minutes)..."
    sleep 300
    
    # Check if we have enough experiences
    EXPERIENCE_COUNT=$(curl -s http://localhost:8081/experiences | jq '.experiences | length' 2>/dev/null || echo "0")
    
    if [ "$EXPERIENCE_COUNT" -gt 50 ]; then
        success "Found $EXPERIENCE_COUNT experiences. Starting offline training..."
        
        cd "$OFFLINE_TRAINING_DIR"
        
        # Run offline training
        python3 experience_consumer.py \
            --collector-url http://localhost:8081 \
            --output-dir "$PROJECT_ROOT/rl_agent/models/offline_trained_$(date +%Y%m%d_%H%M%S)"
        
        success "Offline training completed"
    else
        warning "Only $EXPERIENCE_COUNT experiences found. Need at least 50 for training."
        log "Continuing to monitor..."
    fi
    
    cd "$PROJECT_ROOT"
}

# Cleanup function
cleanup() {
    log "Cleaning up processes..."
    
    # Stop load testing
    if [ -f /tmp/rl_load_test.pid ]; then
        LOAD_TEST_PID=$(cat /tmp/rl_load_test.pid)
        if kill -0 $LOAD_TEST_PID 2>/dev/null; then
            log "Stopping load test (PID: $LOAD_TEST_PID)"
            kill $LOAD_TEST_PID
        fi
        rm -f /tmp/rl_load_test.pid
    fi
    
    # Stop dashboard
    if [ -f /tmp/rl_dashboard.pid ]; then
        DASHBOARD_PID=$(cat /tmp/rl_dashboard.pid)
        if kill -0 $DASHBOARD_PID 2>/dev/null; then
            log "Stopping dashboard (PID: $DASHBOARD_PID)"
            kill $DASHBOARD_PID
        fi
        rm -f /tmp/rl_dashboard.pid
    fi
    
    success "Cleanup completed"
}

# Signal handlers
trap cleanup EXIT
trap 'error "Script interrupted"; exit 1' INT TERM

# Main execution
main() {
    log "Starting RL Training Pipeline Orchestration"
    log "=========================================="
    
    # Parse command line arguments
    SKIP_LOAD_TEST=false
    SKIP_DASHBOARD=false
    TRAINING_ONLY=false
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --skip-load-test)
                SKIP_LOAD_TEST=true
                shift
                ;;
            --skip-dashboard)
                SKIP_DASHBOARD=true
                shift
                ;;
            --training-only)
                TRAINING_ONLY=true
                shift
                ;;
            --help)
                echo "Usage: $0 [OPTIONS]"
                echo "Options:"
                echo "  --skip-load-test    Skip load testing phase"
                echo "  --skip-dashboard    Skip dashboard startup"
                echo "  --training-only     Only run offline training"
                echo "  --help              Show this help message"
                exit 0
                ;;
            *)
                error "Unknown option: $1"
                exit 1
                ;;
        esac
    done
    
    # Check prerequisites
    check_prerequisites
    
    if [ "$TRAINING_ONLY" = true ]; then
        log "Running offline training only..."
        cd "$OFFLINE_TRAINING_DIR"
        python3 experience_consumer.py --collector-url http://localhost:8081
        exit 0
    fi
    
    # Phase 1: Load Testing
    if [ "$SKIP_LOAD_TEST" = false ]; then
        start_load_testing
        sleep 10  # Give load test time to start
    fi
    
    # Phase 2: Dashboard
    if [ "$SKIP_DASHBOARD" = false ]; then
        start_dashboard
        sleep 5   # Give dashboard time to start
    fi
    
    # Phase 3: Monitor and Train
    monitor_and_train
    
    # Keep script running for monitoring
    log "Pipeline is running. Press Ctrl+C to stop."
    log "Monitor progress at:"
    log "  - Dashboard: http://localhost:8501"
    log "  - Load Test Logs: $LOAD_TEST_DIR/rl_training_load_test.log"
    log "  - RL Collector Logs: Use ./kubernetes-stack/access_rl_logs.sh"
    
    # Wait for load test to complete or user interrupt
    if [ -f /tmp/rl_load_test.pid ]; then
        LOAD_TEST_PID=$(cat /tmp/rl_load_test.pid)
        wait $LOAD_TEST_PID 2>/dev/null || true
    else
        # If no load test, just wait for interrupt
        while true; do
            sleep 60
            # Periodically check for new experiences and trigger training
            EXPERIENCE_COUNT=$(curl -s http://localhost:8081/experiences | jq '.experiences | length' 2>/dev/null || echo "0")
            if [ "$EXPERIENCE_COUNT" -gt 100 ]; then
                log "Found $EXPERIENCE_COUNT experiences. Triggering offline training..."
                monitor_and_train
            fi
        done
    fi
    
    success "RL Training Pipeline completed successfully"
}

# Run main function
main "$@"
