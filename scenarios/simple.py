import requests


API_URL = "http://localhost:8080"


def spawn_collector(x, y):
    r = requests.post(f"{API_URL}/collector/spawn", json={
        "location": {
            "x": x,
            "y": y
        },
        "speed": 20,
        "capacity": 30
    })
    assert r.status_code == 201


def spawn_waste_source(x, y):
    r = requests.post(f"{API_URL}/source/spawn", json={
        "location": {
            "x": x,
            "y": y
        },
        "capacity": 30
    })
    assert r.status_code == 201


def spawn_waste_sink(x, y):
    r = requests.post(f"{API_URL}/sink/spawn", json={
        "location": {
            "x": x,
            "y": y
        },
        "efficiency": 100,
        "storageCapacity": 100
    })
    assert r.status_code == 201


spawn_waste_source(0, 0)
spawn_collector(10, 10)
spawn_waste_sink(20, 20)
