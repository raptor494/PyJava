#!/python

from pyparser.parser import PyJavaParser
from pyparser.tokenize import tokenize
import io

code = RB"""

x, y = 5, 7;

del y;

for x, y in zip([1,2,3], [4,5,6]) {
    print("(x = {0}, y = {1})".format(x, y), end="");
}

l1 = lambda (x: int) { return 2 * x; };
l2 = lambda y -> int {
    if y < 20 and y != 0 {
        return 2 * y;
    } else {
        return 3 // y;
    }
};

z = 3;

x, *y = [1,2,3];

class Point {
    def __init__(self, x: int, y: int) {
        self.x = x;
        self.y = y;
    }
    @staticmethod
    def create(x, y) {
        return Point(x,y);
    }
}

with open("test.txt", 'r') as file, open("test2.txt", 'w') as file2 {
    file2.writelines(file.readlines());
}

foo(z := 3 and 2);

from abc import ABC, abstractmethod;

class Animal(ABC) {
    @abstractmethod
    def make_noise(self) {}
}

#{
  multi-line comment
#}

dog = class(Animal)() {
    def make_noise(self) {
        print("woof!");
    }
};

"""




p = PyJavaParser(tokenize(io.BytesIO(code).readline))

stmts = p.parse_file()
for stmt in stmts:
    print(stmt)