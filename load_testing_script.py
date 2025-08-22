import requests
import concurrent.futures
import time

URL = "http://localhost:8080/proxy/order-service/api/process"
TOTAL_REQUESTS = 10000    # Total number of requests
MAX_WORKERS = 20         # Number of concurrent threads

def send_request(n):
    try:
        response = requests.get(URL, timeout=5)
        print(f"[{n}] Status: {response.status_code}")
        return response.status_code
    except Exception as e:
        print(f"[{n}] Error: {e}")
        return None

if __name__ == "__main__":
    start = time.time()
    with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        futures = [executor.submit(send_request, i) for i in range(TOTAL_REQUESTS)]
        results = [f.result() for f in concurrent.futures.as_completed(futures)]
    end = time.time()

    success = sum(1 for r in results if r == 200)
    failed = len(results) - success
    print("\n--- Load Test Summary ---")
    print(f"Total Requests Sent: {TOTAL_REQUESTS}")
    print(f"Successful: {success}")
    print(f"Failed: {failed}")
    print(f"Time Taken: {end - start:.2f} seconds")