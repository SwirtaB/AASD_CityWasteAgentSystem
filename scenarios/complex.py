import requests


API_URL = "http://localhost:8080"


def spawn_collector(x, y):
    r = requests.post(f"{API_URL}/collector/spawn", json={
        "location": {
            "x": x,
            "y": y
        },
        "speed": 20,
        "capacity": 100
    })
    assert r.status_code == 201


def spawn_waste_source(x, y):
    r = requests.post(f"{API_URL}/source/spawn", json={
        "location": {
            "x": x,
            "y": y
        },
        "capacity": 20
    })
    assert r.status_code == 201


def spawn_waste_sink(x, y):
    r = requests.post(f"{API_URL}/sink/spawn", json={
        "location": {
            "x": x,
            "y": y
        },
        "efficiency": 100,
        "storageCapacity": 1000
    })
    assert r.status_code == 201


spawn_waste_source(0, 0)
spawn_waste_source(0, 5)
spawn_waste_source(0, 10)
spawn_waste_source(0, 15)
spawn_waste_source(0, 20)
spawn_waste_source(10, 0)
spawn_waste_source(10, 20)
spawn_waste_source(20, 0)
spawn_waste_source(20, 5)
spawn_waste_source(20, 10)
spawn_waste_source(20, 15)
spawn_waste_source(20, 20)
spawn_waste_source(30, 0)
spawn_waste_source(30, 20)
spawn_waste_source(40, 0)
spawn_waste_source(40, 5)
spawn_waste_source(40, 10)
spawn_waste_source(40, 15)
spawn_waste_source(40, 20)
spawn_collector(10, 15)
spawn_collector(30, 15)
spawn_waste_sink(10, 10)
spawn_waste_sink(30, 10)
