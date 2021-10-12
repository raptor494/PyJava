from typing import NamedTuple

class Point2d(NamedTuple):
    x: int
    y: int

class Point3d(NamedTuple):
    x: int
    y: int
    z: int

def make_point_3d(arg) -> Point3d:
    match (arg):
        case Point3d():
            return arg
        case (int(x), int(y), int(z)):
            return Point3d(x, y, z)
        case (int(x), int(y)):
            return Point3d(x, y, 0)
        case _:
            raise ValueError(f"Cannot convert {arg!r} to Point3d")
