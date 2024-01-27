# Example file showing a basic pygame "game loop"
import pygame
from dataclasses import dataclass
import requests


def load_image(path: str, scaling_factor: float):
    img = pygame.image.load(path)
    width, height = img.get_rect().size
    return pygame.transform.scale(img, (width / scaling_factor, height / scaling_factor))

FPS = 60.0
WINDOW_WIDTH = 1280
WINDOW_HEIGHT = 720
MAX_Y = 25
MAX_X = 25
UNIT_SIZE = min(WINDOW_WIDTH / MAX_X, WINDOW_HEIGHT / MAX_Y)
SMOOTHING_FACTOR = 0.1
GRASS_GREEN = (65,152,10)
waste_source_img = load_image("waste_source.png", 7.0)
garbage_collector_img = load_image("garbage_collector.png", 5.0)
waste_sink_img = load_image("waste_sink.jpg", 10.0)


@dataclass
class Point:
    x: float
    y: float


class Object:
    def __init__(self, image, position: Point):
        self.image = image
        self.position = position

    def draw(self, screen):
        screen.blit(self.image, (self.position.x * UNIT_SIZE, self.position.y * UNIT_SIZE))


class WasteSource(Object):
    def __init__(self, image, position: Point, garbage: int, capacity: int):
        super().__init__(image, position)
        self.garbage = garbage
        self.capacity = capacity

    def draw(self, screen):
        super().draw(screen)
        font = pygame.font.Font(None, 36)
        text = f"{self.garbage}/{self.capacity}"
        text_surface = font.render(text, True, (255, 0, 0))
        screen.blit(text_surface, (self.position.x * UNIT_SIZE, self.position.y * UNIT_SIZE))


class WasteSink(Object):
    def __init__(self, image, position: Point, totalReserved: int, capacity: int):
        super().__init__(image, position)
        self.totalReserved = totalReserved 
        self.capacity = capacity

    def draw(self, screen):
        super().draw(screen)
        font = pygame.font.Font(None, 36)
        text = f"{self.totalReserved}/{self.capacity}"
        text_surface = font.render(text, True, (255, 0, 0))
        screen.blit(text_surface, (self.position.x * UNIT_SIZE, self.position.y * UNIT_SIZE))


class GarbageCollector(Object):
    def __init__(self, image, position: Point, garbage: int, capacity: int):
        super().__init__(image, position)
        self.garbage = garbage
        self.capacity = capacity

    def draw(self, screen):
        super().draw(screen)
        font = pygame.font.Font(None, 36)
        text = f"{self.garbage}/{self.capacity}"
        text_surface = font.render(text, True, (255, 0, 0))
        screen.blit(text_surface, (self.position.x * UNIT_SIZE, self.position.y * UNIT_SIZE))


class ObjectFactory:
    def create_waste_source(self, position: Point, garbage: int, capacity: int):
        return WasteSource(waste_source_img, position, garbage, capacity)

    def create_waste_sink(self, position: Point, totalReserved: int, capacity: int):
        return WasteSink(waste_sink_img, position, totalReserved, capacity)

    def create_garbage_collector(self, position: Point, garbage: int, capacity: int):
        return GarbageCollector(garbage_collector_img, position, garbage, capacity)

object_factory = ObjectFactory()


# pygame setup
pygame.init()
screen = pygame.display.set_mode((WINDOW_WIDTH, WINDOW_HEIGHT))
clock = pygame.time.Clock()
running = True

elapsed_time = 0
state = None
while running:
    elapsed_time += 1 / FPS

    if elapsed_time > 1:
        elapsed_time = 0
        try:
            r = requests.get("http://localhost:8080/status")
            state = r.json()
        except Exception:
            pass

    # poll for events
    # pygame.QUIT event means the user clicked X to close your window
    for event in pygame.event.get():
        if event.type == pygame.QUIT:
            running = False

    # fill the screen with a color to wipe away anything from last frame
    screen.fill(GRASS_GREEN)

    # RENDER YOUR GAME HERE
    if state:
        for collector_state in state["collectors"]:
            object_factory.create_garbage_collector(
                Point(*collector_state["location"]),
                collector_state["garbageLevel"],
                collector_state["capacity"],
            ).draw(screen)
        for source in state["sources"]:
            object_factory.create_waste_source(
                Point(*source["location"]),
                source["garbageLevel"],
                source["capacity"]
            ).draw(screen)
        for sink in state["sinks"]:
            garbage_level = 0
            if sink["garbagePackets"]:
                garbage_level = sum(map(lambda it: it["totalMass"], sink["garbagePackets"]))
            object_factory.create_waste_sink(
                Point(*sink["location"]),
                garbage_level,
                sink["capacity"]
            ).draw(screen)

    # flip() the display to put your work on screen
    pygame.display.flip()

    clock.tick(FPS)  # limits FPS to 60

pygame.quit()