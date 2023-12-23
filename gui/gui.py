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
waste_source_img = load_image("waste_source.jpg", 15.0)
garbage_collector_img = load_image("garbage_collector.jpg", 15.0)
waste_sink_img = load_image("waste_sink.jpg", 15.0)


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

class ObjectFactory:
    def create_waste_source(self, position: Point):
        return Object(waste_source_img, position)

    def create_waste_sink(self, position: Point):
        return Object(waste_sink_img, position)

    def create_garbage_collector(self, position: Point):
        return Object(garbage_collector_img, position)

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
            object_factory.create_garbage_collector(Point(*collector_state["location"])).draw(screen)
        for source in state["sources"]:
            object_factory.create_waste_source(Point(*source["location"])).draw(screen)
        for sink in state["sinks"]:
            object_factory.create_waste_sink(Point(*sink["location"])).draw(screen)

    # flip() the display to put your work on screen
    pygame.display.flip()

    clock.tick(FPS)  # limits FPS to 60

pygame.quit()