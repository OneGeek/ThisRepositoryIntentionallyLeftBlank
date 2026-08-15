"""TamaWatch original 16-color 'gotchi' palette. Single source of truth for
all generated art so every asset stays cohesive. RGBA tuples."""

INK        = (34, 32, 52, 255)     # near-black outline
WHITE      = (255, 255, 255, 255)
GRAY_L     = (203, 219, 214, 255)
GRAY_D     = (105, 106, 106, 255)
LCD_L      = (170, 220, 120, 255)  # light LCD green
LCD_D      = (96, 150, 60, 255)    # dark LCD green
CREAM      = (255, 236, 214, 255)
SKIN       = (255, 204, 170, 255)
PINK_L     = (255, 190, 214, 255)
PINK_D     = (233, 111, 160, 255)
RED        = (223, 62, 62, 255)
BLUE_L     = (140, 200, 240, 255)
BLUE_D     = (70, 130, 200, 255)
YELLOW     = (250, 220, 90, 255)
GREEN      = (120, 200, 130, 255)
PURPLE     = (170, 120, 210, 255)

TRANSPARENT = (0, 0, 0, 0)

ALL = [INK, WHITE, GRAY_L, GRAY_D, LCD_L, LCD_D, CREAM, SKIN,
       PINK_L, PINK_D, RED, BLUE_L, BLUE_D, YELLOW, GREEN, PURPLE]


def darker(c, f=0.7):
    return (int(c[0] * f), int(c[1] * f), int(c[2] * f), c[3])


def lighter(c, f=0.4):
    return (int(c[0] + (255 - c[0]) * f),
            int(c[1] + (255 - c[1]) * f),
            int(c[2] + (255 - c[2]) * f), c[3])
