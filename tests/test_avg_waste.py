import subprocess
import time
import os
import signal


def kill(p: subprocess.Popen):
    os.killpg(os.getpgid(p.pid), signal.SIGTERM)

scenario = "simple"
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
subprocess.run(f"python3 scenarios/{scenario}.py", shell=True)

time.sleep(10)
kill(sim)
kill(gui)