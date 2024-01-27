import subprocess
import time
import os
import signal
import requests
import matplotlib.pyplot as plt


def kill(p: subprocess.Popen):
    os.killpg(os.getpgid(p.pid), signal.SIGTERM)

timesteps = 60
scenario = "simple"
subprocess_params = {
    "shell": True
    # "stdout": subprocess.PIPE,
    # "stderr": subprocess.PIPE
}

print("Starting Akka simulation")
# sim = subprocess.Popen("sbt run", **subprocess_params)

time.sleep(10)

print("Starting Python GUI")
gui = subprocess.Popen("cd gui && source venv/bin/activate && python3 gui.py", **subprocess_params)

print(f"Running scenario {scenario}")
subprocess.run(f"python3 scenarios/{scenario}.py", shell=True)

states = []
for i in range(timesteps):
    r = requests.get("http://localhost:8080/status")
    state = r.json()
    states.append(state)
    time.sleep(1)

avg_collection_times = []
avg_source_garbage_levels = []
avg_collector_garbage_levels = []
avg_sink_total_reserved = []

for state in states:
    sources = state["sources"]
    sinks = state["sinks"]
    collectors = state["collectors"]

    avg_col_time = sum(map(lambda it: sum(it["collectionTimes"]) / len(it["collectionTimes"]), sources)) / len(sources)
    avg_src_grb_lvl = sum(map(lambda it: it["garbageLevel"], sources)) / len(sources)
    avg_col_grb_lvl = sum(map(lambda it: it["garbageLevel"], collectors)) / len(collectors)
    avg_sink_tr = sum(map(lambda it: it["totalReserved"], sinks)) / len(sinks)
    
    avg_collection_times.append(avg_col_time)
    avg_source_garbage_levels.append(avg_src_grb_lvl)
    avg_collector_garbage_levels.append(avg_col_grb_lvl)
    avg_sink_total_reserved.append(avg_sink_tr)

x = [i+1 for i in range(timesteps)]
plt.plot(x, avg_source_garbage_levels, color="r", label="Average source garbage level")
plt.plot(x, avg_collector_garbage_levels, color="b", label="Average collector garbage level")
plt.plot(x, avg_sink_total_reserved, color="g", label="Average sink total reserved")
plt.plot(x, avg_collection_times, color = "o", label="Avergae collection time")
plt.ylabel("Unit")
plt.xlabel("Timestep")
plt.legend()
plt.savefig("diagrams/test_avg.png")

kill(sim)
kill(gui)
