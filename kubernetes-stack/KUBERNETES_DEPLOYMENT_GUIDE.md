# 🚀 Deploying the AI Load Balancer on Kubernetes

This guide provides easy-to-follow steps to deploy the entire AI Load Balancer microservices ecosystem on a local Kubernetes cluster using `kind` (Kubernetes in Docker).

## ✅ Prerequisites

Before you begin, make sure you have the following tools installed on your system:

*   **Docker**: To build and run the containerized services. [Install Docker](https://docs.docker.com/get-docker/)
*   **kubectl**: The command-line tool for interacting with a Kubernetes cluster. [Install kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/)
*   **Kind**: To run a local Kubernetes cluster inside a Docker container. [Install Kind](https://kind.sigs.k8s.io/docs/user/quick-start/#installation)

## 🛠️ Step 1: Build Your Docker Images

First, you need to build all the project's Docker images. A convenient script is provided to automate this entire process.

From the `kubernetes-stack/` directory, run:

```bash
./build.sh
```

This script will build all the Java microservices and create a Docker image for each one, tagging them as `:latest`.

## 🚀 Step 2: Launch the Kubernetes Stack

With your images ready, you can now launch the entire stack with a single script. This script automates the process of setting up the cluster, loading your images, and deploying all the services.

From the `kubernetes-stack/` directory, run:

```bash
./startup.sh
```

**What does this script do?**

1.  **Creates a `kind` Cluster**: It spins up a local Kubernetes cluster named `ai-loadbalancer-cluster`.
2.  **Loads Docker Images**: It loads the Docker images you just built into the `kind` cluster so Kubernetes can access them.
3.  **Deploys All Components**: It applies all the necessary Kubernetes configuration files (`.yaml`) to deploy:
    *   **Infrastructure**: PostgreSQL, Redis, Kafka, and Zookeeper.
    *   **Monitoring**: Prometheus and Grafana.
    *   **Logging**: OpenSearch and Fluent Bit for centralized logging.
    *   **Application**: All the e-commerce microservices and the AI Load Balancer.
4.  **Initializes the Database**: It runs a script to ensure all required databases and tables are created in PostgreSQL.

## 🖥️ Step 3: Access Your Services

Once the `startup.sh` script is finished, your services will be running inside the Kubernetes cluster. To access them from your local machine, you need to forward their ports. The script provides the necessary commands at the end of its execution.

Open new terminal tabs and run the following commands:

*   **AI Load Balancer (The main entry point for the app)**:
    ```bash
    kubectl port-forward -n ai-loadbalancer service/ai-loadbalancer-service 8080:8080
    ```
    *Access at: `http://localhost:8080`*

*   **Grafana (For viewing dashboards)**:
    ```bash
    kubectl port-forward -n ai-loadbalancer service/grafana-service 3000:3000
    ```
    *Access at: `http://localhost:3000` (login with `admin`/`admin`)*

*   **Prometheus (For exploring metrics)**:
    ```bash
    kubectl port-forward -n ai-loadbalancer service/prometheus-service 9090:9090
    ```
    *Access at: `http://localhost:9090`*

*   **OpenSearch Dashboards (For viewing logs)**:
    ```bash
    kubectl port-forward -n logging service/opensearch-dashboards-svc 5601:5601
    ```
    *Access at: `http://localhost:5601`*

## 🔍 How to Check the Deployment

If you want to see what's happening inside your cluster, here are a few useful commands:

*   **See all running pods (the containers for your services)**:
    ```bash
    kubectl get pods -n ai-loadbalancer
    ```

*   **Check the logs of a specific service (e.g., the User Service)**:
    ```bash
    kubectl logs -n ai-loadbalancer deployment/user-service
    ```

*   **Check the pods in the logging namespace**:
    ```bash
    kubectl get pods -n logging
    ```

## ❌ How to Shut Down the Environment

When you're done, you can tear down the entire stack and delete the local cluster by running the `shutdown.sh` script from the `kubernetes-stack/` directory:

```bash
./shutdown.sh
```

This will delete the `kind` cluster and all the resources you created, cleaning up your local environment.
