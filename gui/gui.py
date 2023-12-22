# Example file showing a basic pygame "game loop"
import pygame
from dataclasses import dataclass


def load_image(path: str, scaling_factor: float):
    img = pygame.image.load(path)
    width, height = img.get_rect().size
    return pygame.transform.scale(img, (width / scaling_factor, height / scaling_factor))

FPS = 60.0
UNIT_SIZE = 60
SMOOTHING_FACTOR = 0.1
GRASS_GREEN = (65,152,10)
waste_source_img = load_image("waste_source.jpg", 15.0)
garbage_collector_img = load_image("garbage_collector.jpg", 15.0)
waste_sink_img = load_image("waste_sink.jpg", 15.0)


def lerp(a, b, t):
    return a + t * (b - a)

@dataclass
class Point:
    x: float
    y: float


class Object:
    def __init__(self, image, position: Point):
        self.image = image
        self.position = position
        self.target_position = position

    def move(self):
        self.position.x = lerp(self.position.x, self.target_position.x, SMOOTHING_FACTOR)
        self.position.y = lerp(self.position.y, self.target_position.y, SMOOTHING_FACTOR)

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
screen = pygame.display.set_mode((1280, 720))
clock = pygame.time.Clock()
running = True


waste_source = object_factory.create_waste_source(Point(0, 0))
waste_sink = object_factory.create_waste_sink(Point(0, 1))
garbage_collector = object_factory.create_garbage_collector(Point(0, 2))


elapsed_time = 0
while running:
    elapsed_time += 1 / FPS

    if elapsed_time > 1:
        elapsed_time = 0
        garbage_collector.target_position.x += 1

    # poll for events
    # pygame.QUIT event means the user clicked X to close your window
    for event in pygame.event.get():
        if event.type == pygame.QUIT:
            running = False

    # fill the screen with a color to wipe away anything from last frame
    screen.fill(GRASS_GREEN)

    # RENDER YOUR GAME HERE
    garbage_collector.move()
    waste_source.draw(screen)
    waste_sink.draw(screen)
    garbage_collector.draw(screen)

    # flip() the display to put your work on screen
    pygame.display.flip()

    clock.tick(FPS)  # limits FPS to 60

pygame.quit()