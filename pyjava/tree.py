from typing import List, Optional, Union, Set
from collections.abc import MutableSequence
from abc import ABC, abstractmethod
import re
from textwrap import indent, dedent
from enum import Enum, auto

class StringAwareIndenter:
    def __init__(self):
        self.str_type = None
        # None = not in string
        # 0 = single-quoted single-line string
        # 1 = double-quoted single-line string
        # 2 = single-quoted multi-line string
        # 3 = double-quoted multi-line string

    def __call__(self, line: str, i=0):
        result = self.str_type is None
        while i < len(line):
            if self.str_type is None: # not in string
                if line.startswith("'''", i):
                    self.str_type = 2
                    i += 2
                elif line.startswith('"""', i):
                    self.str_type = 3
                    i += 2
                elif line.startswith("'", i):
                    self.str_type = 0
                elif line.startswith('"', i):
                    self.str_type = 1
            elif self.str_type is 0: # single-quoted single-line string
                if line.startswith('\\', i):
                    i += 1
                elif line.startswith("'", i):
                    self.str_type = None
            elif self.str_type is 1: # double-quoted single-line string
                if line.startswith('\\', i):
                    i += 1
                elif line.startswith('"', i):
                    self.str_type = None
            elif self.str_type is 2: # single-quoted multi-line string
                if line.startswith('\\', i):
                    i += 1
                elif line.startswith("'''", i):
                    self.str_type = None
                    i += 2
            elif self.str_type is 3: # double-quoted multi-line string
                if line.startswith('\\', i):
                    i += 1
                elif line.startswith('"""', i):
                    self.str_type = None
                    i += 2
            else:
                assert False
            i += 1
        return result

class Node(ABC):
    def __init__(self):
        self.parent: Optional[Node] = None

    @property
    def children(self):
        for key, child in vars(self):
            if key != "parent":
                if isinstance(child, Node):
                    yield child
                elif isinstance(child, NodeList):
                    yield from child

    def NodeList(self, elems=None) -> 'NodeList':
        return NodeList(self, elems)

    def __setattr__(self, name: str, value):
        if name == "parent":
            if value is not None and not isinstance(value, Node):
                raise TypeError("can only set 'parent' attribute to a Node or None")
        elif isinstance(value, Node):
            value.parent = self
        elif isinstance(value, NodeList):
            if value.parent is None:
                value.parent = self
            elif value.parent is not self:
                raise ValueError(f"attempted to set attribute '{name}' to a NodeList which is not owned by this Node")
        if hasattr(self, name):
            oldval = getattr(self, name)
            if isinstance(oldval, (Node, NodeList)):
                oldval.parent = None
        super().__setattr__(name, value)

    @abstractmethod
    def __str__(self): pass

    def __eq__(self, other):
        if self is other:
            return True
        if type(self) is not type(other):
            return False
        for key, value in vars(self).items():
            if key != "parent":
                if value != getattr(other, key):
                    return False
        return True

    def accept(self, visitor: 'TreeVisitor'):
        attr = 'visit' + type(self).__name__
        if hasattr(visitor, attr):
            if getattr(visitor, attr)(self):
                for child in self.children:
                    visitor.visit(child)

class NodeList(MutableSequence):
    def __init__(self, parent: Node, elems=None):
        if not isinstance(parent, Node):
            raise TypeError("'parent' argument must be a Node")
        self._nodes = []
        self.parent = parent
        if elems is not None:
            self.extend(elems)

    def __len__(self):
        return len(self._nodes)

    def __getitem__(self, index):
        return self._nodes[index]

    def __setitem__(self, index, value):
        if isinstance(index, slice):
            try:
                iter(value)    
            except TypeError:
                raise TypeError("can only assign an iterable")
            for item in value:
                if not isinstance(item, Node):
                    raise TypeError("can only add Nodes to a NodeList")
                item.parent = self
            for elem in self._nodes[index]:
                elem.parent = None
            self._nodes[index] = value

    def __delitem__(self, index):
        if isinstance(index, slice):
            for item in self._nodes[index]:
                if item is not None:
                    item.parent = None
        else:
            node = self._nodes[index]
            if node is not None:
                node.parent = None
        del self._nodes[index]

    def insert(self, index: int, value):
        if value is not None:
            if not isinstance(value, Node):
                raise TypeError("can only append Nodes to a NodeList")
            value.parent = self.parent
        self._nodes.insert(index, value) 
    
    def pop(self, index=None):
        if index is None:
            value = self._nodes.pop()
        else:
            value = self._nodes.pop(index)
        if value is not None:
            value.parent = None
        return value

    def remove(self, obj):
        self._nodes.remove(obj)
        if obj is not None:
            obj.parent = None

    def __setattr__(self, name: str, value):
        if name == "parent":
            if value is not None and not isinstance(value, Node):
                raise TypeError("can only set 'parent' attribute to a Node or None")
            for elem in self._nodes:
                if elem is not None:
                    elem.parent = value
        elif name == "_nodes":
            if hasattr(self, "_nodes"):
                raise AttributeError("cannot reassign '_nodes' attribute")
        super().__setattr__(name, value)

    def __str__(self):
        return str(self._nodes)

    def join(self, sep: str=" "):
        return sep.join(str(node) for node in self._nodes)

class Name(Node):
    REGEX = re.compile(r"^(?![0-9])\w+$")

    def __init__(self, value: str):
        if not Name.REGEX.match(value):
            raise ValueError(f"{value!r} is not a valid Name")
        super().__init__()
        self._str = value

    def __len__(self):
        return len(self._str)

    def __getitem__(self, index):
        return self._str[index]

    def __add__(self, other):
        if isinstance(other, str):
            return Name(self._str + other) if other else self
        elif isinstance(other, Name):
            return Name(self._str + other._str)
        else:
            return NotImplemented

    def __radd__(self, other):
        if isinstance(other, str):
            return Name(other + self._str) if other else self
        elif isinstance(other, Name):
            return Name(other._str + self._str)
        else:
            return NotImplemented

    def __str__(self):
        return self._str

    def __hash__(self):
        return hash(self._str)

    def __eq__(self, obj):
        return obj is self or isinstance(obj, ALL_NAMES) and self._str == str(obj)

    def __setattr__(self, name: str, value):
        if name == "_str" and hasattr(self, "_str"):
            raise AttributeError("cannot reassign '_str' attribute")
        super().__setattr__(name, value)

    def accept(self, visitor: 'TreeVisitor'):
        visitor.visitName(self)

class DottedName(Node):
    REGEX = re.compile(r"^(?![0-9])\w+(\.(?![0-9])\w+)*$")

    def __init__(self, names: List[Name]):
        if len(names) == 0:
            raise ValueError("no names given")
        super().__init__()
        for item in names:
            if not isinstance(item, Name):
                raise TypeError(f"expected Name, not {type(item).__name__}")
        self.names = self.NodeList(names)
        self._str = self.names.join('.')
        self._hash = hash(self._str)

    def __len__(self):
        return len(self.names)

    def __getitem__(self, index):
        return self.names[index]

    def __hash__(self):
        return self._hash

    def __str__(self):
        return self._str

    def __eq__(self, obj):
        return obj is self or isinstance(obj, ALL_NAMES) and self._str == str(obj)

    def __contains__(self, value):
        try:
            self.index(value)
            return True
        except ValueError:
            return False

    def index(self, value):
        if isinstance(value, str):
            if '.' in value:
                split = value.split(".")
                if len(split) > len(self):
                    raise ValueError(f"{value!r} is not in name")
                split_range = range(len(split))
                def search(start: int):
                    for i, j in zip(range(start, start+len(split)), split_range):
                        if self.names[i] != split[j]:
                            return False
                    return True
                for i in range(0, len(self)-len(split)+1):
                    if search(i):
                        return i
            else:
                for i, name in enumerate(self):
                    if name == value:
                        return i
                
        elif isinstance(value, Name):
            for i, name in enumerate(self):
                if name == value:
                    return i

        elif isinstance(value, DottedName) and not isinstance(value, ModuleName):
            if len(value) > len(self):
                raise ValueError(f"{value!r} is not in name")
            value_range = range(len(value))
            def search(start: int):
                for i, j in zip(range(start, start+len(value)), value_range):
                    if self.names[i] != value.names[j]:
                        return False
                return True
            for i in range(0, len(self)-len(value)+1):
                if search(i):
                    return i

        raise ValueError(f"{value!r} is not in name")

    def __setattr__(self, name: str, value):
        if name == "names":
            if hasattr(self, "names"):
                raise AttributeError("cannot reassign 'names' attribute")
            if not isinstance(value, NodeList):
                raise AttributeError("cannot assign 'names' a value which is not a NodeList")
        elif name in ("_hash", "_str") and hasattr(self, name):
            raise AttributeError(f"cannot reassign '{name}' attribute")
        super().__setattr__(name, value)
    
    @classmethod
    def from_str(cls, value: str):
        if not cls.REGEX.match(value):
            raise ValueError(f"{value!r} is not a valid {cls.__name__}")
        return cls([Name(arg) for arg in value.split(".")])

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitDottedName(self):
            visitor.visitAll(self.names)

#region expressions

class Expression(Node): pass

class CommaExpression(Expression):
    def __init__(self, items: List[Expression]):
        if len(items) == 0:
            raise ValueError("CommaExpression() needs at least 1 item")
        super().__init__()
        self.items = self.NodeList(items)

    def __str__(self):
        if len(self.items) == 1:
            return f"{self.items[0]},"
        else:
            return self.items.join(", ")

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitCommaExpression(self):
            visitor.visitAll(self.items)

class ParensExpression(Expression):
    def __init__(self, expr: Optional[Expression]=None):
        super().__init__()
        self.expr = expr
    
    def __str__(self):
        return f"({self.expr})" if self.expr else "()"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitParensExpression(self):
            visitor.visit(self.expr)

class LambdaParameter(Node):
    def __init__(self, name: Name):
        super().__init__()
        self.name = name

class NormalLambdaParameter(LambdaParameter):
    def __init__(self, name: Name, default: Optional[Expression]=None):
        super().__init__(name)
        self.default = default

    def __str__(self):
        if self.default:
            return f"{self.name} = {self.default}"
        else:
            return str(self.name)

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitNormalLambdaParameter(self):
            visitor.visit(self.name)
            visitor.visit(self.default)

class PositionalLambdaParameter(LambdaParameter):
    def __init__(self):
        super().__init__(None)

    def __str__(self):
        return "/"

class StarLambdaParameter(LambdaParameter):
    def __str__(self):
        return f"*{self.name or ''}"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.vistiStarLambdaParameter(self):
            visitor.visit(self.name)

class StarStarLambdaParameter(LambdaParameter):
    def __str__(self):
        return f"**{self.name}"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitStarStarLambdaParameter(self):
            visitor.visit(self.name)

class Lambda(Expression):
    def __init__(self, parameters: List[LambdaParameter], body: Expression):
        super().__init__()
        self.parameters = self.NodeList(parameters)
        self.body = body

    def __str__(self):
        if self.parameters:
            return f"lambda {self.parameters.join(', ')}: {self.body}"
        else:
            return f"lambda: {self.body}"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitLambda(self):
            visitor.visitAll(self.parameters)
            visitor.visit(self.body)

class BinaryOperator(Enum):
    ADD = '+'
    SUB = '-'
    MUL = '*'
    MATMUL = '@'
    TRUEDIV = '/'
    FLOORDIV = '//'
    POW = '**'
    LSHIFT = '<<'
    RSHIFT = '>>'
    LT = '<'
    LE = '<='
    GT = '>'
    GE = '>='
    EQ = '=='
    NE = '!='
    IS = 'is'
    IS_NOT = 'is not'
    IN = 'in'
    NOT_IN = 'not in'
    LOGIC_OR = 'or'
    LOGIC_AND = 'and'
    BIT_OR = '|'
    BIT_AND = '&'
    XOR = '^'
    DIAMOND = '<>'

class BinaryExpression(Expression):
    def __init__(self, left: Expression, operator: BinaryOperator, right: Expression):
        if not isinstance(operator, BinaryOperator):
            operator = BinaryOperator(operator)
        super().__init__()
        self.left = left
        self.operator = operator
        self.right = right

    def __str__(self):
        return f"{self.left} {self.operator.value} {self.right}"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitBinaryExpression(self):
            visitor.visit(self.left)
            visitor.visit(self.right)

class UnaryOperator(Enum):
    NOT = 'not'
    NEG = '-'
    POS = '+'
    INVERT = '~'
    STAR = '*'
    STARSTAR = '**'
    AWAIT = 'await'

class UnaryExpression(Expression):
    def __init__(self, operator: UnaryOperator, expr: Expression):
        if not isinstance(operator, UnaryOperator):
            operator = UnaryOperator(operator)
        super().__init__()
        self.operator = operator
        self.expr = expr

    def __str__(self):
        if self.operator.value[-1].isalpha():
            return f"{self.operator.value} {self.expr}"
        else:
            return f"{self.operator.value}{self.expr}"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitUnaryExpression(self):
            visitor.visit(self.expr)

class YieldExpression(Expression):
    def __init__(self, expr: Optional[Expression]=None, isfrom=False):
        super().__init__()
        self.expr = expr
        self.isfrom = isfrom

    def __str__(self):
        if self.expr:
            return f"{'yield from' if self.isfrom else 'yield'} {self.expr}"
        else:
            return "yield"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitYieldExpression(self):
            visitor.visit(self.expr)

class GeneratorExpression(Expression):
    def __init__(self, expr: Expression, variables: Expression, iterable: Expression, condition: Optional[Expression]=None, isasync=False):
        super().__init__()
        self.expr = expr
        self.variables = variables
        self.iterable = iterable
        self.condition = condition
        self.isasync = isasync

    def __str__(self):
        result = f"{self.expr} {'async for' if self.isasync else 'for'} {self.variables} in {self.iterable}" 
        if self.condition:
            result += f" if {self.condition}"
        return result

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitGeneratorExpression(self):
            visitor.visit(self.expr)
            visitor.visit(self.variables)
            visitor.visit(self.iterable)
            visitor.visit(self.condition)

class KeywordArgument(Expression):
    def __init__(self, keyword: Name, value: Expression):
        super().__init__()
        self.keyword = keyword
        self.value = value

    def __str__(self):
        return f"{self.keyword} = {self.value}"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitKeywordArgument(self):
            visitor.visit(self.keyword)
            visitor.visit(self.value)

class CallExpression(Expression):
    def __init__(self, function: Expression, arguments: List[Expression]):
        super().__init__()
        self.function = function
        self.arguments = self.NodeList(arguments)

    def __str__(self):
        return f"{self.function}({self.arguments.join(', ')})"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitCallExpression(self):
            visitor.visit(self.function)
            visitor.visitAll(self.arguments)

class SliceExpression(Expression):
    def __init__(self, start: Optional[Expression]=None, end: Optional[Expression]=None, increment: Optional[Expression]=None):
        super().__init__()
        self.start = start
        self.end = end
        self.increment = increment

    def __str__(self):
        result = f"{self.start or ''}:{self.end or ''}"
        if self.increment:
            result += f":{self.increment}"
        return result

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitSliceExpression(self):
            visitor.visit(self.start)
            visitor.visit(self.end)
            visitor.visit(self.increment)

class IndexExpression(Expression):
    def __init__(self, array: Expression, index: Expression):
        super().__init__()
        self.array = array
        self.index = index

    def __str__(self):
        return f"{self.array}[{self.index}]"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitIndexExpression(self):
            visitor.visit(self.array)
            visitor.visit(self.index)

class NumberLiteral(Expression):
    def __init__(self, strvalue: str):
        super().__init__()
        if '.' in strvalue or (not strvalue.startswith(('0x', '0X')) and ('e' in strvalue or 'E' in strvalue)):
            self.value = float(strvalue)
        else:
            self.value = int(strvalue, base=0)
        self._str = strvalue

    def __str__(self):
        return self._str

    def accept(self, visitor: 'TreeVisitor'):
        visitor.visitNumberLiteral(self)

class StringLiteral(Expression):
    def __init__(self, strvalue: Union[str, List[str]]):
        if isinstance(strvalue, list):
            if len(strvalue) == 0:
                raise ValueError("no strings given")
            self.value = ""
            self._str = None
            from ast import literal_eval
            for elem in strvalue:
                if elem[-1] not in ('"', "'"):
                    raise ValueError(f"{elem!r} does not represent a string literal")
                self.value += literal_eval(elem)
                if self._str:
                    self._str += ' ' + elem
                else:
                    self._str = elem
        else:
            if strvalue[-1] not in ('"', "'"):
                raise ValueError(f"{strvalue!r} does not represent a string literal")
            from ast import literal_eval
            self.value = literal_eval(strvalue)
            self._str = strvalue

    def __str__(self):
        return self._str

    def accept(self, visitor: 'TreeVisitor'):
        visitor.visitStringLiteral(self)

class EllipsisLiteral(Expression):
    def __init__(self):
        super().__init__()
        self.value = ...

    def __str__(self):
        return "..."

    def accept(self, visitor: 'TreeVisitor'):
        visitor.visitEllipsisLiteral(self)

class BoolLiteral(Expression):
    def __init__(self, value: bool):
        if type(value) is not bool:
            raise TypeError
        self.value = value

    def __str__(self):
        return str(self.value)

    def accept(self, visitor: 'TreeVisitor'):
        visitor.visitBoolLiteral(self)

class NoneLiteral(Expression):
    def __init__(self):
        super().__init__()
        self.value = None

    def __str__(self):
        return None

    def accept(self, visitor: 'TreeVisitor'):
        visitor.visitNoneLiteral(self)

class VarExpression(Expression):
    def __init__(self, name: Name):
        super().__init__()
        self.name = name

    def __str__(self):
        return str(self.name)

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitVarExpression(self):
            visitor.visit(self.name)

class AttrExpression(Expression):
    def __init__(self, obj: Expression, attr: Name):
        super().__init__()
        self.obj = obj
        self.attr = attr

    def __str__(self):
        return f"{self.obj}.{self.attr}"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitAttrExpression(self):
            visitor.visit(self.obj)
            visitor.visit(self.attr)

    @property
    def is_dotted_name(self):
        return isinstance(self.obj, VarExpression) or isinstance(self.obj, AttrExpression) and self.obj.is_dotted_name

class IfExpression(Expression):
    def __init__(self, truepart: Expression, condition: Expression, falsepart: Expression):
        super().__init__()
        self.truepart = truepart
        self.condition = condition
        self.falsepart = falsepart

    def __str__(self):
        return f"{self.truepart} if {self.condition} else {self.falsepart}"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitIfExpression(self):
            visitor.visit(self.truepart)
            visitor.visit(self.condition)
            visitor.visit(self.falsepart)

class ListLiteral(Expression):
    def __init__(self, items: List[Expression]):
        super().__init__()
        self.items = self.NodeList(items)

    def __str__(self):
        return f"[{self.items.join(', ')}]"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitListLiteral(self):
            visitor.visitAll(self.items)

class SetLiteral(Expression):
    def __init__(self, items: List[Expression]):
        if len(items) == 0:
            raise ValueError("cannot have empty set literal")
        super().__init__()
        self.items = self.NodeList(items)

    def __str__(self):
        return f"{{{self.items.join(', ')}}}"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitSetLiteral(self):
            visitor.visitAll(self.items)

class MapEntry(Node):
    def __init__(self, key: Expression, value: Expression):
        super().__init__()
        self.key = key
        self.value = value

    def __str__(self):
        return f"{self.key}: {self.value}"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitMapEntry(self):
            visitor.visit(self.key)
            visitor.visit(self.value)

class MapLiteral(Expression):
    def __init__(self, items: List[Expression]):
        super().__init__()
        self.items = self.NodeList(items)

    def __str__(self):
        return f"{{{self.items.join(', ')}}}"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitMapLiteral(self):
            visitor.visitAll(self.items)

class AssignmentExpression(Expression):
    def __init__(self, name: Name, value: Expression):
        super().__init__()
        self.name = name
        self.value = value

    def __str__(self):
        return f"{self.name} := {self.value}"

#endregion expressions

#region statements

class Statement(Node): pass
class SimpleStatement(Statement): pass
class FlowStatement(SimpleStatement): pass

class Decorator(Node):
    def __init__(self, name: DottedName, args: List[Expression]=None):
        super().__init__()
        self.name = name
        self.args = self.NodeList(args)

    def __str__(self):
        if self.args:
            return f'@{self.name}({self.args.join(", ")})'
        else:
            return f'@{self.name}'

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitDecorator(self):
            visitor.visit(self.name)
            visitor.visitAll(self.args)

class ExpressionStatement(SimpleStatement):
    def __init__(self, expr: Expression):
        super().__init__()
        self.expr = expr

    def __str__(self):
        return str(self.expr)

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitExpressionStatement(self):
            visitor.visit(self.expr)

class AnnotatedAssignmentStatement(SimpleStatement):
    def __init__(self, assigned: Expression, annotation: Expression, value: Optional[Expression]=None):
        if value is None and annotation is None:
            raise ValueError("both value and annotation cannot be None at once")
        self.assigned = assigned
        self.value = value
        self.annotation = annotation

    def __str__(self):
        if self.annotation:
            if self.value:
                return f"{self.assigned}: {self.annotation} = {self.value}"
            else:
                return f"{self.assigned}: {self.annotation}"
        else:
            return f"{self.assigned} = {self.value}"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitAnnotatedAssignmentStatement(self):
            visitor.visit(self.assigned)
            visitor.visit(self.annotation)
            visitor.visit(self.value)

class MultiAssignmentStatement(SimpleStatement):
    def __init__(self, assigned: List[Expression], value: Expression):
        if len(assigned) == 0:
            raise ValueError("no assignments given")
        super().__init__()
        self.assigned = self.NodeList(assigned)
        self.value = value

    def __str__(self):
        return f"{self.assigned.join(' = ')} = {self.value}"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitMultiAssignmentStatement(self):
            visitor.visitAll(self.assigned)
            visitor.visit(self.value)

class AugmentType(Enum):
    ADD = "+="
    SUB = "-="
    MUL = "*="
    MATMUL = "@="
    TRUEDIV = "/="
    FLOORDIV = "//="
    MOD = "%="
    AND = "&="
    OR = "|="
    XOR = "^="
    LSHIFT = "<<="
    RSHIFT = ">>="
    POW = "**="

class AugmentStatement(SimpleStatement):
    def __init__(self, assigned: Expression, operation: AugmentType, value: Expression):
        if not isinstance(operation, AugmentType):
            operation = AugmentType(operation)
        super().__init__()
        self.assigned = assigned
        self.operation = operation
        self.value = value

    def __str__(self):
        return f"{self.assigned} {self.operation.value} {self.value}"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitAugmentStatement(self):
            visitor.visit(self.assigned)
            visitor.visit(self.value)

class PassStatement(SimpleStatement):
    def __str__(self):
        return "pass"

    def accept(self, visitor: 'TreeVisitor'):
        visitor.visitPassStatement()

class DelStatement(SimpleStatement):
    def __init__(self, exprs: List[Expression]):
        if len(exprs) == 0:
            raise ValueError("no expressions given")
        super().__init__()
        self.exprs = self.NodeList(exprs)

    def __str__(self):
        return f"del {self.exprs.join(', ')}"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitDelStatement(self):
            visitor.visitAll(self.exprs)

class BreakStatement(FlowStatement):
    def __str__(self):
        return "break"

    def accept(self, visitor: 'TreeVisitor'):
        visitor.visitBreakStatement(self)

class ContinueStatement(FlowStatement):
    def __str__(self):
        return "continue"

    def accept(self, visitor: 'TreeVisitor'):
        visitor.visitContinueStatement(self)

class ReturnStatement(FlowStatement):
    def __init__(self, expr: Optional[Expression]=None):
        super().__init__()
        self.expr = expr

    def __str__(self):
        if self.expr is None:
            return "return"
        else:
            return f"return {self.expr}"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitReturnStatement(self):
            visitor.visit(self.expr)

class RaiseStatement(FlowStatement):
    def __init__(self, error: Optional[Expression]=None, from_expr: Optional[Expression]=None):
        if error is None and from_expr is not None:
            raise ValueError("cannot give from_expr when error is None")
        self.error = error
        self.from_expr = from_expr

    def __str__(self):
        if self.error is None:
            return "raise"
        elif self.from_expr is None:
            return f"raise {self.error}"
        else:
            return f"raise {self.error} from {self.from_expr}"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitRaiseStatement(self):
            visitor.visit(self.error)
            visitor.visit(self.from_expr)

class DottedImportName(Node):
    def __init__(self, name: DottedName, alias: Optional[Name]=None):
        super().__init__()
        self.name = name
        self.alias = alias

    def __str__(self):
        if self.alias:
            return f"{self.name} as {self.alias}"
        else:
            return str(self.name)

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitDottedImportName(self):
            visitor.visit(self.name)
            visitor.visit(self.alias)

class ImportStatement(SimpleStatement):
    def __init__(self, imports: List[DottedImportName]):
        if len(imports) == 0:
            raise ValueError("no imports given")
        super().__init__()
        self.imports = self.NodeList(imports)

    def __str__(self):
        return f"import {self.imports.join(', ')}"

    def accept(self, visitor: 'TreeVisitor'):
        if visitor.visitImportStatement(self):
            visitor.visitAll(self.imports)

class ModuleName(DottedName):
    REGEX = re.compile(r"^(?:\.+((?![0-9])\w+(\.(?![0-9])\w+)*)?|(?![0-9])\w+(\.(?![0-9])\w+)*)$")

    def __init__(self, names: List[Name], prefix_dots: int=0):
        Node.__init__(self)
        if len(names) == 0 and prefix_dots == 0:
            raise ValueError("no names given")
        if prefix_dots < 0:
            raise ValueError("prefix_dots < 0")
        for item in names:
            if not isinstance(item, Name):
                raise TypeError(f"expected Name, not {type(item).__name__}")
        self.prefix_dots = prefix_dots
        self.names = self.NodeList(names)
        self._str = '.'*prefix_dots + self.names.join('.')
        self._hash = hash(self._str)

ALL_NAMES = (DottedName, ModuleName, Name, str)

class ImportName(Node):
    def __init__(self, name: Name, alias: Optional[Name]=None):
        super().__init__()
        self.name = name
        self.alias = alias

    def __str__(self):
        if self.alias:
            return f"{self.name} as {self.alias}"
        else:
            return str(self.name)

class ImportFromStatement(SimpleStatement):
    def __init__(self, module: ModuleName, imports: List[ImportName]=None):
        super().__init__()
        self.module = module
        self.imports = self.NodeList(imports)

    def __str__(self):
        if self.imports:
            return f"from {self.module} import {self.imports.join(', ')}"
        else:
            return f"from {self.module} import *"

class GlobalStatement(SimpleStatement):
    def __init__(self, names: List[Name]):
        if len(names) == 0:
            raise ValueError("no names given")
        super().__init__()
        self.names = self.NodeList(names)

    def __str__(self):
        return f"global {self.names.join(', ')}"

class NonLocalStatement(SimpleStatement):
    def __init__(self, names: List[Name]):
        if len(names) == 0:
            raise ValueError("no names given")
        super().__init__()
        self.names = self.NodeList(names)

    def __str__(self):
        return f"nonlocal {self.names.join(', ')}"

class AssertStatement(SimpleStatement):
    def __init__(self, condition: Expression, message: Optional[Expression]=None):
        super().__init__()
        self.condition = condition
        self.message = message

    def __str__(self):
        if self.message:
            return f"assert {self.condition}, {self.message}"
        else:
            return f"assert {self.condition}"

class Suite(Node):
    def __init__(self, stmts: List[Statement]):
        if len(stmts) == 0:
            raise ValueError("no statements given")
        super().__init__()
        self.stmts = self.NodeList(stmts)

    def __str__(self):
        return ":\n" + indent(self.stmts.join('\n'), '    ', StringAwareIndenter())

BodyType = Union[Suite, SimpleStatement]

class CompoundStatement(Statement):
    def __init__(self, body: BodyType):
        super().__init__()
        self.body = body

    def body_str(self, body=None):
        if body is None:
            body = self.body
        if isinstance(body, Suite):
            return str(body)
        else:
            return f": {body}"

class Decorated(CompoundStatement):
    def __init__(self, body: BodyType, decorators: List[Decorator]=None):
        super().__init__(body)
        self.decorators = self.NodeList(decorators)

    def decorator_str(self):
        if self.decorators:
            return self.decorators.join("\n") + "\n"
        else:
            return ""

class Elif(Node):
    def __init__(self, condition: Expression, body: BodyType):
        super().__init__()
        self.condition = condition
        self.body = body

    body_str = CompoundStatement.body_str
    
    def __str__(self):
        return f"elif {self.condition}{self.body_str()}"

class IfStatement(CompoundStatement):
    def __init__(self, condition: Expression, body: BodyType, elifs: List[Elif]=None, else_body: Optional[BodyType]=None):
        super().__init__(body)
        self.condition = condition
        self.elifs = self.NodeList(elifs)
        self.else_body = else_body

    def __str__(self):
        result = f"if {self.condition}{self.body_str()}"
        if self.elifs:
            result += "\n" + self.elifs.join("\n")
        if self.else_body:
            result += f"\nelse{self.body_str(self.else_body)}"
        return result

class WhileStatement(CompoundStatement):
    def __init__(self, condition: Expression, body: BodyType, else_body: Optional[BodyType]=None):
        super().__init__(body)
        self.condition = condition
        self.else_body = else_body

    def __str__(self):
        if self.else_body:
            return f"while {self.condition}{self.body_str()}\nelse{self.body_str(self.else_body)}"
        else:
            return f"while {self.condition}{self.body_str()}"

class ForStatement(CompoundStatement):
    def __init__(self, variables: Expression, iterable: Expression, body: BodyType, else_body: Optional[BodyType]=None, isasync=False):
        super().__init__(body)
        self.variables = variables
        self.iterable = iterable
        self.else_body = else_body
        self.isasync = isasync

    def __str__(self):
        result = f"for {self.variables} in {self.iterable}{self.body_str()}"
        if self.else_body:
            result += f"\nelse{self.body_str(self.else_body)}"
        if self.isasync:
            result = "async " + result
        return result

class ExceptClause(Node):
    def __init__(self, *, test: Expression=None, name: Optional[Name]=None, body: BodyType):
        super().__init__()
        self.test = test
        self.name = name
        self.body = body

    body_str = CompoundStatement.body_str

    def __str__(self):
        if self.test:
            if self.name:
                return f"except {self.test} as {self.name}{self.body_str()}"
            else:
                return f"except {self.test}{self.body_str()}"
        else:
            return f"except{self.body_str()}"

class TryStatement(CompoundStatement):
    def __init__(self, body: Expression, excepts: List[ExceptClause]=None, else_body: Optional[BodyType]=None, finally_body: Optional[BodyType]=None):
        if excepts is None or len(excepts) == 0:
            if else_body is not None:
                raise ValueError("can only give else_body when there are except clauses")
            if finally_body is None:
                raise ValueError("missing either except clauses or finally_body")
        super().__init__(body)
        self.excepts = self.NodeList(excepts)
        self.else_body = else_body
        self.finally_body = finally_body

    def __str__(self):
        result = f"try{self.body_str()}\n" + self.excepts.join('\n')
        if self.else_body:
            result += f"\nelse{self.body_str(self.else_body)}"
        if self.finally_body:
            result += f"\nfinally{self.body_str(self.finally_body)}"
        return result

class WithItem(Node):
    def __init__(self, value: Expression, assigned: Optional[Expression]=None):
        super().__init__()
        self.value = value
        self.assigned = assigned

    def __str__(self):
        if self.assigned:
            return f"{self.value} as {self.assigned}"
        else:
            return str(self.value)

class WithStatement(CompoundStatement):
    def __init__(self, items: List[WithItem], body: BodyType, isasync=False):
        if len(items) == 0:
            raise ValueError("no items given")
        super().__init__(body)
        self.items = self.NodeList(items)
        self.isasync = False

    def __str__(self):
        result = f"with {self.items.join(', ')}{self.body_str()}"
        if self.isasync:
            result = "async " + result
        return result

class FunctionParameter(Node):
    def __init__(self, name: Name, annotation: Optional[Expression]=None):
        super().__init__()
        self.name = name
        self.annotation = annotation

class NormalFunctionParameter(FunctionParameter):
    def __init__(self, name: Name, annotation: Optional[Expression]=None, default: Optional[Expression]=None):
        super().__init__(name, annotation)
        self.default = default

    def __str__(self):
        result = str(self.name)
        if self.annotation:
            result += f": {self.annotation}"
        if self.default:
            result += f" = {self.default}"
        return result

class PositionalFunctionParameter(FunctionParameter):
    def __init__(self):
        super().__init__(None)

    def __str__(self):
        return "/"

class StarFunctionParameter(FunctionParameter):
    def __str__(self):
        return f"*{self.name or ''}"

class StarStarFunctionParameter(FunctionParameter):
    def __str__(self):
        return f"**{self.name}"

class FunctionDefinition(Decorated):
    def __init__(self, name: Name, parameters: List[FunctionParameter], *, annotation: Optional[Expression]=None, body: BodyType, isasync=False, decorators: List[Decorator]=None):
        super().__init__(body, decorators)
        self.name = name
        self.parameters = self.NodeList(parameters)
        self.annotation = annotation
        self.isasync = isasync

    def __str__(self):
        result = f"{self.decorator_str()}def {self.name}({self.parameters.join(', ')})"
        if self.isasync:
            result = "async " + result
        if self.annotation:
            result += f" -> {self.annotation}"
        result += self.body_str()
        return result

class ClassStatement(Decorated):
    def __init__(self, name: Name, arguments: List[Expression], body: BodyType, decorators: List[Decorator]=None):
        super().__init__(body, decorators)
        self.name = name
        self.arguments = self.NodeList(arguments)

    def __str__(self):
        result = f"{self.decorator_str()}class {self.name}"
        if self.arguments:
            result += f"({self.arguments.join(', ')})"
        result += self.body_str()
        return result

#endregion statements

class TreeVisitor:
    def visitAnnotatedAssignmentStatement(self, node: AnnotatedAssignmentStatement): return True
    def visitAssertStatement(self, node: AssertStatement): return True
    def visitAttrExpression(self, node: AttrExpression): return True
    def visitAugmentStatement(self, node: AugmentStatement): return True
    def visitBinaryExpression(self, node: BinaryExpression): return True
    def visitBoolLiteral(self, node: BoolLiteral): return True
    def visitBreakStatement(self, node: BreakStatement): return True
    def visitCallExpression(self, node: CallExpression): return True
    def visitClassStatement(self, node: ClassStatement): return True
    def visitCommaExpression(self, node: CommaExpression): return True
    def visitContinueStatement(self, node: ContinueStatement): return True
    def visitDecorator(self, node: Decorator): return True
    def visitDelStatement(self, node: DelStatement): return True
    def visitDottedImportName(self, node: DottedImportName): return True
    def visitDottedName(self, node: DottedName): return True
    def visitElif(self, node: Elif): return True
    def visitEllipsisLiteral(self, node: EllipsisLiteral): return True
    def visitExceptClause(self, node: ExceptClause): return True
    def visitExpressionStatement(self, node: ExpressionStatement): return True
    def visitForStatement(self, node: ForStatement): return True
    def visitFunctionDefinition(self, node: FunctionDefinition): return True
    def visitGeneratorExpression(self, node: GeneratorExpression): return True
    def visitGlobalStatement(self, node: GlobalStatement): return True
    def visitIfExpression(self, node: IfExpression): return True
    def visitIfStatement(self, node: IfStatement): return True
    def visitImportFromStatement(self, node: ImportFromStatement): return True
    def visitImportName(self, node: ImportName): return True
    def visitImportStatement(self, node: ImportStatement): return True
    def visitIndexExpression(self, node: IndexExpression): return True
    def visitKeywordArgument(self, node: KeywordArgument): return True
    def visitLambda(self, node: Lambda): return True
    def visitListLiteral(self, node: ListLiteral): return True
    def visitMapEntry(self, node: MapEntry): return True
    def visitMapLiteral(self, node: MapLiteral): return True
    def visitModuleName(self, node: ModuleName): return True
    def visitMultiAssignmentStatement(self, node: MultiAssignmentStatement): return True
    def visitName(self, node: Name): return True
    def visitNoneLiteral(self, node: NoneLiteral): return True
    def visitNonLocalStatement(self, node: NonLocalStatement): return True
    def visitNormalFunctionParameter(self, node: NormalFunctionParameter): return True
    def visitNormalLambdaParameter(self, node: NormalLambdaParameter): return True
    def visitNumberLiteral(self, node: NumberLiteral): return True
    def visitParensExpression(self, node: ParensExpression): return True
    def visitPassStatement(self, node: PassStatement): return True
    def visitRaiseStatement(self, node: RaiseStatement): return True
    def visitReturnStatement(self, node: ReturnStatement): return True
    def visitSetLiteral(self, node: SetLiteral): return True
    def visitSliceExpression(self, node: SliceExpression): return True
    def visitStarFunctionParameter(self, node: StarFunctionParameter): return True
    def vistiStarLambdaParameter(self, node): return True
    def visitStarStarFunctionParameter(self, node: StarStarFunctionParameter): return True
    def visitStarStarLambdaParameter(self, node: StarStarLambdaParameter): return True
    def visitStringLiteral(self, node: StringLiteral): return True
    def visitSuite(self, node: Suite): return True

    def visit(self, node: Node):
        if node is None:
            return
        node.accept(self)

    def visitAll(self, nodes: NodeList):
        for elem in nodes:
            self.visit(elem)