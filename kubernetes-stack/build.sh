#!/bin/bash

echo "🏗️  AI Load Balancer - Complete Build Script"
echo "=============================================="

# Set build variables
PROJECT_ROOT="/Users/subhajgh/Documents/bits/final-project/ai-loadbalancer"
BUILD_START_TIME=$(date +%s)

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

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Check prerequisites
echo ""
print_status "Checking prerequisites..."

if ! command_exists mvn; then
    print_error "Maven is not installed. Please install Maven first."
    exit 1
fi

if ! command_exists docker; then
    print_error "Docker is not installed. Please install Docker first."
    exit 1
fi

if ! command_exists kind; then
    print_error "Kind is not installed. Please install Kind first."
    exit 1
fi

print_success "All prerequisites are available"

# Navigate to project root
cd "$PROJECT_ROOT" || {
    print_error "Failed to navigate to project root: $PROJECT_ROOT"
    exit 1
}

print_status "Working directory: $(pwd)"

# Clean previous builds
echo ""
print_status "Cleaning previous builds..."
mvn clean -q
if [ $? -eq 0 ]; then
    print_success "Maven clean completed"
else
    print_error "Maven clean failed"
    exit 1
fi

# Build all Maven projects
echo ""
print_status "Building all Maven projects..."
mvn package -DskipTests -q
if [ $? -eq 0 ]; then
    print_success "Maven build completed successfully"
else
    print_error "Maven build failed"
    exit 1
fi

# Define services and their Docker build contexts
SERVICES=("user-service" "cart-service" "order-service" "inventory-service" "payment-service" "notification-service" "load-balancer")

# Build Docker images for Spring Boot services
echo ""
print_status "Building Docker images for microservices..."

for service in "${SERVICES[@]}"; do
    context="./$service"
    
    print_status "Building Docker image for $service..."
    
    # Check if Dockerfile exists
    if [ ! -f "$context/Dockerfile" ]; then
        print_warning "Dockerfile not found for $service at $context/Dockerfile"
        continue
    fi
    
    # Build Docker image with correct context
    docker build -t "$service:latest" -f "$context/Dockerfile" "$context" 
    
    if [ $? -eq 0 ]; then
        print_success "✅ $service:latest built successfully"
    else
        print_error "❌ Failed to build $service:latest"
        exit 1
    fi
done

# Build RL Agent (Python service)
echo ""
print_status "Building RL Agent Docker image..."

if [ -f "./rl_agent/Dockerfile" ]; then
    docker build -t "rl-agent:latest" -f "./rl_agent/Dockerfile" "./rl_agent" --quiet
    
    if [ $? -eq 0 ]; then
        print_success "✅ rl-agent:latest built successfully"
    else
        print_error "❌ Failed to build rl-agent:latest"
        exit 1
    fi
else
    print_warning "RL Agent Dockerfile not found at ./rl_agent/Dockerfile"
fi

# Build RL Experience Collector (Python FastAPI service)
echo ""
print_status "Building RL Experience Collector Docker image..."

RL_COLLECTOR_PATH="$PROJECT_ROOT/training/rl_experience_collector"
if [ -f "$RL_COLLECTOR_PATH/Dockerfile" ]; then
    docker build -t "rl-experience-collector:latest" -f "$RL_COLLECTOR_PATH/Dockerfile" "$RL_COLLECTOR_PATH" --quiet
    
    if [ $? -eq 0 ]; then
        print_success "✅ rl-experience-collector:latest built successfully"
    else
        print_error "❌ Failed to build rl-experience-collector:latest"
        exit 1
    fi
else
    print_warning "RL Experience Collector Dockerfile not found at $RL_COLLECTOR_PATH/Dockerfile"
fi

# Verify all images are built
echo ""
print_status "Verifying built Docker images..."

EXPECTED_IMAGES=(
    "user-service:latest"
    "cart-service:latest"
    "order-service:latest"
    "inventory-service:latest"
    "payment-service:latest"
    "notification-service:latest"
    "load-balancer:latest"
    "rl-agent:latest"
    "rl-experience-collector:latest"
)

MISSING_IMAGES=()

for image in "${EXPECTED_IMAGES[@]}"; do
    if docker images --format "table {{.Repository}}:{{.Tag}}" | grep -q "^$image$"; then
        print_success "✅ $image - Available"
    else
        print_warning "❌ $image - Missing"
        MISSING_IMAGES+=("$image")
    fi
done

# Display build summary
echo ""
echo "🎯 BUILD SUMMARY"
echo "================"

BUILD_END_TIME=$(date +%s)
BUILD_DURATION=$((BUILD_END_TIME - BUILD_START_TIME))

print_status "Build completed in ${BUILD_DURATION} seconds"

if [ ${#MISSING_IMAGES[@]} -eq 0 ]; then
    print_success "🎉 All Docker images built successfully!"
    echo ""
    print_status "Built images:"
    for image in "${EXPECTED_IMAGES[@]}"; do
        echo "  ✅ $image"
    done
    
    echo ""
    print_status "Next steps:"
    echo "  1. Run './startup.sh' to deploy to Kubernetes"
    echo "  2. Or run 'docker-compose up -d' for Docker Compose deployment"
    
else
    print_warning "⚠️  Some images failed to build:"
    for image in "${MISSING_IMAGES[@]}"; do
        echo "  ❌ $image"
    done
    echo ""
    print_error "Please check the build logs and fix any issues before deploying."
    exit 1
fi

# Optional: Display image sizes
echo ""
print_status "Docker image sizes:"
for image in "${EXPECTED_IMAGES[@]}"; do
    if docker images --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}" | grep -q "^$image"; then
        size=$(docker images --format "{{.Size}}" "$image")
        echo "  📦 $image - $size"
    fi
done

echo ""
print_success "🚀 Build script completed successfully!"
print_status "All services are ready for deployment to Kubernetes or Docker Compose."
