# PyJava
### Description
This project is the inverse of my [JavaPy project](https://github.com/raptor4694/JavaPy). It allows you to write Python code using semicolons and braces instead of indentation, like Java.

### Usage
Call the program with `python pyjava.py <filename>` and it will output a file
called the same thing except with a `.py` extension.
The program tries to format the file to be human-readable but may not be quite right in places. Use your own formatter as necessary.
The parser does not *always* check for semantically invalid syntax, such as duplicate/missing variable names, duplicate functions, etc.

### Differences from Normal Java
#### Simple Statements
Non-compound statements must now end with a semicolon.

#### Code Blocks (aka Suites)
A block of code is now enclosed in curly brackets `{` `}`.
You can also do a colon `:` followed by a single statement.

**Examples**:

Normal Python:
```python
class Example:
    def __init__(self, x, y):
        self.x = x
        self.y = y
```
PyJava:
```python
class Example {
    def __init__(self, x, y) {
        self.x = x;
        self.y = y;
    }
}
```
<!--\_______________________________________________________________________
-->

#### Lambdas
A nice pro of not caring about whitespace is that you can now make mutli-line lambdas (anonymous functions).

**Example**:
```python
filter(lambda x {
    if not isinstance(x, str):
        return False;
    if x.isspace():
        return False;
    return True;
}, args)
```

You can also add type annotations to lambdas. Adding type annotations to the parameters requires enclosing the parameters in parenthesis. Adding a return type annotation is as simple as following the parameter list with a `->` and the annotation expression.

**Example**:
```python
lambda (x: int, y: int) -> int {
    if x + y < 10 {
        return 3;
    } else {
        return x - y;
    }
}
```

#### Classes
You can now do anonymous classes.
The syntax is this:

    class <name> [superclass arguments] <arguments> <brace-enclosed class body>

If superclass arguments are provided, the hidden name of the class will try to be similar to the first superclass defined. Otherwise, it will be similar to 'object'.

**Example**:
```python
class Animal(ABC) {
    @abstractmethod
    def speak(self) {}
}

dog = class(Animal)() {
    def speak(self) {
        print("woof!");
    }
};
```

### Notes
1. The walrus operator `:=`, new in Python 3.8, is supported.
2. The positional parameter syntax `/`, new in Python 3.8, is supported.
3. I shouldn't have to mention this, but this project only supports Python 3.