#!/bin/bash

echo "🚀 AI Load Balancer - Complete Build & Deploy Script"
echo "===================================================="

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Parse command line arguments
SKIP_BUILD=false
SKIP_DEPLOY=false
CLEAN_DEPLOY=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --skip-deploy)
            SKIP_DEPLOY=true
            shift
            ;;
        --clean)
            CLEAN_DEPLOY=true
            shift
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --skip-build    Skip the build phase"
            echo "  --skip-deploy   Skip the deployment phase"
            echo "  --clean         Clean shutdown before deployment"
            echo "  --help          Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0                    # Full build and deploy"
            echo "  $0 --clean           # Clean shutdown, build, and deploy"
            echo "  $0 --skip-build      # Deploy with existing images"
            echo "  $0 --skip-deploy     # Build only, no deployment"
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Step 1: Clean shutdown if requested
if [ "$CLEAN_DEPLOY" = true ]; then
    echo ""
    print_status "🛑 Performing clean shutdown..."
    ./shutdown.sh
    if [ $? -ne 0 ]; then
        print_warning "Shutdown script failed or cluster was not running"
    fi
    echo ""
    print_status "⏳ Waiting 5 seconds for cleanup to complete..."
    sleep 5
fi

# Step 2: Build phase
if [ "$SKIP_BUILD" = false ]; then
    echo ""
    print_status "🏗️  Starting build phase..."
    ./build.sh
    if [ $? -ne 0 ]; then
        print_error "Build failed. Aborting deployment."
        exit 1
    fi
    print_success "Build phase completed successfully"
else
    print_warning "⏭️  Skipping build phase as requested"
fi

# Step 3: Deployment phase
if [ "$SKIP_DEPLOY" = false ]; then
    echo ""
    print_status "🚀 Starting deployment phase..."
    ./startup.sh
    if [ $? -ne 0 ]; then
        print_error "Deployment failed."
        exit 1
    fi
    print_success "Deployment phase completed successfully"
else
    print_warning "⏭️  Skipping deployment phase as requested"
fi

# Step 4: Post-deployment verification
if [ "$SKIP_DEPLOY" = false ]; then
    echo ""
    print_status "🔍 Performing post-deployment verification..."
    
    # Wait a bit for pods to start
    sleep 10
    
    # Check pod status
    print_status "Checking pod status..."
    kubectl get pods -n ai-loadbalancer --no-headers 2>/dev/null | while read line; do
        pod_name=$(echo $line | awk '{print $1}')
        pod_status=$(echo $line | awk '{print $3}')
        
        if [[ "$pod_status" == "Running" ]]; then
            print_success "✅ $pod_name - $pod_status"
        elif [[ "$pod_status" == "Pending" || "$pod_status" == "ContainerCreating" ]]; then
            print_warning "⏳ $pod_name - $pod_status"
        else
            print_error "❌ $pod_name - $pod_status"
        fi
    done
    
    echo ""
    print_status "📊 Deployment Summary:"
    kubectl get pods -n ai-loadbalancer 2>/dev/null | head -1
    kubectl get pods -n ai-loadbalancer --no-headers 2>/dev/null | awk '{print $3}' | sort | uniq -c | while read count status; do
        if [[ "$status" == "Running" ]]; then
            print_success "  ✅ $count pods $status"
        elif [[ "$status" == "Pending" || "$status" == "ContainerCreating" ]]; then
            print_warning "  ⏳ $count pods $status"
        else
            print_error "  ❌ $count pods $status"
        fi
    done
fi

# Final instructions
echo ""
print_success "🎉 Deploy script completed!"

if [ "$SKIP_DEPLOY" = false ]; then
    echo ""
    print_status "🌐 Access your services:"
    echo "  AI Load Balancer: kubectl port-forward -n ai-loadbalancer service/ai-loadbalancer-service 8080:8080"
    echo "  Prometheus:       kubectl port-forward -n ai-loadbalancer service/prometheus-service 9090:9090"
    echo "  Grafana:          kubectl port-forward -n ai-loadbalancer service/grafana-service 3000:3000"
    echo "  MailHog:          kubectl port-forward -n ai-loadbalancer service/mailhog 8025:8025"
    echo ""
    print_status "🔍 Monitor your deployment:"
    echo "  kubectl get pods -n ai-loadbalancer"
    echo "  kubectl logs -n ai-loadbalancer deployment/ai-loadbalancer"
    echo ""
    print_status "🛑 To shutdown:"
    echo "  ./shutdown.sh"
fi
