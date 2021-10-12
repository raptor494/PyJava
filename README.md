# PyJava
### Description
This project is the inverse of my [JavaPy project](https://github.com/raptor4694/JavaPy). It allows you to write Python code using semicolons and braces instead of indentation, like Java.

### Usage
Call the program with `java -jar pyjava.jar <filename>` and it will output a file
called the same thing except with a `.py` extension.
You can use pyjavaconfig.json to configure parsing options.
The program tries to format the file to be human-readable but may not be quite right in places. Use your own formatter as necessary.
The parser does not *always* check for semantically invalid syntax, such as duplicate/missing variable names, duplicate functions, etc.

### Config File
The configuration file, if present, has the format
```typescript
{
    "requireSemicolons"?: boolean = false,
    "allowColonSimpleBlocks"?: boolean = true,
    "allowNoColonSimpleBlocks"?: boolean = true,
    "forceParensInStatements"?: boolean = false,
    "forceParensInReturnYieldRaise"?: boolean = false,
    "files"?: {
        "include"?: string[] = ["**.pyj"],
        "exclude"?: string[] = []
    }
}
```

#### requireSemicolons
Simply put, if this is `true` then semicolons will be required at the end of statements, as opposed to the default method which is similar to JavaScript's semicolon auto-insertion.

Defaults to `false`.

##### Example:
Input:
```python
if (condition)
    return 
        getValue();
```

Output when `requireSemicolons` is false:
```python
if (condition):
    return
getValue()
```

Output when `requireSemicolons` is true:
```python
if (condition):
    return getValue()
```

#### allowColonSimpleBlocks
This allows you to have blocks consisting of a colon followed by a single statement, such as
```python
if condition: return True
```

Defaults to `true`.

#### allowNoColonSimpleBlocks
This allows you to have blocks consisting of a single statement, such as
```python
if condition
    return True
```

Defaults to `true`.

#### forceParensInStatements
This option, when `true`, requires conditions in `if`, `while`, `for` and other statements to be surrounded by parenthesis.
```python
if (condition) { ... }

while (condition) { ... }

for (elem in values) { ... }
```

Defaults to `false`.

#### forceParensInReturnYieldRaise
This option, when `true`, requires the values of `return`, `yield`, `assert`, `raise`, and basically any other statement which accepts an expression immediately following its keyword, to be enclosed by parenthesis.
```python
return (value)
raise (Exception)
yield (element)
assert (condition, "message")
del (x.y, z[0])
```

Defaults to `false`.

#### files
This object allows you to specify a list of files/folder globs to include and exclude from compilation.

`include` defaults to `["**.pyj"]`.<br>
`exclude` defaults to `[]`.

### Differences from Normal Python
#### Simple Statements
Non-compound statements must now end with a semicolon *unless* the `--optional-semicolons` flag is provided.

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

    class [superclass arguments] <arguments> <brace-enclosed class body>

If superclass arguments are provided, the hidden name of the class will try to be similar to the first superclass defined. Otherwise, it will be similar to 'object'.

**Example**:
```ruby
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
3. The match statement, new in Python 3.10, is supported.
