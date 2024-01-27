import subprocess
import time
import os
import signal
import requests
import matplotlib.pyplot as plt
import argparse


def kill(p: subprocess.Popen):
    os.killpg(os.getpgid(p.pid), signal.SIGTERM)


parser = argparse.ArgumentParser(prog="tester")
parser.add_argument("scenario", type=str)
parser.add_argument("timesteps", type=int)
parser.add_argument("--collectors", type=int, required=False, default=2)
args = parser.parse_args()

timesteps = args.timesteps
scenario = args.scenario
subprocess_params = {
    "shell": True,
    "stdout": subprocess.PIPE,
    "stderr": subprocess.PIPE
}

print("Starting Akka simulation")
sim = subprocess.Popen("sbt run", **subprocess_params)

time.sleep(10)

print("Starting Python GUI")
gui = subprocess.Popen("cd gui && source venv/bin/activate && python3 gui.py", **subprocess_params)

print(f"Running scenario {scenario}")
if args.scenario == "complex":
    subprocess.run(f"python3 scenarios/{scenario}.py {args.collectors}", shell=True)
else:
    subprocess.run(f"python3 scenarios/{scenario}.py", shell=True)

states = []
for i in range(timesteps):
    r = requests.get("http://localhost:8080/status")
    state = r.json()
    states.append(state)
    time.sleep(1)

avg_source_garbage_levels = []
avg_collector_garbage_levels = []
avg_sink_total_reserved = []

max_source_garbage_levels = []
max_collector_garbage_levels = []
max_sink_total_reserved = []

for state in states:
    sources = state["sources"]
    sinks = state["sinks"]
    collectors = state["collectors"]

    avg_src_grb_lvl = sum(map(lambda it: it["garbageLevel"], sources)) / len(sources)
    avg_col_grb_lvl = sum(map(lambda it: it["garbageLevel"], collectors)) / len(collectors)
    def mapper(it):
        if not it["garbagePackets"]:
            return 0
        return sum(map(lambda it2: it2["totalMass"], it["garbagePackets"]))
    avg_sink_tr = sum(map(mapper,sinks)) / len(sinks)

    avg_source_garbage_levels.append(avg_src_grb_lvl)
    avg_collector_garbage_levels.append(avg_col_grb_lvl)
    avg_sink_total_reserved.append(avg_sink_tr)

    max_src_grb_lvl = max(map(lambda it: it["garbageLevel"], sources))
    max_col_grb_lvl = max(map(lambda it: it["garbageLevel"], collectors))
    max_sink_tr = max(map(mapper,sinks))

    max_source_garbage_levels.append(max_src_grb_lvl)
    max_collector_garbage_levels.append(max_col_grb_lvl)
    max_sink_total_reserved.append(max_sink_tr)

x = [i+1 for i in range(timesteps)]

if scenario == "simple":
    plt.title(f"Wartości średnie dla scenariusza prostego.")
elif scenario == "all2s":
    plt.title(f"Wartości średnie dla scenariusza wieloaktorowego.")
else:
    plt.title(f"Wartości średnie dla scenariusza rzeczywistego.")

plt.plot(x, avg_source_garbage_levels, color="r", label="WasteSource")
plt.plot(x, avg_collector_garbage_levels, color="b", label="GarbageCollector")
plt.plot(x, avg_sink_total_reserved, color="g", label="WasteSink")
plt.ylabel("średnia")
plt.xlabel("czas [s]")
plt.legend()
if args.scenario == "complex":
    plt.savefig(f"diagrams/test_avg_{scenario}_c{args.collectors}.png")
else:
    plt.savefig(f"diagrams/test_avg_{scenario}.png")

plt.clf()

if scenario == "simple":
    plt.title(f"Wartości maksymalne dla scenariusza prostego.")
elif scenario == "all2s":
    plt.title(f"Wartości maksymalne dla scenariusza wieloaktorowego.")
else:
    plt.title(f"Wartości maksymalne dla scenariusza rzeczywistego.")

plt.plot(x, max_source_garbage_levels, color="r", label="WasteSource")
plt.plot(x, max_collector_garbage_levels, color="b", label="GarbageCollector")
plt.plot(x, max_sink_total_reserved, color="g", label="WasteSink")
plt.ylabel("maksimum")
plt.xlabel("czas [s]")
plt.legend()
if args.scenario == "complex":
    plt.savefig(f"diagrams/test_max_{scenario}_c{args.collectors}.png")
else:
    plt.savefig(f"diagrams/test_max_{scenario}.png")

kill(sim)
kill(gui)
