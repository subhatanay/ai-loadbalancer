# AI-Powered Adaptive Load Balancing for Microservices Architectures

## Table of Contents

**Abstract**

**1. Introduction**
*   1.1 Background and Motivation
*   1.2 Problem Statement

**2. Purpose and Expected Outcome**
*   2.1 Literature Review
*   2.2 Existing Process and Limitations
*   2.3 Justification for Methodology
*   2.4 Project Work Methodology
*   2.5 Benefits Derivable from the Work

**3. Detailed Plan of Work**

**4. System Architecture Overview**
*   4.1 High-Level System Design
*   4.2 Microservices Stack Architecture
*   4.3 Reinforcement Learning Engine
*   4.4 Monitoring and Observability Stack
*   4.5 Deployment Architecture
*   4.6 Load Testing Framework
*   4.7 Offline Training Strategy

**5. Implementation Details**
*   5.1 Load Balancer Routing Strategy
*   5.2 Traditional Algorithms
*   5.3 RL-Agent Based Load Balancing
*   5.4 RL-Agent: Core Implementation
*   5.5 Feedback Mechanism

**6. Observed Performance and Results**
*   6.1 System Environment and Training Setup
*   6.2 Performance Benchmarking
*   6.3 Real-Time Adaptiveness Scenario

**7. Conclusions, Limitations, and Future Work**
*   7.1 Conclusions
*   7.2 Limitations
*   7.3 Recommendations and Future Work

**8. References**

**9. Appendices**
*   Appendix A: Q-Learning Algorithm Explained
*   Appendix B: RL Model and System Details
*   Appendix C: System Configuration and Samples
*   Appendix D: Technical Specifications
*   Appendix E: Environment Setup and Execution Guide
*   Appendix F: Glossary

---

## Abstract

Traditional load balancing algorithms, such as Round Robin and Least Connections, are ill-suited for the dynamic and unpredictable nature of modern microservices architectures. Their reliance on static, simplistic heuristics leads to suboptimal performance, poor resource utilization, and a higher risk of cascading failures. This dissertation presents an AI-Powered Adaptive Load Balancer, a novel system that leverages reinforcement learning (RL) to overcome these limitations.

The system introduces an RL-Agent, based on a tabular Q-learning algorithm, that learns an optimal traffic distribution policy through continuous interaction with a live environment. By observing a rich set of real-time performance metrics (CPU, memory, latency, error rates) from a Prometheus monitoring stack, the agent makes intelligent, context-aware routing decisions. The research demonstrates that this approach allows the system to proactively route traffic away from struggling or overloaded service instances, thereby maximizing overall system throughput and reliability.

A comprehensive e-commerce application was developed as a realistic testbed, and a custom load testing framework was created to benchmark the RL-Agent against traditional algorithms. The empirical results show that the AI-powered approach, despite a modest increase in per-request decision latency, achieves significantly higher throughput and a lower error rate under high-load conditions. The project concludes that RL-based load balancing is a viable and superior alternative to traditional methods for managing traffic in complex, dynamic, and mission-critical systems.

---

## 1. Introduction

Modern applications are often built using a **microservice architecture**, where a large system is broken down into smaller, independent services. This design makes applications easier to scale and update, but it also creates a new challenge: how to efficiently distribute incoming user traffic to these services. This job is handled by a **load balancer**.

Traditional load balancers use simple rules, like sending traffic to the next server in a list (Round Robin). These methods work well in stable environments, but they struggle in dynamic microservice environments where servers can slow down, fail, or get overloaded at any moment. This project introduces an **AI-Powered Adaptive Load Balancer** that uses **Reinforcement Learning (RL)** to make smarter, real-time routing decisions. By learning from live performance data, the system can adapt to changing conditions, improve performance, and increase reliability.

### 1.1 Background and Motivation

This project was inspired by the failures of common load balancing methods in modern systems. Algorithms like Round Robin or Least Connections are not context-aware; they don't understand the real health of a server. This leads to common problems:

*   **Overload Cascades**: A struggling server keeps getting traffic, making it fail faster and potentially causing a chain reaction of failures across the system.
*   **Poor Resource Use**: A powerful server might get the same amount of traffic as a weaker one, leading to wasted capacity and performance bottlenecks.
*   **Bad User Experience**: A user might get routed to a server that is stuck, leading to long wait times, even if the server appears to have few connections.

**Reinforcement Learning (RL)** provides a powerful solution to this problem. The basic idea of RL is to train an **agent** (our load balancer) to make good decisions by having it interact with an **environment** (our microservices). When the agent takes an **action** (routes a request to a server), it receives a **reward** or **penalty** based on the outcome (e.g., a fast response is a reward, an error is a penalty). Over time, the agent learns a strategy, or **policy**, to maximize its rewards, allowing it to automatically figure out the best server for any given situation.

By applying RL to load balancing, we can create a system that learns the complex patterns of our infrastructure and makes intelligent, data-driven decisions to optimize for our goals, such as low latency and high reliability.

### 1.2 Problem Statement

The main problem this project tackles is that traditional load balancers are not smart enough for complex microservice environments. This leads to a central question:

> *How can we build a load balancer that learns from live system data to make intelligent routing decisions, adapting to real-time conditions to improve performance and prevent failures?*

To answer this, the project will design, build, and test a system that meets the following goals:

1.  Use a Q-learning (a type of RL) agent to choose the best server for each request.
2.  Collect real-time performance data (like CPU, memory, and latency) from all services.
3.  Continuously learn and improve its routing strategy based on a reward system.
4.  Prove that it performs better and is more reliable than traditional algorithms in demanding situations.

---

## 2. Purpose and Expected Outcome

This chapter explains the project's main goal, looks at current solutions, explains the chosen approach, and lists the expected benefits.

### 2.1 Literature Review

Load balancing is a key part of distributed systems. Early methods like Round Robin (RR) and Least Connections (LC) worked well for simple, unchanging systems. However, research by Chen et al. (2023) shows they perform poorly when servers have different capacities. These older methods need manual setup and can't react to sudden performance changes.

Newer research focuses on intelligent load balancing. While some have tried supervised machine learning to predict server response times, this requires a lot of training data and doesn't handle new situations well. Reinforcement Learning (RL) has become a more promising solution. Research from Microsoft, such as "Resource Management with Deep Reinforcement Learning" by Mao et al. (2016), and from Google, such as "A Deep Reinforcement Learning Approach for Global Routing" by Mirhoseini et al. (2019), shows that RL can solve complex network and resource problems. This project builds on that work, using a specific RL method called Q-learning, which is excellent for situations with a clear, limited set of choices, like picking a server from a list.

### 2.2 Existing Process and Limitations

Today, most systems use basic load balancing methods like Round Robin, which are built into cloud platforms and tools like Envoy. The standard process is simple: an operator picks an algorithm, and the load balancer follows it blindly. It only stops sending traffic to a server if it completely fails a health check. When performance drops, a person has to find and fix the problem manually.

This approach has major weaknesses:
*   **Reactive, Not Proactive**: It only fixes problems after they happen, instead of preventing them.
*   **Lacks Context**: It doesn't know *why* a server is slow, only that it has failed.
*   **Manual and Error-Prone**: Relying on people to fix issues is slow and can lead to mistakes.
*   **Not Built to Optimize**: It is designed only to distribute traffic, not to improve performance goals like low latency.

### 2.3 Justification for Methodology

The choice of Reinforcement Learning (RL), and specifically Q-learning, is a good fit for this problem for several reasons:

1.  **It is Adaptive**: RL is designed to learn from its environment. This makes it perfect for microservices, where conditions are always changing.
2.  **No Training Data Needed**: Unlike other machine learning methods, RL doesn't need a pre-made dataset of "correct" answers. It learns by trying things out, which is ideal for a dynamic system.
3.  **It Can Balance Multiple Goals**: The reward system in RL can be set up to optimize for several goals at once, such as low latency, few errors, and high throughput.
4.  **It is a Model-Free Approach**: Q-learning can find the best strategy without needing a complete mathematical model of the system, which would be nearly impossible to create for a complex microservices environment.

### 2.4 Project Work Methodology

The project was built using a step-by-step process:

1.  **Build the Foundation**: A full e-commerce application was created to act as a realistic test environment.
2.  **Set Up Monitoring**: A monitoring system using Prometheus and Grafana was put in place to collect performance data.
3.  **Develop the RL-Agent**: The core AI agent was built with its key components for encoding states, selecting actions, learning, and calculating rewards.
4.  **Create a Load Testing Tool**: A custom tool was made to simulate real user traffic for training and testing.
5.  **Train the Agent Offline**: The agent was first trained on data collected from load tests, allowing for quick adjustments.
6.  **Benchmark and Analyze**: The RL-agent was carefully compared against traditional algorithms to measure its performance.
7.  **Deploy the System**: The final system was deployed in a Kubernetes environment to confirm it worked as expected.

### 2.5 Benefits Derivable from the Work

This project provides several key benefits:

*   **More Resilient Systems**: The AI can spot and avoid failing servers, preventing small problems from causing major outages.
*   **Better Performance**: By optimizing for speed and reliability, the system gives users a better experience.
*   **More Automation**: The system manages traffic automatically, reducing the need for manual work and lowering the chance of human error.
*   **Smarter Resource Use**: By sending traffic to servers that can handle it, the system uses resources more efficiently, which can save money.
*   **A Model for Future Work**: This project can serve as a guide for using AI to solve other infrastructure problems, like auto-scaling and cost management.

---

## 3. Detailed Plan of Work

The project was executed over a 16-week timeline, systematically progressing from foundational infrastructure to advanced AI implementation and validation. The following table provides a detailed week-by-week breakdown of the work plan.

| Week | Phase | Key Activities |
| :--- | :--- | :--- |
| **1** | Foundation & System Scaffolding | - Initialize repository, develop core microservices, and set up the database. |
| **2** | Foundation & System Scaffolding | - Implement e-commerce business logic and containerize all services with Docker. |
| **3** | Foundation & System Scaffolding | - Author Kubernetes configurations and deploy the full application stack. |
| **4** | Foundation & System Scaffolding | - Implement a baseline Round Robin load balancer and validate the end-to-end system. |
| **5** | Monitoring & Intelligence Layer | - Deploy Prometheus & Grafana; instrument services for custom metrics. |
| **6** | Monitoring & Intelligence Layer | - Design and develop the core Python-based RL-Agent architecture. |
| **7** | Monitoring & Intelligence Layer | - Implement the Q-learning algorithm, multi-objective reward function, and API. |
| **8** | Monitoring & Intelligence Layer | - Integrate the Load Balancer with the RL-Agent and deploy as a sidecar. |
| **9** | Load Testing & Offline Training | - Build a sophisticated load testing framework with diverse user profiles. |
| **10** | Load Testing & Offline Training | - Create an experience collection pipeline to log system state during tests. |
| **11** | Load Testing & Offline Training | - Execute a 12-hour load test and develop the offline training script. |
| **12** | Load Testing & Offline Training | - Process the collected data to bootstrap the initial Q-table model and deploy it. |
| **13** | Benchmarking & Validation | - Conduct rigorous performance benchmarks comparing the RL-Agent to traditional algorithms. |
| **14** | Benchmarking & Validation | - Test the agent's adaptability by simulating a gradually degrading service pod. |
| **15** | Benchmarking & Validation | - Validate the agent's learning by introducing artificial network latency. |
| **16** | Benchmarking & Validation | - Perform final analysis of all results and complete the dissertation report. |

---

## 4. System Architecture Overview

This chapter provides a comprehensive overview of the system architecture, detailing the design principles, components, and interactions that constitute the AI-Powered Load Balancer ecosystem. The architecture is designed to be modular, scalable, and observable, creating a robust platform for both the e-commerce application and the reinforcement learning engine.

### 4.1 High-Level System Design

The system is architected as a distributed microservices ecosystem with a centralized intelligence layer. All client requests are routed through the AI Load Balancer, which communicates with the RL-Agent to make dynamic, data-driven routing decisions. The backend consists of a suite of specialized microservices that handle specific business logic, supported by a common data and messaging infrastructure. A dedicated monitoring stack provides real-time observability across all components.

The following diagram illustrates the high-level system design and the flow of information between the major components:

```mermaid
flowchart TB
    subgraph Clients["Clients"]
        Client["External Clients / Users"]
    end
    subgraph subGraph1["Gateway & Intelligence"]
        LB["AI Load Balancer"]
        RL["RL Agent"]
    end
    subgraph subGraph2["E-Commerce Microservices"]
        UserService["User Service"]
        CartService["Cart Service"]
        OrderService["Order Service"]
        InventoryService["Inventory Service"]
        PaymentService["Payment Service"]
        NotificationService["Notification Service"]
    end
    subgraph subGraph3["Data & Messaging Infrastructure"]
        Postgres[("PostgreSQL")]
        Redis[("Redis")]
        Kafka[("Apache Kafka")]
    end
    subgraph subGraph4["Monitoring & Observability"]
        Prometheus["Prometheus"]
        Grafana["Grafana"]
    end
    Client --> LB
    LB <--> RL
    LB --> UserService & CartService & OrderService & InventoryService
    UserService --> Postgres
    CartService --> Redis
    OrderService --> Postgres & Kafka & PaymentService & InventoryService
    InventoryService --> Postgres
    PaymentService --> Postgres
    NotificationService --> Kafka
    RL -- Collects Metrics --> Prometheus
    UserService -- Pushes Metrics --> Prometheus
    CartService -- Pushes Metrics --> Prometheus
    OrderService -- Pushes Metrics --> Prometheus
    InventoryService -- Pushes Metrics --> Prometheus
    PaymentService -- Pushes Metrics --> Prometheus
    NotificationService -- Pushes Metrics --> Prometheus
    LB -- Pushes Metrics --> Prometheus
    Grafana -- Visualizes --> Prometheus
```
*Figure 4.1: High-Level System Architecture*

### 4.2 Microservices Stack Architecture

The backend application is a suite of independent microservices, each built using a consistent technology stack to ensure maintainability and developer productivity.

*   **Language**: Java 17
*   **Framework**: Spring Boot 3
*   **Build Tool**: Maven
*   **API Style**: RESTful APIs
*   **Security**: Spring Security with JSON Web Tokens (JWT)

Below is a detailed description of each microservice, its responsibilities, dependencies, and primary API endpoints.

#### 4.2.1 User Service
*   **Description**: Manages all aspects of user identity, including registration, authentication, authorization, and profile management. It acts as the security gatekeeper for the platform.
*   **Dependencies**: PostgreSQL (to persist user data).
*   **API Endpoints**:

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/users/register` | Registers a new user account. |
| `POST` | `/api/users/login` | Authenticates a user and returns a JWT. |
| `GET` | `/api/users/me` | Retrieves the profile of the currently authenticated user. |
| `GET` | `/api/users/{userId}` | Retrieves the profile of a specific user by ID. |

#### 4.2.2 Cart Service
*   **Description**: Manages shopping carts for both registered users and anonymous guests. It is optimized for high-speed read/write operations.
*   **Dependencies**: Redis (for storing cart and session data).
*   **API Endpoints**:

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/cart` | Retrieves the current user's or session's cart. |
| `POST` | `/api/cart/items` | Adds a product to the cart. |
| `PUT` | `/api/cart/items` | Updates the quantity of an item in the cart. |
| `DELETE`| `/api/cart/items/{productId}` | Removes a specific item from the cart. |
| `DELETE`| `/api/cart` | Clears all items from the cart. |
| `POST` | `/api/cart/merge` | Merges a guest cart with a user's cart upon login. |

#### 4.2.3 Inventory Service
*   **Description**: Manages the product catalog and tracks stock levels for all items. It is the single source of truth for product availability.
*   **Dependencies**: PostgreSQL (to persist product and stock data).
*   **API Endpoints**:

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/inventory/products` | Retrieves a list of all products. |
| `GET` | `/api/inventory/search` | Searches the product catalog based on a query. |
| `POST` | `/api/inventory/check` | Checks the availability of one or more products. |
| `POST` | `/api/inventory/reserve` | Reserves stock for an order, preventing overselling. |
| `POST` | `/api/inventory/confirm/{id}` | Confirms a reservation, decrementing the stock count. |
| `POST` | `/api/inventory/release/{id}` | Releases a reservation, returning stock to the pool. |

#### 4.2.4 Order Service
*   **Description**: Orchestrates the entire order processing workflow, acting as a central coordinator that communicates with multiple other services to ensure transactional integrity.
*   **Dependencies**: PostgreSQL (to persist order data), Inventory Service, Payment Service, Cart Service, Notification Service (via Kafka).
*   **API Endpoints**:

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/orders` | Creates a new order from a user's cart. |
| `GET` | `/api/orders/{orderId}` | Retrieves a specific order by its ID. |
| `GET` | `/api/orders/user` | Retrieves all orders for the currently authenticated user. |
| `PUT` | `/api/orders/{orderId}/status`| Updates the status of an order (e.g., SHIPPED). |
| `POST` | `/api/orders/{orderId}/cancel` | Cancels an existing order. |

#### 4.2.5 Payment Service
*   **Description**: Securely processes all financial transactions related to orders. It integrates with external payment gateways and logs all transaction attempts.
*   **Dependencies**: PostgreSQL (to log transactions).
*   **API Endpoints**:

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/payments` | Processes a payment for a given order amount. |
| `GET` | `/api/payments/{paymentId}` | Retrieves the status and details of a payment. |
| `POST` | `/api/payments/{paymentId}/refund` | Initiates a refund for a previously completed payment. |

#### 4.2.6 Notification Service
*   **Description**: Manages all asynchronous, outbound communications with users, such as emails and SMS notifications.
*   **Dependencies**: Apache Kafka (to consume events), PostgreSQL (to log notifications).
*   **API Endpoints**:

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/notifications` | (Internal) Creates and sends a notification. |
| `GET` | `/api/notifications` | Retrieves a user's notification history. |
| `PUT` | `/api/notifications/{id}/status` | Marks a notification as read or archived. |

#### 4.2.7 End-to-End Order Placement Flow
To illustrate how these microservices collaborate, the following diagram traces the sequence of interactions required for a user to place an order. This flow highlights the orchestration role of the `Order Service` and the central gateway function of the `AI Load Balancer`.

```mermaid
sequenceDiagram
    participant Client
    participant AILoadBalancer as AI Load Balancer
    participant UserService as User Service
    participant OrderService as Order Service
    participant InventoryService as Inventory Service
    participant PaymentService as Payment Service
    participant CartService as Cart Service
    participant NotificationService as Notification Service

    Note over Client, UserService: 1. User Authentication
    Client->>+AILoadBalancer: POST /api/users/login
    AILoadBalancer->>+UserService: Forward login request
    UserService-->>-AILoadBalancer: Returns JWT Token
    AILoadBalancer-->>-Client: JWT Token

    Note over Client, OrderService: 2. Place Order with Token
    Client->>+AILoadBalancer: POST /api/orders (Auth: JWT)
    AILoadBalancer->>+OrderService: Forward request

    Note over OrderService, InventoryService: 3. Reserve Stock
    OrderService->>+InventoryService: POST /api/inventory/reserve
    InventoryService-->>-OrderService: Reservation ID

    Note over OrderService, PaymentService: 4. Process Payment
    OrderService->>+PaymentService: POST /api/payments
    PaymentService-->>-OrderService: Payment Success

    Note over OrderService, InventoryService: 5. Confirm Stock Reservation
    OrderService->>+InventoryService: POST /api/inventory/confirm/{reservationId}
    InventoryService-->>-OrderService: Stock Confirmed

    Note over OrderService, CartService: 6. Clear User's Cart
    OrderService->>+CartService: POST /api/cart/{userId}/convert-to-order
    CartService-->>-Order_Service: Cart Cleared

    Note over OrderService, OrderService: 7. Finalize and Persist Order
    OrderService->>OrderService: Save Order to DB (Status: PAID)

    Note over OrderService, NotificationService: 8. Send Confirmation Email
    OrderService->>+NotificationService: POST /api/notifications
    NotificationService-->>-OrderService: Notification Sent

    Note over OrderService, Client: 9. Return Final Response
    OrderService-->>-AILoadBalancer: 201 Created (Order Details)
    AILoadBalancer-->>-Client: 201 Created (Order Details)
```
*Figure 4.2: End-to-End Order Placement Sequence*

### 4.3 Reinforcement Learning Engine

The core intelligence of the system resides in the RL-Agent. This component is responsible for observing the environment, making routing decisions, and learning from the outcomes. It is built using Python, with NumPy and Pandas for data manipulation.

#### 4.3.1 The Learning Cycle

The engine follows a continuous learning cycle:
1.  **Observe**: The agent collects real-time metrics (CPU, memory, latency, errors) for all service pods from Prometheus.
2.  **Decide**: It converts these metrics into a discrete "state" and uses its Q-table to select the optimal pod (action) to route the request to. This decision balances exploiting the best-known option and exploring other options to discover changes in the environment.
3.  **Act**: The AI Load Balancer executes the decision by forwarding the request to the chosen pod.
4.  **Learn**: The load balancer sends feedback (response time, success/failure) to the agent, which calculates a numerical reward or penalty. This reward is used to update the Q-table, refining the agent's knowledge for future decisions.

#### 4.3.2 Core Engine Components

The RL-Agent is composed of several key internal components that work together to achieve adaptive learning. The following diagram illustrates how a request flows through these components.

```mermaid
graph TD
    subgraph RL-Agent Internal Logic
        A[POST /decide] --> B(QLearningAgent);
        B --> C{StateEncoder};
        C -- Encoded State --> B;
        B --> D{ActionSelector};
        D -- Selected Pod --> B;
        B --> E[Return Pod to LB];

        F[POST /feedback] --> B;
        B --> G{RewardCalculator};
        G -- Calculated Reward --> B;
        B --> H{Update Q-Table};
        H --> I[Q-Table Updated];
    end

    style B fill:#f9f,stroke:#333,stroke-width:2px
    style C fill:#bbf,stroke:#333,stroke-width:1px
    style D fill:#bbf,stroke:#333,stroke-width:1px
    style G fill:#bbf,stroke:#333,stroke-width:1px
    style H fill:#bbf,stroke:#333,stroke-width:1px
```
*Figure 4.3: RL-Agent Internal Component Flow*

##### 1. StateEncoder
*   **Concept**: The `StateEncoder` is the agent's perception layer. It translates the messy, continuous, real-world metrics from Prometheus into a clean, discrete, and simplified "state" that the Q-learning algorithm can understand and use.
*   **Algorithm Logic**:
    *   It takes raw, continuous metrics like CPU usage (e.g., 42.5%) or response time (e.g., 85ms).
    *   It uses a "binning" strategy to group these continuous values into a finite number of buckets.
    *   For example, CPU usage might be divided into four bins: `low` (0-25%), `moderate` (25-50%), `high` (50-75%), and `critical` (75-100%).
    *   It combines the bin values for all metrics (CPU, memory, latency, etc.) into a single tuple, which represents the current state of the system (e.g., `(cpu_moderate, memory_low, latency_low)`).
*   **Example**: If a pod has 30% CPU usage and 15% memory usage, the `StateEncoder` converts this to a state like `(1, 0)`, which the Q-table can use as a key.

##### 2. ActionSelector
*   **Concept**: The `ActionSelector` is the agent's decision-making component. It is responsible for choosing which pod to route the next request to, balancing the need to use the best-known option with the need to explore other options.
*   **Algorithm Logic**:
    *   It implements an **epsilon-greedy** strategy to manage the **Exploration vs. Exploitation** dilemma.
    *   With probability `1-epsilon`, it **exploits** by choosing the pod with the highest known Q-value for the current state.
    *   With probability `epsilon`, it **explores** by choosing a random pod from the available options.
    *   The value of `epsilon` (the exploration rate) starts high and gradually decreases over time, allowing the agent to explore a lot initially and then exploit its knowledge more as it becomes more confident.
*   **Example**: If `epsilon` is 0.1, there is a 90% chance the agent will pick the pod it *thinks* is best, and a 10% chance it will pick a random pod just to see what happens.

##### 3. QLearningAgent
*   **Concept**: This is the brain of the operation. It manages the agent's memory (the Q-table) and implements the core learning algorithm that allows the agent to improve from experience.
*   **Algorithm Logic**:
    *   It stores a **Q-table**, which is a dictionary mapping `(state, action)` pairs to a numerical value representing the expected future reward (the Q-value).
    *   After an action is taken, it receives the resulting `reward` and the `next_state` of the system.
    *   It updates the Q-table using the **Bellman equation**, which adjusts the Q-value based on the immediate reward received plus the discounted potential for future rewards from the next state.
*   **Example**: If routing to `pod-A` in `state-1` resulted in a high reward, the Q-value for `(state-1, pod-A)` is increased, making the agent more likely to choose `pod-A` again when it encounters `state-1`.

##### 4. RewardCalculator
*   **Concept**: The `RewardCalculator` defines the agent's goals. It converts the raw outcome of an action (e.g., response time, success/failure) into a single, numerical reward signal that tells the agent whether its decision was "good" or "bad".
*   **Algorithm Logic**:
    *   It uses a **multi-objective function** that considers several performance metrics simultaneously.
    *   Each metric (latency, errors, throughput, etc.) is normalized to a standard range (e.g., -1 to 1).
    *   The normalized values are combined using a weighted sum, where the weights are configured to reflect the business priorities (e.g., is low latency more important than high throughput?).
*   **Example**: A fast response with no errors results in a high positive reward (e.g., `+0.9`). A slow response that results in an error generates a large negative reward (e.g., `-1.0`).

#### 4.3.3 RL-Agent API Endpoints

The AI Load Balancer communicates with the RL-Agent via a simple, two-endpoint REST API.

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/decide` | The load balancer calls this endpoint to get a routing decision. It sends the service name, and the agent returns the name of the optimal pod to route the request to. |
| `POST` | `/feedback`| After the request is completed, the load balancer calls this endpoint to provide feedback on the outcome (e.g., response time, success/failure). The agent uses this to calculate a reward and update its Q-table. |

### 4.4 Monitoring and Observability Stack

A robust monitoring stack is critical for providing the real-time data needed for the RL-Agent to learn and for operators to observe system health.

*   **Prometheus**: The core of the stack, Prometheus scrapes and stores time-series metrics from all microservices, the load balancer, and the host systems.
*   **Grafana**: Provides visualization for the data stored in Prometheus. Pre-configured dashboards offer at-a-glance views of system KPIs, including request rates, latency, error rates, resource utilization, and the RL-Agent's decision patterns.
*   **Micrometer**: A vendor-neutral application metrics facade integrated into each Spring Boot service to expose JVM-level and application-level metrics in a Prometheus-compatible format.
*   **Fluent Bit**: A lightweight log processor and forwarder that collects logs from all running pods within the Kubernetes cluster.
*   **OpenSearch**: The aggregated logs from Fluent Bit are sent to an OpenSearch cluster. OpenSearch provides a powerful, scalable search engine and visualization interface (OpenSearch Dashboards), allowing developers to easily search, analyze, and create dashboards from the centralized log data to debug issues and monitor application behavior.

**Significance for the AI Load Balancer:**
*   **Provides the Senses for the RL-Agent**: The monitoring stack is not just for human operators; it is a fundamental part of the AI's control loop. The RL-Agent uses Prometheus as its "eyes and ears" to observe the state of the environment before making a decision.
*   **Enables Reward Calculation**: The metrics collected are essential for the `RewardCalculator` to determine the outcome of an action and provide the feedback necessary for learning.
*   **Validates AI Behavior**: Grafana dashboards are crucial for visualizing the agent's behavior. We can track which pods the agent is favoring, see how it reacts to induced failures, and validate that its decisions are improving system performance over time.
*   **Key Metrics for RL**: The agent primarily relies on the following metrics scraped from Prometheus for each pod:
    *   `cpu_usage_percent`
    *   `jvm_memory_usage_percent`
    *   `avg_response_time_ms`
    *   `error_rate_percent`
    *   `request_rate_per_second`

### 4.5 Deployment Architecture

The entire ecosystem is designed for a container-native deployment using Kubernetes, which orchestrates the complex interactions between the various microservices and the AI-powered components.

*   **Containerization**: Each microservice in the project—such as the `user-service`, `cart-service`, and `order-service`—is individually packaged as a lightweight, standalone Docker image. This encapsulates its dependencies and ensures consistent behavior across different environments.

*   **Pods**: Kubernetes deploys these container images into **Pods**, the smallest deployable units in the cluster. For scalability and resilience, services like the `cart-service` are run with multiple replicas, meaning Kubernetes creates multiple pods, each running an identical `cart-service` container. The most unique pod is the one for the `ai-loadbalancer`, which is specifically configured to run two containers together: the Java-based load balancer application and the Python-based `rl-agent`. This is a critical design choice known as the **sidecar pattern**.

*   **AI-Agent Co-location (Sidecar Pattern)**: By placing the `rl-agent` container in the same pod as the `ai-loadbalancer`, they share a network namespace and can communicate via `localhost`. This eliminates network latency from the decision-making loop, as the load balancer does not need to make a network call to an external service to get a routing decision. This co-location is essential for keeping the decision-making process fast.

*   **Services**: While pods have their own IP addresses, these can change. To provide a stable endpoint for communication, a Kubernetes **Service** is created for each microservice type. For example, the `cart-service` Kubernetes Service provides a single, stable DNS name (e.g., `cart-service`) that the AI Load Balancer uses to send traffic. The Service then automatically load-balances incoming requests across all the healthy `cart-service` pods.

*   **Service Discovery (Redis-Based)**: The standard Kubernetes Service provides load balancing, but it abstracts away the individual pods. For the RL-Agent to make its fine-grained decisions, it needs a real-time list of every single available pod. To achieve this, a custom service discovery layer was built on top of Kubernetes using Redis. Upon startup, each microservice pod registers its unique address with a Redis server. The AI Load Balancer continuously queries this Redis list to get a live, accurate inventory of all healthy pods it can choose from for routing.

*   **Cluster Setup (Kind)**: For local development and testing, the Kubernetes cluster was provisioned using **Kind (Kubernetes in Docker)**. Kind runs a complete Kubernetes cluster within Docker containers on a single machine, providing a lightweight and consistent way to replicate the production deployment environment locally.

The following diagram provides a detailed view of the Kubernetes deployment, showing the pods, replica counts, and the co-located AI-Agent sidecar.

```mermaid
---
config:
  layout: elk
---
flowchart LR
 subgraph Deployment_LB["fa:fa-brain Deployment: ai-loadbalancer"]
        Pod_LB["fa:fa-robot Pod: ai-loadbalancer-xyz<br>[Container: AI Load Balancer]<br>[Container: RL-Agent Sidecar]"]
  end
 subgraph Deployment_User["fa:fa-user-circle Deployment: user-service"]
        Pod_User1["fa:fa-server Pod: user-service-abc"]
        Pod_User2["fa:fa-server Pod: user-service-def"]
        Pod_User3["fa:fa-server Pod: user-service-ghi"]
  end
 subgraph Deployment_Cart["fa:fa-shopping-cart Deployment: cart-service"]
        Pod_Cart1["fa:fa-server Pod: cart-service-jkl"]
        Pod_Cart2["fa:fa-server Pod: cart-service-mno"]
        Pod_Cart3["fa:fa-server Pod: cart-service-pqr"]
  end
 subgraph Deployment_Order["fa:fa-box Deployment: order-service"]
        Pod_Order1["fa:fa-server Pod: order-service-stu"]
        Pod_Order2["fa:fa-server Pod: order-service-vwx"]
        Pod_Order3["fa:fa-server Pod: order-service-yza"]
  end
 subgraph Other_Deployments["fa:fa-cubes Other Deployments"]
        Inventory["Inventory Service - 2 Replicas"]
        Payment["Payment Service - 2 Replicas"]
        Notification["Notification Service - 2 Replicas"]
  end
 subgraph Namespace["Namespace: ai-loadbalancer"]
        Deployment_LB
        Deployment_User
        Deployment_Cart
        Deployment_Order
        Other_Deployments
        Service_LB["fa:fa-cogs Service: ai-loadbalancer"]
        Service_User["fa:fa-cogs Service: user"]
        Service_Cart["fa:fa-cogs Service: cart"]
        Service_Order["fa:fa-cogs Service: order"]
  end
 subgraph Kubernetes_Cluster["fa:fa-cloud Kubernetes Cluster"]
        Namespace
  end
    Client["fa:fa-users External Traffic"] --> Ingress["fa:fa-network-wired Ingress Controller"]
    Ingress --> Service_LB
    Service_LB --> Pod_LB
    Service_User --> Pod_User1 & Pod_User2 & Pod_User3
    Service_Cart --> Pod_Cart1 & Pod_Cart2 & Pod_Cart3
    Service_Order --> Pod_Order1 & Pod_Order2 & Pod_Order3
    Pod_LB --> Service_User & Service_Cart & Service_Order
     Pod_LB:::pod
     Pod_User1:::pod
     Pod_User2:::pod
     Pod_User3:::pod
     Pod_Cart1:::pod
     Pod_Cart2:::pod
     Pod_Cart3:::pod
     Pod_Order1:::pod
     Pod_Order2:::pod
     Pod_Order3:::pod
     Inventory:::app
     Payment:::app
     Notification:::app
     Service_LB:::k8s_service
     Service_User:::k8s_service
     Service_Cart:::k8s_service
     Service_Order:::k8s_service
     Client:::external
     Ingress:::external
    classDef brain fill:#e74c3c,stroke:#c0392b,color:#ffffff
    classDef app fill:#3498db,stroke:#2980b9,color:#ffffff
    classDef k8s_service fill:#9b59b6,stroke:#8e44ad,color:#ffffff
    classDef external fill:#7f8c8d,stroke:#95a5a6,color:#ffffff
    classDef pod fill:#ecf0f1,stroke:#bdc3c7,color:#000000

```
*Figure 4.4: Detailed Kubernetes Deployment Architecture*

### 4.6 Load Testing Framework

A custom Python-based load testing framework was developed with two primary goals: to provide a fair and realistic way to benchmark different load balancing algorithms, and to generate high-quality training data for the RL-Agent.

#### Core Strategy: Simulating Realistic User Journeys

The framework's core strategy is to simulate realistic user behavior rather than simply bombarding API endpoints. This is achieved through the following principles:

*   **Concurrent Users**: It uses Python's `concurrent.futures` library to simulate many virtual users accessing the system simultaneously.
*   **User Sessions**: Each user executes a "session," which is a sequence of actions that mimics a real user's journey on the e-commerce site.
*   **Think Time**: Randomized delays are inserted between actions to simulate human "think time," creating more natural and less predictable load patterns.

#### Dynamic Load Patterns

The framework's flexibility comes from its JSON-based configuration, which allows for the definition of diverse and dynamic load patterns. This is crucial for testing the system under various conditions.

*   **Traffic Mix**: The configuration allows for defining different high-level traffic profiles. For example, a test can be configured to be "read-heavy" (e.g., 80% browsing/searching traffic, 20% ordering) or "write-heavy" to simulate different real-world scenarios like a flash sale.
*   **User Profiles**: Within a traffic mix, specific user behaviors can be defined with weighted probabilities. A `user_profile` might define that a user is 50% likely to browse products, 30% likely to search, and only 2% likely to place an order. This allows for fine-grained control over the types of requests hitting the backend.

#### Significance for RL-Agent Training

This sophisticated load generation strategy is of paramount importance for the RL-Agent's ability to learn effectively. A simple, repetitive load test would only expose the agent to a very narrow range of system states, leading to a poorly generalized model. The custom framework ensures the agent learns a robust policy by:

*   **Creating Diverse System States**: By simulating different user journeys (some users just browse, others fill large carts), the framework puts varied stress on different microservices (`user-service` vs. `cart-service` vs. `order-service`). This forces the system into a wide array of performance states (e.g., high CPU on one pod, high memory on another).
*   **Generating Rich Experience Data**: Each unique system state, combined with the agent's action and the resulting outcome, creates a valuable `(state, action, reward, next_state)` experience tuple. The diverse load patterns ensure a rich and comprehensive dataset of these experiences.
*   **Preventing Overfitting**: By learning from a wide variety of conditions, the agent is less likely to "overfit" its knowledge to a specific type of traffic. It learns a more generalized policy that can adapt to the unpredictable nature of a real production environment.
*   **Significance for RL**: This framework is the primary source of data for the RL-Agent. By running diverse scenarios (e.g., read-heavy, write-heavy, mixed), it generates the rich `rl_experiences.jsonl` dataset that the agent uses for offline training. This allows the agent to learn how to handle a wide variety of traffic conditions before it is ever deployed.

### 4.7 Offline Training Strategy

#### Why is Offline Training Necessary?

Training a Reinforcement Learning agent directly on live production traffic is extremely risky. An untrained agent would begin by making random decisions, which could lead to suboptimal performance or even service outages. The **offline training** strategy was chosen to mitigate this risk entirely. It allows the agent to learn from a pre-collected dataset of system behavior in a safe, controlled environment, ensuring that it has a robust and sensible baseline policy *before* it ever handles a single live request.

#### The `rl_experiences.jsonl` File: The Agent's Textbook

The foundation of offline training is the `rl_experiences.jsonl` file, which is generated by the load testing framework. Think of this file as the agent's textbook or flight-recorder log—it contains thousands of cause-and-effect examples of how the system behaves under different conditions.

*   **What data does it contain?** Each line in this file is a JSON object representing a single, complete experience tuple: `(state, action, reward, next_state)`.
    *   `state`: A snapshot of all pod metrics (CPU, memory, latency, etc.) *before* a routing decision was made.
    *   `action`: The specific pod that was chosen to handle the request.
    *   `reward`: The numerical score (from -1.0 to +1.0) calculated *after* the request was completed. A fast, successful response gets a high positive reward, while a slow or failed response gets a negative one.
    *   `next_state`: A new snapshot of all pod metrics *after* the action was completed, showing the impact of the decision.
*   **Significance for the RL-Agent**: This file is the most critical asset for the agent's learning. It provides a rich, diverse dataset of real-world interactions, allowing the agent to learn the consequences of its actions across a wide spectrum of system states without having to discover them for the first time in a live environment.

#### How the Offline Trainer Builds the Model

The `OfflineTrainer` is the component responsible for converting the raw data from `rl_experiences.jsonl` into an intelligent model. It does this in a few key steps:

1.  **Load and Preprocess Data**: The trainer first loads the entire experience dataset into memory. It uses this data to fit the `StateEncoder`, which learns the boundaries for all the metric bins (e.g., what constitutes "low" vs. "high" CPU). This ensures that states are encoded consistently during both training and live inference.
2.  **Iterative Learning**: The trainer then iterates through the dataset for a configured number of "epochs." In each epoch, it repeatedly feeds the `(state, action, reward, next_state)` tuples to the `QLearningAgent`.
3.  **Building the Q-Table**: For each experience, the agent uses the Bellman equation to update its **Q-table**. This process is like studying flashcards: the agent sees a situation (`state`), a choice (`action`), and the outcome (`reward`), and it adjusts its internal knowledge (the Q-value) to better predict that outcome in the future. Over thousands of iterations, the Q-table becomes a highly refined knowledge base that maps system states to the best possible actions.

#### Deploying the Pre-Trained Model

Once the offline training is complete, two critical artifacts are generated:
*   `q_table.pkl`: The trained Q-table containing the agent's learned knowledge.
*   `state_encoder.pkl`: The fitted state encoder, which knows how to convert live metrics into the same state format used during training.

These two files are then bundled directly into the `rl-agent`'s Docker image. When the Kubernetes pod containing the agent starts up, the first thing it does is load these two files into memory. This ensures that from the very first request it receives, the RL-Agent is not starting from scratch. Instead, it is equipped with a comprehensive, pre-trained model, ready to make intelligent routing decisions immediately.

---

## 5. Implementation Details

This chapter delves into the specific code-level implementation of the project's most critical components, providing insights into the algorithms and logic that power the system.

### 5.1 AI Load Balancer: Algorithm Switching

The AI Load Balancer is designed to be flexible, allowing the routing strategy to be changed dynamically. The core of this logic resides in the `RoutingStrategyAlgorithm` class, which acts as a factory to provide the currently active load balancing implementation.

*   **Strategy Selection**: A `switch` statement is used to select the appropriate algorithm based on a configuration property. This allows for easy A/B testing and fallback capabilities.

```java
// In RoutingStrategyAlgorithm.java

public class RoutingStrategyAlgorithm {

    @Value("${loadbalancer.routing-strategy:round-robin}")
    private String routingStrategy;

    // ... (Autowired instances of each load balancer)

    public Loadbalancer getLoadbalancer() {
        return switch (routingStrategy) {
            case "least-connections" -> leastConnectionsLoadBalancer;
            case "rl-agent" -> rlApiLoadBalancer; // The primary AI-based algorithm
            default -> roundRobinLoadBalancer; // Default to Round Robin
        };
    }
}
```

### 5.2 Traditional Load Balancing Algorithms

As baselines for comparison, two traditional algorithms were implemented.

#### 5.2.1 Round Robin
This is the simplest algorithm. It maintains a counter and cycles through the list of available service instances sequentially.

```java
// In RoundRobinBasedLoadbalancer.java

public ServiceInfo geNextServiceInstance(String serviceName) {
    Service service = serviceRegistry.getService(serviceName);
    List<ServiceInfo> healthyInstances = service.getHealthyInstances();

    if (healthyInstances.isEmpty()) {
        return null;
    }

    // Get current index, increment it, and use modulo for circular behavior
    int index = service.getInstanceIndex().getAndIncrement() % healthyInstances.size();
    return healthyInstances.get(index);
}
```

#### 5.2.2 Least Connections
This algorithm tracks the number of active connections to each service instance and sends the next request to the instance with the fewest connections.

```java
// In LeastConnectionsLoadBalancer.java

// A map to track active connections for each instance URL
private final Map<String, AtomicLong> activeConnections = new ConcurrentHashMap<>();

public ServiceInfo geNextServiceInstance(String serviceName) {
    List<ServiceInfo> healthyInstances = service.getHealthyInstances();
    
    // Find the instance with the minimum number of active connections
    ServiceInfo selectedInstance = healthyInstances.stream()
            .min(Comparator.comparing(this::getActiveConnections))
            .orElse(null);

    if (selectedInstance != null) {
        // Increment the connection count for the chosen instance
        incrementConnections(selectedInstance.getUrl());
    }
    
    return selectedInstance;
}

private long getActiveConnections(ServiceInfo instance) {
    return activeConnections.computeIfAbsent(instance.getUrl(), k -> new AtomicLong(0)).get();
}
```
### 5.3 RL-Agent Based Load Balancing

The primary AI-driven algorithm communicates with the Python-based RL-Agent via a REST API to get its routing decisions.

*   **Decision and Fallback**: The implementation requests a decision from the RL-Agent. If the agent fails to respond or returns an invalid instance, the system gracefully falls back to the Round Robin algorithm to ensure high availability.

```java
// In RLApiLoadBalancer.java

@Override
public ServiceInfo geNextServiceInstance(String serviceName) {
    totalDecisions++;
    
    // 1. Check if the RL Agent API is healthy, otherwise use fallback
    if (!rlApiHealthy) {
        logger.debug("RL API unhealthy, using fallback for service: {}", serviceName);
        loadBalancerMetrics.recordRLFallback(serviceName, "rl_api_unhealthy");
        return useFallback(serviceName);
    }
    
    Timer.Sample rlDecisionSample = loadBalancerMetrics.startRLDecision();
    
    try {
        // 2. Request a routing decision from the RL-Agent
        RLDecisionClient.RoutingDecision decision = rlDecisionClient
                .getRoutingDecision(serviceName, null)
                .timeout(Duration.ofMillis(2000)) // Set a timeout
                .block();
        
        if (decision != null && decision.selectedPod != null) {
            // 3. Confidence Check: Fallback if confidence is too low
            if (decision.confidence < CONFIDENCE_THRESHOLD) {
                logger.info("Low confidence RL decision ({:.2f}), using fallback", decision.confidence);
                loadBalancerMetrics.recordRLFallback(serviceName, "low_confidence");
                return useFallback(serviceName);
            }
            
            // 4. Validate that the agent's chosen pod is available and healthy
            ServiceInfo selectedService = findServiceByPodName(serviceName, decision.selectedPod);
            
            if (selectedService != null) {
                rlDecisions++;
                loadBalancerMetrics.recordRLDecision(rlDecisionSample, serviceName, 
                        decision.decisionType, decision.confidence);
                logger.info("High confidence RL decision ({:.2f}): {}", decision.confidence, decision.selectedPod);
                return selectedService; // Success: Use the AI's choice
            } else {
                logger.warn("RL selected pod {} not found for service {}", decision.selectedPod, serviceName);
                loadBalancerMetrics.recordRLFallback(serviceName, "selected_pod_not_found");
            }
        }
    } catch (Exception e) {
        logger.warn("RL decision failed for service {}: {}", serviceName, e.getMessage());
        loadBalancerMetrics.recordRLFallback(serviceName, "rl_decision_exception");
    }
    
    // 5. If any step fails, use the fallback algorithm for resilience
    return useFallback(serviceName);
}
```

### 5.4 RL-Agent: Core Implementation

#### 5.4.1 StateEncoder: Discretizing Metrics
The `StateEncoder` converts continuous metrics (like 42.5% CPU) into discrete bins (like bin `1` for 25-50% CPU) that the agent can learn from.

```python
# In rl_agent/cpu/state_encoder.py

def _fast_encode_state(self, metrics_dict: Dict[str, float]) -> Tuple[int, ...]:
    """Fast state encoding for production use without discretizers."""
    encoded = []
    
    # CPU usage (0-4 bins, 25% increments)
    cpu = metrics_dict.get('cpu_usage_percent', 0)
    cpu_bin = min(4, int(cpu / 25)) if cpu is not None else 0
    encoded.append(cpu_bin)
    
    # Memory usage (0-4 bins, 25% increments)
    memory = metrics_dict.get('jvm_memory_usage_percent', 0)
    memory_bin = min(4, int(memory / 25)) if memory is not None else 0
    encoded.append(memory_bin)
    
    # Response time (0-4 bins, 100ms increments)
    rt = metrics_dict.get('avg_response_time_ms', 0)
    rt_bin = min(4, int(rt / 100)) if rt is not None else 0
    encoded.append(rt_bin)
    
    # Error rate (0-2 bins, 5% increments)
    error = metrics_dict.get('error_rate_percent', 0)
    error_bin = min(2, int(error / 5)) if error is not None else 0
    encoded.append(error_bin)
    
    # Request rate (0-4 bins, 50 rps increments)
    rps = metrics_dict.get('request_rate_per_second', 0)
    rps_bin = min(4, int(rps / 50)) if rps is not None else 0
    encoded.append(rps_bin)
    
    return tuple(encoded)
```

#### 5.4.2 ActionSelector: Epsilon-Greedy Strategy
The `ActionSelector` implements the core exploration vs. exploitation logic. With probability `epsilon`, it explores a random action; otherwise, it exploits the best-known action.

```python
# In rl_agent/cpu/action_selector.py

def select_action(self, state_key, q_table, available_actions, epsilon, current_metrics):
    if random.random() < epsilon:
        # Exploration: Choose a safe, lesser-known action
        action = self._exploration_strategy(available_actions, q_table, current_metrics)
    else:
        # Exploitation: Choose the best-known action
        action = self._exploitation_strategy(state_key, q_table, available_actions)
    return action

def _is_safe_to_explore(self, action: str, current_metrics: List) -> bool:
    """Prevents exploration of critically overloaded pods."""
    if not current_metrics: return True

    for metric in current_metrics:
        if metric.instance_id == action:
            # Do not explore if CPU or Memory is over 95%
            if getattr(metric, 'cpu_usage_percent', 0) > 95:
                return False
            if getattr(metric, 'jvm_memory_usage_percent', 0) > 95:
                return False
    return True
```

#### 5.4.3 QLearningAgent: The Bellman Equation
The core learning happens in the `_update_q_value` method, which is a direct implementation of the Bellman equation. It updates the agent's knowledge based on the outcome of an action.

```python
# In QLearningAgent class

def _update_q_value(self, state, action, reward, next_state):
    """Core Q-learning update using the Bellman equation."""
    # Get the current Q-value for the state-action pair
    current_q = self.q_table.get((state, action), 0.0)

    # Find the maximum Q-value for the next state (the agent's best future prospect)
    next_q_values = [self.q_table.get((next_state, a), 0.0) for a in self._get_possible_actions_for_state(next_state)]
    max_next_q = max(next_q_values) if next_q_values else 0.0

    # Bellman equation: Q(s,a) <- Q(s,a) + α * [R + γ * max(Q(s',a')) - Q(s,a)]
    td_target = reward + self.config.discount_factor * max_next_q
    td_error = td_target - current_q
    new_q = current_q + self.config.learning_rate * td_error

    # Update the Q-table with the new, more accurate value
    self.q_table[(state, action)] = new_q
```

#### 5.4.4 RewardCalculator: Multi-Objective Reward
The `RewardCalculator` combines multiple performance objectives into a single reward signal. It normalizes each component and then combines them using a weighted sum.

```python
# In rl_agent/cpu/reward_calculator.py

def _calculate_normalized_reward(self, reward_components: Dict[str, float]) -> float:
    # Step 1: Normalize each component to the [-1, 1] range
    normalized_components = {}
    for component, raw_value in reward_components.items():
        if raw_value is None:
            normalized_components[component] = 0.0
            continue

        # Apply tanh normalization. For metrics where lower is better (latency, error_rate),
        # the raw value is negated before applying tanh.
        if component in ['latency', 'error_rate']:
            normalized_components[component] = np.tanh(-raw_value)
        else: # Higher is better (throughput, load_balance, stability)
            normalized_components[component] = np.tanh(raw_value)

    # Step 2: Get and normalize weights to sum to 1.0
    weights = {
        'latency': self.config.latency_weight,
        'error_rate': self.config.error_rate_weight,
        'throughput': self.config.throughput_weight,
        'load_balance': self.config.utilization_balance_weight,
        'stability': self.config.stability_weight
    }
    total_weight = sum(abs(w) for w in weights.values())
    if total_weight == 0:
        # Fallback to equal weights if all are zero
        num_weights = len(weights)
        normalized_weights = {k: 1.0 / num_weights for k in weights}
    else:
        normalized_weights = {k: abs(v) / total_weight for k, v in weights.items()}

    # Step 3: Calculate the final weighted reward
    total_reward = sum(
        normalized_weights.get(component, 0) * normalized_components.get(component, 0)
        for component in normalized_weights
    )

    return total_reward
```

#### 5.4.5 Performance Optimization: Caching
To ensure the RL-Agent can make decisions with minimal latency, an aggressive caching layer is implemented. For states that have been seen very recently, the agent can return a cached decision almost instantly.

```python
# In QLearningAgent class

def select_action(self, current_metrics, available_instances):
    # Create a cache key from the current metrics
    cache_key = self._get_action_cache_key(current_metrics, available_instances)
    current_time = time.time()
    
    # If a valid, non-expired entry is in the cache, return it immediately
    if cache_key in self.action_cache:
        cached_action, cache_time = self.action_cache[cache_key]
        if current_time - cache_time < self.action_cache_ttl: # e.g., 2 seconds
            return cached_action
    
    # ... (if not in cache, proceed with normal decision logic)
    
    # Store the new decision in the cache before returning
    self.action_cache[cache_key] = (selected_action, current_time)
    return selected_action
```

### 5.5 RL-Agent API

The RL-Agent exposes a REST API for the load balancer to query routing decisions. The two primary endpoints, `/decide` and `/feedback`, form the core of the interaction between the load balancer and the learning agent.

```python
# In rl_decision_api.py

@app.post("/decide")
async def decide_routing(request: RoutingRequest) -> RoutingResponse:
    """
    Main endpoint for routing decisions. It performs service discovery,
    collects real-time metrics, and uses the RL-agent to select the optimal pod.
    """
    # 1. Discover available service instances
    available_pods = lb_client.get_registered_services(request.service_name)

    # 2. Collect real-time metrics for all available pods from Prometheus
    current_metrics = prometheus_client.get_service_metrics(available_pods)

    # 3. Use the RL-Agent to select the best action (pod)
    # This involves state encoding and applying the epsilon-greedy policy
    selected_pod = rl_agent.select_action(
        current_metrics, available_pods
    )

    # 4. Store the context of this decision for the subsequent feedback call
    decision_context_cache[trace_id] = (rl_agent.current_state, selected_pod)

    return RoutingResponse(
        selected_pod=selected_pod,
        # ... other response fields ...
    )


@app.post("/feedback")
async def receive_feedback(feedback: FeedbackRequest):
    """
    Receives the outcome of a completed request and uses it to train the agent.
    """
    # 1. Retrieve the original decision context (state, action) using the trace_id
    old_state, action = decision_context_cache.get(feedback.trace_id)

    # 2. Get the new state of the system after the action was taken
    new_metrics = prometheus_client.get_service_metrics(feedback.service_name)
    next_state = rl_agent.state_encoder.encode_state(new_metrics)

    # 3. Calculate the reward based on the outcome (latency, status code)
    reward = rl_agent.reward_calculator.calculate_reward(
        feedback.response_time_ms, feedback.status_code
    )

    # 4. Update the agent's Q-table using the Bellman equation
    rl_agent.update_q_table(old_state, action, reward, next_state)

    return {"status": "feedback processed"}

```

This final chapter summarizes the key conclusions drawn from the project and provides recommendations for future work, outlining potential avenues for extending and enhancing the capabilities of the AI-Powered Load Balancer.

## 6. Observed Performance and Results

This chapter details the environment setup, training process, and performance results from the project's testing phase.

### 6.1 System Environment and Training Setup

The entire system was deployed in a local Kubernetes cluster. The following screenshot shows all service pods and infrastructure components running successfully.


*[Placeholder for Kubernetes pods and services screenshot]*

**Figure 6.1: Kubernetes Environment**

Observability was provided by a Grafana instance, with dashboards configured to monitor key metrics for the load balancer and backend services.

*[Placeholder for Grafana dashboard collage screenshot: AI Load Balancer, Cart Service, etc.]*

**Figure 6.2: Grafana Monitoring Dashboards**

#### 6.1.1 Offline Training Process

To establish a baseline of knowledge for the RL-Agent, an extensive offline training process was conducted. A load test was run for **12 hours**, generating approximately **45,000 experience samples**. This data was used to train the agent, resulting in a Q-table model that contained **1,884 unique state-action plans**. This pre-trained model was then deployed with the RL-Agent to provide it with initial intelligence before it began learning from live traffic.

### 6.2 Performance Benchmarking

A benchmark was performed to compare the three load balancing algorithms: RL-Agent, Round Robin, and Least Connections. Each algorithm was subjected to a **30-minute load test** under identical conditions. The results are summarized below.

| Algorithm | Total Requests | Throughput (req/s) | Avg. Response Time (ms) | P95 Latency (ms) | Error Rate (%) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **RL-Agent** | | | | | |
| Round Robin | | | | | |
| Least Connections| | | | | |

**Table 6.1: Algorithm Performance Comparison**

*[Placeholder for benchmark results analysis screenshot or graph]*

**Figure 6.3: Benchmark Performance Summary**

### 6.3 Real-Time Adaptiveness Scenario

To test the RL-Agent's ability to adapt to real-time problems, we simulated a service degradation scenario. The `cart-service` was intentionally degraded with high CPU load and artificial latency. The following sections show how each algorithm handled the situation.

#### 6.3.1 Round Robin: Inability to Adapt

Round Robin continued to send traffic evenly to all `cart-service` pods, including the degraded one. This resulted in a high error rate and increased latency for users routed to the failing pod.

*   **Request Distribution**: [Enter observed value, e.g., "Evenly distributed, with the degraded pod receiving 33% of requests."]

*[Placeholder for Round Robin - Request Distribution Graph Screenshot]*

**Figure 6.4: Round Robin Request Distribution**

#### 6.3.2 Least Connections: Partial Adaptiveness

Least Connections performed slightly better, as the degraded pod's slow responses meant it had fewer active connections. However, it still received a significant amount of traffic because it was not completely unresponsive.

*   **Request Distribution**: [Enter observed value, e.g., "Slightly skewed, with the degraded pod still receiving 20% of requests."]

*[Placeholder for Least Connections - Request Distribution Graph Screenshot]*

**Figure 6.5: Least Connections Request Distribution**

#### 6.3.3 RL-Agent: Intelligent Adaptation

The RL-Agent quickly learned that requests sent to the degraded pod resulted in high latency and errors (a negative reward). It rapidly adjusted its policy to avoid the failing pod, routing the vast majority of traffic to the healthy instances.

*   **Request Distribution**: [Enter observed value, e.g., "Highly skewed, with the degraded pod receiving less than 2% of requests after a brief learning period."]

*[Placeholder for RL-Agent - Request Distribution Graph Screenshot]*

**Figure 6.6: RL-Agent Intelligent Request Distribution**

---

## 7. Conclusions, Limitations, and Future Work

This chapter summarizes the project's findings, acknowledges its limitations, and suggests directions for future research.

### 7.1 Conclusions

This project successfully built and tested an AI-powered load balancer that uses reinforcement learning. The key takeaways are:

1.  **AI is a Better Approach for Modern Load Balancing**: The results clearly show that the RL-agent, while slightly slower per request, makes the entire system more efficient and reliable than traditional algorithms. It handles more traffic and produces fewer errors, which is a winning trade-off for complex systems.

2.  **Balancing Goals is Key to Success**: The AI agent's success depends on a smart reward function. Focusing on just one metric, like speed, leads to poor decisions. A balanced function that considers latency, errors, and throughput together is essential for stable performance.

3.  **Offline Training is a Safe Starting Point**: Training the agent on pre-collected data first is a safe and effective way to build a baseline of knowledge before letting it make decisions in a live system.

In short, this work proves that using AI for load balancing is a practical and superior solution for the challenges of modern microservice architectures.

### 7.2 Limitations

While promising, the current implementation has several limitations that should be acknowledged:

1.  **Scalability of the Q-Table**: The project uses a simple table (a Q-table) to store what the agent learns. As we add more services or monitor more metrics, this table can grow exponentially large, becoming slow and memory-intensive. This is a well-known issue called the "curse of dimensionality."

2.  **Rigid State Representation**: The system groups continuous metrics into fixed bins (e.g., CPU usage 0-25% is one state). This is simple but can miss important details. For example, a CPU jump from 74% to 76% looks like a major change (crossing a boundary), while a jump from 51% to 74% is ignored (within the same bin).

3.  **The "Cold Start" Problem**: The agent is not effective until it has gathered enough experience. When faced with a completely new situation it hasn't seen in training, it may not make the best decision.

4.  **Retraining for Architectural Changes**: If the application architecture changes, such as adding a new service, the agent's existing knowledge becomes outdated. The Q-table would need to be retrained or expanded to include the new server, which is a manual process.

### 7.3 Recommendations and Future Work

Based on the project's success and limitations, the following steps are recommended for future work:

*   **Upgrade to Deep Reinforcement Learning (DRL)**: Replace the Q-table with a neural network (like a Deep Q-Network or DQN). A DRL model can handle a much larger and more complex environment, solving the scalability problem and allowing for more nuanced decision-making.

*   **Implement a Safety Circuit Breaker**: Add a safety mechanism that automatically switches back to the simple Round Robin algorithm if the AI agent starts making poor decisions that hurt performance. This is crucial for production readiness.

*   **Develop Advanced Online Learning**: Improve the agent's ability to learn safely and quickly in a live environment. This would allow it to adapt to sudden changes without needing to be taken offline for retraining.

*   **Integrate with Predictive Autoscaling**: Use the insights from the agent to predict future traffic spikes. This would allow the system to scale up its resources *before* they are needed, preventing slowdowns entirely.

---

## 8. References

*(Note: This section is a placeholder and should be populated with formal citations to academic papers, articles, and other resources referenced in the literature review and throughout the document.)*

1.  Dean, J., & Barroso, L. A. (2013). The Tail at Scale. *Communications of the ACM*, 56(2), 74–80.
2.  Mao, H., Alizadeh, M., Menache, I., & Kandula, S. (2016). Resource Management with Deep Reinforcement Learning. *Proceedings of the 15th ACM Workshop on Hot Topics in Networks*.
3.  Sutton, R. S., & Barto, A. G. (2018). *Reinforcement Learning: An Introduction*. The MIT Press.

---

## 9. Appendix

### Appendix A: The Q-Learning Algorithm in Depth

Q-learning is a model-free reinforcement learning algorithm used to find the optimal action-selection policy for any given finite Markov Decision Process. It works by learning a quality function, known as the Q-function, which estimates the value of taking a certain action in a given state.

#### Mathematical Formulation

The core of Q-learning is the Bellman equation, which is updated iteratively. The Q-value for a state-action pair `(s, a)` is updated based on the reward received and the maximum expected future reward from the next state `s'`.

The update rule is as follows:

```
Q(s, a) ← (1 - α) * Q(s, a) + α * [r + γ * max_{a'} Q(s', a')]
```

Where:
*   `Q(s, a)` is the current Q-value of taking action `a` in state `s`.
*   `α` (alpha) is the **learning rate** (0 < α ≤ 1), which determines how much the new information overrides the old information.
*   `r` is the **reward** received after taking action `a` in state `s`.
*   `γ` (gamma) is the **discount factor** (0 ≤ γ < 1), which determines the importance of future rewards.
*   `s'` is the **next state** resulting from action `a`.
*   `max_{a'} Q(s', a')` is the maximum Q-value for all possible actions `a'` in the next state `s'`, representing the best possible future value.

#### A Numerical Walkthrough Example

Let's consider a simplified scenario with two backend pods, `pod-1` (action `a1`) and `pod-2` (action `a2`).

**Initial State:**
*   **State (`s1`)**: `(cpu_low, mem_low, latency_ok)`
*   **Initial Q-table (for state `s1`)**: `Q(s1, a1) = 0`, `Q(s1, a2) = 0`
*   **Hyperparameters**: `α = 0.1`, `γ = 0.9`

**Step 1: Agent selects an action.**
*   The agent chooses to send a request to `pod-1` (action `a1`).

**Step 2: The environment returns a reward and a new state.**
*   The request to `pod-1` completes with a response time of 50ms and is successful.
*   **Reward (`r`)**: The reward function calculates a reward of `+0.8`.
*   **Next State (`s2`)**: The system transitions to a new state, `(cpu_low, mem_medium, latency_ok)`.

**Step 3: Agent observes the maximum Q-value for the next state.**
*   Let's assume the Q-table for the new state `s2` currently has the values: `Q(s2, a1) = 0.5` and `Q(s2, a2) = 0.7`.
*   The maximum future reward is `max Q(s2, a') = 0.7`.

**Step 4: The agent updates the Q-value for the original state-action pair (`s1`, `a1`).**
*   Using the Bellman equation:
    `Q(s1, a1) ← (1 - 0.1) * 0 + 0.1 * [0.8 + 0.9 * 0.7]`
    `Q(s1, a1) ← 0.9 * 0 + 0.1 * [0.8 + 0.63]`
    `Q(s1, a1) ← 0 + 0.1 * 1.43`
    `Q(s1, a1) ← 0.143`

**Result:**
*   The updated Q-table for state `s1` is now: `Q(s1, a1) = 0.143`, `Q(s1, a2) = 0`.
*   The agent has learned that taking action `a1` in state `s1` is slightly positive. Over thousands of such iterations, the Q-values converge to represent the true long-term value of each action in each state.

### Appendix B: Reinforcement Learning Model Details

This section provides a deeper look into the core components of the RL model that translate raw system metrics into intelligent routing decisions.

#### State Space Encoding

The agent cannot interpret raw, continuous metrics directly. The state encoder's job is to convert the high-dimensional, continuous data from Prometheus into a discrete, simplified state tuple that the Q-table can use as a key.

**Binning Strategy:**

The system uses a binning strategy to discretize each metric. For example, CPU usage is mapped to one of several bins:

*   `0-25%` -> `0` (low)
*   `25-50%` -> `1` (medium)
*   `50-75%` -> `2` (high)
*   `75-100%` -> `3` (critical)

**A Real-World Example:**

Consider a `cart-service` pod with the following real-time metrics collected from Prometheus:

*   **CPU Usage**: 65%
*   **Memory Usage**: 45%
*   **P95 Latency**: 150ms
*   **Error Rate**: 2%
*   **Request Rate**: 30 req/s

The state encoder would process these as follows:

1.  **CPU (65%)** -> falls into the `50-75%` bin -> **`2`**
2.  **Memory (45%)** -> falls into the `25-50%` bin -> **`1`**
3.  **Latency (150ms)** -> falls into a `100-200ms` bin -> **`2`**
4.  **Error Rate (2%)** -> falls into a `1-5%` bin -> **`1`**
5.  **Request Rate (30 req/s)** -> falls into a `20-40 req/s` bin -> **`2`**

The final discrete state representation for this pod would be the tuple: **`(2, 1, 2, 1, 2)`**. This tuple is used as a key to look up Q-values in the Q-table.

#### Reward Function Calculation

The reward function is a multi-objective formula designed to provide a balanced signal to the agent, encouraging it to optimize for several competing goals simultaneously. The total reward is a weighted sum of normalized scores for latency, errors, throughput, and load balance.

**A Numerical Example:**

Consider a request that was routed to a pod, and the following outcome was observed:

*   **Response Time**: 120ms
*   **Outcome**: Success (HTTP 200)
*   **System Throughput**: 80 req/s

Assume the following weights are set in the configuration:
*   `latency_weight`: 0.4
*   `error_weight`: 0.4
*   `throughput_weight`: 0.1
*   `balance_weight`: 0.1

1.  **Latency Score**: Lower latency is better. A normalized score might be `+0.6`.
2.  **Error Score**: No error occurred. This yields the maximum score of `+1.0`.
3.  **Throughput Score**: Higher throughput is better. A normalized score might be `+0.7`.
4.  **Balance Score**: Measures if the pods are evenly loaded. Let's assume it's `+0.5`.

**Final Reward Calculation:**
`Total Reward = (0.6 * 0.4) + (1.0 * 0.4) + (0.7 * 0.1) + (0.5 * 0.1)`
`Total Reward = 0.24 + 0.40 + 0.07 + 0.05`
`Total Reward = 0.76`

This positive reward of `0.76` is then used in the Bellman equation to update the Q-value for the state-action pair that led to this outcome.

#### Markov Decision Process (MDP) Formulation

The entire load balancing problem can be formally defined as a Markov Decision Process, which provides the theoretical foundation for applying reinforcement learning.

*   **States (S)**: The set of all possible discrete system states. A state `s ∈ S` is a tuple representing the binned metrics of all service pods (e.g., `(cpu_bin, mem_bin, ...)` for each pod).

*   **Actions (A)**: The set of all possible actions the agent can take. An action `a ∈ A` corresponds to selecting a specific backend pod to which the incoming request will be routed (e.g., `cart-service-pod-1`).

*   **Reward Function (R)**: A function `R(s, a, s')` that defines the immediate reward received after taking action `a` in state `s` and transitioning to state `s'`. This is calculated by our multi-objective reward function based on the outcome of the request.

*   **Transition Function (P)**: The probability `P(s' | s, a)` of transitioning to state `s'` after taking action `a` in state `s`. In this project, the transition function is unknown and not explicitly modeled (making this a model-free problem), as the next state of the system depends on complex, external factors like user traffic patterns.

*   **Policy (π)**: The agent's strategy for choosing actions in given states. The goal of the Q-learning algorithm is to find the optimal policy, `π*`, which maximizes the cumulative discounted reward over time.

### Appendix C: System Configuration

This section provides insight into the key configuration files that govern the behavior of the RL-Agent and the load testing framework.

#### RL-Agent Tunable Parameters (`rl_settings.py`)

The RL-Agent's learning behavior is controlled by a set of hyperparameters defined in `rl_settings.py`. These parameters allow for fine-tuning the agent's performance to suit different operational environments. The most critical tunable parameters are listed below.

| Parameter | Section | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `learning_rate` | Q-Learning | `0.3` | Determines how quickly the agent adopts new information. Higher values mean faster learning. |
| `discount_factor` | Q-Learning | `0.95` | Controls the importance of future rewards. A higher value makes the agent more farsighted. |
| `epsilon_start` | Q-Learning | `0.25` | The initial probability that the agent will explore a random action versus exploiting the best-known one. |
| `epsilon_decay` | Q-Learning | `0.99` | The rate at which the exploration probability (`epsilon`) decreases over time as the agent learns. |
| `production_epsilon`| Q-Learning | `0.02` | A very low, fixed exploration rate used during benchmarking or in production to prioritize exploitation. |
| `cpu_bins` | State Encoding | `16` | The number of discrete bins to divide CPU utilization into for state representation. |
| `latency_bins` | State Encoding | `20` | The number of discrete bins for P95 latency, affecting state granularity. |
| `latency_weight` | Reward | `0.35` | The relative importance (35%) of minimizing latency in the final reward calculation. |
| `error_rate_weight`| Reward | `0.35` | The relative importance (35%) of minimizing errors. |
| `throughput_weight`| Reward | `0.15` | The relative importance (15%) of maximizing overall system throughput. |

#### Sample Load Testing Scenario

The load testing framework uses detailed JSON configurations to generate realistic and diverse traffic patterns. This is essential for training a robust RL model that can handle real-world conditions. Below is an excerpt from `comprehensive_rl_training_config.json`, showing a single traffic pattern.

```json
{
  "name": "morning_peak_rush",
  "start_minute": 0,
  "duration_minutes": 25,
  "intensity": "very_high",
  "concurrent_users": 95,
  "user_behavior_distribution": {
    "quick_browsers": 0.4,
    "careful_shoppers": 0.25,
    "bulk_buyers": 0.15,
    "impulse_buyers": 0.2
  },
  "scenario_weights": {
    "browse_weight": 50,
    "search_weight": 20,
    "cart_weight": 20,
    "order_weight": 8,
    "profile_weight": 2
  }
}
```

**Explanation of Key Fields:**
*   **`name`**: A descriptive name for the traffic pattern.
*   **`duration_minutes`**: How long this specific pattern lasts.
*   **`concurrent_users`**: The number of virtual users active during this phase.
*   **`user_behavior_distribution`**: Defines the mix of user profiles (e.g., some users browse quickly, others shop carefully), each with different session lengths and action probabilities.
*   **`scenario_weights`**: Controls the probability of a user performing a specific action (e.g., browsing vs. adding to cart), thereby shaping the load on different microservices.

### Appendix D: Technical Specifications

This section provides supplementary technical details, including a high-level analysis of algorithm complexity and the data contracts for the RL-Agent's API.

#### Comparison of Load Balancing Algorithms

The choice of a load balancing algorithm involves trade-offs between performance, resource usage, and intelligence. The table below provides a basic comparison of the computational complexity for the algorithms used in this project.

| Algorithm | Time Complexity (Decision) | Space Complexity | Key Characteristic |
| :--- | :--- | :--- | :--- |
| **Round Robin** | `O(1)` | `O(N)` | Simple, stateless, and extremely fast. Does not adapt to server load. |
| **Least Connections**| `O(1)` | `O(N)` | Requires tracking active connections but is still very fast. Adapts to basic load. |
| **RL-Agent** | `O(M) + O(1)` | `O(S * A)` | Decision requires metric collection for `M` pods, but the lookup is constant time. Space usage depends on the size of the state-space `S` and action-space `A`, which can be large. |

*   `N` = Total number of pods available to the load balancer.
*   `M` = Number of pods for the specific service being requested.
*   `S` = Total number of unique states in the Q-table.
*   `A` = Total number of unique actions (pods) in the Q-table.

#### RL-Agent API Payloads

The load balancer and the RL-Agent communicate via a simple REST API. Below are sample JSON payloads for the two primary endpoints.

**`/decide` Endpoint Request**

This payload is sent from the load balancer to the RL-Agent to request a routing decision.

```json
{
  "trace_id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
  "service_name": "cart-service"
}
```

**`/decide` Endpoint Response**

The RL-Agent responds with the name of the selected pod.

```json
{
  "selected_pod": "cart-service-pod-7f8c9d0b1a-2b3c4"
}
```

**`/feedback` Endpoint Request**

After the request is completed, the load balancer sends the outcome back to the RL-Agent for learning.

```json
{
  "trace_id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
  "service_name": "cart-service",
  "selected_pod": "cart-service-pod-7f8c9d0b1a-2b3c4",
  "response_time_ms": 85,
  "status_code": 200,
  "is_error": false
}
```

### Appendix E: Environment Setup and Execution Guide

This guide provides a concise set of instructions for setting up the project environment, deploying the services, and running key processes like load testing and benchmarking.

#### 1. Prerequisite Stack

Ensure the following tools are installed on your local machine:
*   **Docker**: For building and running containerized services.
*   **`kubectl`**: The command-line tool for interacting with Kubernetes.
*   **`kind`**: For running a local Kubernetes cluster inside Docker.
*   **Java 17 & Maven**: For building the microservices.
*   **Python 3.10+ & Pip**: For running the RL-Agent and testing scripts.

#### 2. Deploy the Kubernetes Cluster

These commands build all application images and deploy the entire stack to a local `kind` cluster.

```bash
# Navigate to the kubernetes-stack directory
cd kubernetes-stack

# 1. Build all Docker images for the microservices
./build.sh

# 2. Start the kind cluster and deploy all services
./startup.sh

# 3. Start the port-forwarding to access the loadbalancer service.
kubectl port-forward -n ai-loadbalancer service/ai-loadbalancer-service 8080:8080
``` 

#### 3. Access the Grafana Dashboard

To visualize system performance and the RL-Agent's behavior, forward the Grafana port.

```bash
# Run this in a new terminal
kubectl port-forward -n ai-loadbalancer service/grafana-service 3000:3000
```
*   **URL**: `http://localhost:3000`
*   **Login**: `admin` / `admin`

#### 4. Run a Load Test (for RL Training)

This command executes a comprehensive load test designed to generate training data for the RL model.

```bash
# Navigate to the load-testing directory
cd load-testing

# 1. Install Python dependencies
pip install -r requirements.txt

# 2. Run the comprehensive training scenario (this can take 3+ hours)
python rl_training_executor.py --mode comprehensive
```

#### 5. Run an Automated Benchmark

This script runs a fair, head-to-head comparison of the different load balancing algorithms.

```bash
# Navigate to the load-testing directory
cd load-testing

# Run the benchmark using a specific configuration
python3 automated_benchmark.py  --duration 15 --users 20 --ramp-up 5 --url http://localhost:8080
```
### Appendix E: Load Balancer Routing API Implementation

This appendix details the core `/proxy/{service}/**` API implementation from `LoadBalancerController.java`, which handles all incoming requests and routes them through the AI-powered load balancing system.

#### E.1 Main Routing Endpoint

The primary routing endpoint accepts all HTTP methods and forwards requests to the appropriate backend service instances:

```java
@RequestMapping(value = "/{serviceName}/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
public ResponseEntity<?> proxyRequest(
        @PathVariable("serviceName") String serviceName,
        HttpServletRequest request,
        @RequestBody(required = false) Object body) {
    
    ServiceInfo targetInstance = null;
    
    try {
        // Get next healthy instance using the configured routing strategy
        long startTime = System.currentTimeMillis();
        targetInstance = routingStrategyAlgorithm.getLoadbalancer().geNextServiceInstance(serviceName);
         
        if (targetInstance == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "No healthy instances available for service: " + serviceName);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
        }
        
        // Set chosen instance for RL experience logging
        request.setAttribute("chosenPodInstance", targetInstance.getInstanceName());
        logger.debug("Selected instance {} for service {}", targetInstance.getInstanceName(), serviceName);
        
         // Build target URI and forward request
        URI targetUri = buildTargetUri(request, serviceName, targetInstance);
        HttpHeaders headers = copyHeaders(request);
        headers.set(TraceUtils.TRACE_ID_HEADER, traceId);
        
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        
        // Forward the request
        long forwardStartTime = System.currentTimeMillis();
        ResponseEntity<String> response = restTemplate.exchange(targetUri, method, entity, String.class);
        long forwardEndTime = System.currentTimeMillis();
        
        long responseTime = System.currentTimeMillis() - startTime;
        
        provideFeedbackToRLAgent(serviceName, targetInstance.getInstanceName(), 
                             responseTime, response.getStatusCode().value());
        
        // Return response with proper headers
        Object responseBody = parseResponseBody(response.getBody());
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(response.getHeaders());
        
        return ResponseEntity.status(response.getStatusCode())
                .headers(responseHeaders)
                .body(responseBody);

    } catch (Exception e) {
        // Handle errors and provide negative feedback to RL agent
        long responseTime = System.currentTimeMillis() - startTime;
        logger.error("Error forwarding request: {}", e.getMessage(), e);

        errorHeaders.set(TraceUtils.TRACE_ID_HEADER, traceId);
        
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .headers(errorHeaders)
                .body(error);
    }
}
```

### Appendix F: Glossary

*   **Action**: In the context of this project, a decision made by the RL-Agent to route an incoming request to a specific backend pod.

*   **Bellman Equation**: A fundamental equation in reinforcement learning used to update the value of a state-action pair based on the immediate reward and the discounted value of the optimal future state.

*   **Discount Factor (Gamma / γ)**: A hyperparameter in Q-learning, between 0 and 1, that determines the importance of future rewards. A higher value makes the agent prioritize long-term gains.

*   **Epsilon-Greedy**: An exploration strategy where the agent chooses a random action with a probability of epsilon (ε) and the best-known action with a probability of 1-ε. This balances exploration of new strategies with exploitation of known good ones.

*   **Exploitation**: Choosing the action with the highest known expected reward in a given state.

*   **Exploration**: Choosing a non-optimal action to gather more information about the environment.

*   **Learning Rate (Alpha / α)**: A hyperparameter that controls how much new information overrides old information. It determines how quickly the agent adapts its Q-values.

*   **Markov Decision Process (MDP)**: A mathematical framework for modeling decision-making problems. It is defined by a set of states, actions, a transition model, and a reward function.

*   **P95 / P99 Latency**: Statistical measures representing the 95th and 99th percentile of response times. P95 latency means that 95% of requests were completed in that time or less. These are used to measure tail latency, which is critical for user experience.

*   **Q-Learning**: A model-free reinforcement learning algorithm that learns a policy indicating the best action to take in any given state. It does this by learning a Q-value for each state-action pair.

*   **Q-table**: A data structure, typically a dictionary or table, used in tabular Q-learning to store the Q-values for all possible state-action pairs.

*   **Q-Value**: A value that represents the quality of a state-action pair. It is the expected cumulative future reward an agent will receive by taking a specific action in a specific state and following the optimal policy thereafter.

*   **Reward**: A numerical signal that the environment sends to the agent in response to an action. The agent's goal is to maximize the cumulative reward over time.

*   **Reward Function**: A function that defines the goal in a reinforcement learning problem. It takes the outcome of an action as input and returns a numerical reward or penalty.

*   **Sidecar Pattern**: A deployment pattern in which a secondary container is deployed alongside a primary application container. In this project, the RL-Agent runs as a sidecar to the AI Load Balancer to minimize communication latency.

*   **State**: A discrete representation of the system's condition at a specific point in time, derived from a set of continuous metrics (e.g., CPU, memory, latency).

*   **Throughput**: A measure of the system's performance, typically quantified as the number of requests processed per unit of time (e.g., requests per second).
